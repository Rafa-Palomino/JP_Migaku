[CmdletBinding()]
param(
    [Parameter()]
    [string]$WorkspaceRoot = "."
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:PlaceholderAgents = @("unknown", "interactive-session")
$script:PlaceholderSummaries = @("Stop event", "SubagentStop event", "SessionStart event", "UserPromptSubmit event")
$script:IntermediateEvents = @("UserPromptSubmit", "PreToolUse", "PostToolUse", "SubagentStart", "SubagentStop", "PreCompact")

function Test-MeaningfulString {
    param(
        [Parameter()]
        [AllowNull()]
        [string]$Value,

        [Parameter()]
        [string[]]$Disallowed = @()
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    foreach ($blocked in $Disallowed) {
        if ($Value.Trim().Equals($blocked, [System.StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
    }

    return $true
}

function Get-NestedValue {
    param(
        [Parameter(Mandatory = $true)]
        [object]$InputObject,

        [Parameter(Mandatory = $true)]
        [string[]]$Path,

        [Parameter()]
        [object]$Default = $null
    )

    $current = $InputObject
    foreach ($segment in $Path) {
        if ($null -eq $current) {
            return $Default
        }

        if ($current -is [System.Collections.IDictionary]) {
            if (-not $current.Contains($segment)) {
                return $Default
            }

            $current = $current[$segment]
            continue
        }

        $property = $current.PSObject.Properties[$segment]
        if ($null -eq $property) {
            return $Default
        }

        $current = $property.Value
    }

    if ($null -eq $current) {
        return $Default
    }

    return $current
}

function Get-FirstNestedValue {
    param(
        [Parameter(Mandatory = $true)]
        [object]$InputObject,

        [Parameter(Mandatory = $true)]
        [object[]]$Paths,

        [Parameter()]
        [object]$Default = $null
    )

    foreach ($path in $Paths) {
        $value = Get-NestedValue -InputObject $InputObject -Path $path -Default $null
        if ($null -eq $value) {
            continue
        }

        if ($value -is [string] -and [string]::IsNullOrWhiteSpace($value)) {
            continue
        }

        return $value
    }

    return $Default
}

function Convert-ToStringArray {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return ,@()
    }

    if ($Value -is [string]) {
        return ,@($Value)
    }

    if ($Value -is [System.Collections.IEnumerable]) {
        $items = New-Object System.Collections.Generic.List[string]
        foreach ($entry in $Value) {
            if ($null -ne $entry) {
                $items.Add([string]$entry)
            }
        }

        return ,($items.ToArray())
    }

    return ,@([string]$Value)
}

function Convert-ToRelativeWorkspacePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$WorkspaceRoot
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }

    $candidate = $Path.Trim()
    if ([System.IO.Path]::IsPathRooted($candidate)) {
        try {
            $resolvedCandidate = [System.IO.Path]::GetFullPath($candidate)
            $resolvedRoot = [System.IO.Path]::GetFullPath($WorkspaceRoot)
            if ($resolvedCandidate.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
                $relative = $resolvedCandidate.Substring($resolvedRoot.Length).TrimStart('\', '/')
                return ($relative -replace '\\', '/')
            }
        }
        catch {
            return $candidate -replace '\\', '/'
        }
    }

    return $candidate -replace '\\', '/'
}

function Convert-ToWorkspacePathArray {
    param(
        [Parameter()]
        [object[]]$Candidates,

        [Parameter(Mandatory = $true)]
        [string]$WorkspaceRoot
    )

    $items = New-Object System.Collections.Generic.List[string]
    foreach ($candidate in $Candidates) {
        foreach ($entry in (Convert-ToStringArray -Value $candidate)) {
            $relativePath = Convert-ToRelativeWorkspacePath -Path $entry -WorkspaceRoot $WorkspaceRoot
            if ([string]::IsNullOrWhiteSpace($relativePath)) {
                continue
            }

            if ($relativePath -match '^(\.artifacts[/\\]agent-logs[/\\].+\.(ndjson|md))$') {
                continue
            }

            $items.Add($relativePath)
        }
    }

    return ,@($items.ToArray() | Select-Object -Unique)
}

function Convert-ToNullableInteger {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return $null
    }

    try {
        return [int64]$Value
    }
    catch {
        return $null
    }
}

function Convert-ToNullableNumber {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return $null
    }

    try {
        return [double]$Value
    }
    catch {
        return $null
    }
}

function Convert-ToCanonicalValidationArray {
    param(
        [Parameter()]
        [object]$Value
    )

    $items = New-Object System.Collections.Generic.List[object]
    foreach ($entry in (Convert-ToStringArray -Value $Value)) {
        if ([string]::IsNullOrWhiteSpace($entry)) {
            continue
        }

        $items.Add([ordered]@{
            kind = 'other'
            target = 'hook-observed-step'
            result = 'passed'
            details = $entry
        })
    }

    if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
        foreach ($entry in $Value) {
            if ($null -eq $entry) {
                continue
            }

            if ($entry -is [System.Collections.IDictionary]) {
                $items.Add([ordered]@{
                    kind = $(if (Test-MeaningfulString -Value ([string]$entry.kind)) { [string]$entry.kind } else { 'other' })
                    target = $(if (Test-MeaningfulString -Value ([string]$entry.target)) { [string]$entry.target } else { 'hook-observed-step' })
                    result = $(if (Test-MeaningfulString -Value ([string]$entry.result)) { [string]$entry.result } else { 'passed' })
                    details = $(if (Test-MeaningfulString -Value ([string]$entry.details)) { [string]$entry.details } else { $null })
                })
            }
        }
    }

    return ,($items.ToArray())
}

function Add-IfPresent {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.Specialized.OrderedDictionary]$Target,

        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter()]
        [AllowNull()]
        [object]$Value
    )

    if ($null -ne $Value) {
        $Target[$Name] = $Value
    }
}

function Get-OptionalPropertyValue {
    param(
        [Parameter()]
        [object]$InputObject,

        [Parameter(Mandatory = $true)]
        [string]$PropertyName
    )

    if ($null -eq $InputObject) {
        return $null
    }

    if ($InputObject -is [System.Collections.IDictionary]) {
        if ($InputObject.Contains($PropertyName)) {
            return $InputObject[$PropertyName]
        }

        return $null
    }

    $property = $InputObject.PSObject.Properties[$PropertyName]
    if ($null -eq $property) {
        return $null
    }

    return $property.Value
}

function Convert-ToBooleanOrNull {
    param(
        [Parameter()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return $null
    }

    if ($Value -is [bool]) {
        return [bool]$Value
    }

    $text = [string]$Value
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }

    switch ($text.Trim().ToLowerInvariant()) {
        'true' { return $true }
        'yes' { return $true }
        '1' { return $true }
        'false' { return $false }
        'no' { return $false }
        '0' { return $false }
        default { return $null }
    }
}

function Resolve-EntryTimestampInfo {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Payload
    )

    $writerNow = (Get-Date).ToUniversalTime()
    $rawTimestamp = Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('timestamp'), @('eventTimestamp'), @('event_timestamp')
    ) -Default $null

    if (Test-MeaningfulString -Value ([string]$rawTimestamp)) {
        try {
            $parsedTimestamp = [datetimeoffset]::Parse(
                [string]$rawTimestamp,
                [System.Globalization.CultureInfo]::InvariantCulture,
                [System.Globalization.DateTimeStyles]::RoundtripKind
            )
            $utcTimestamp = $parsedTimestamp.ToUniversalTime()
            return [ordered]@{
                iso_timestamp = $utcTimestamp.ToString('o')
                utc_datetime = $utcTimestamp.UtcDateTime
                timestamp_source = 'payload'
                clock_skew_ms = [math]::Round(($writerNow - $utcTimestamp.UtcDateTime).TotalMilliseconds, 2)
            }
        }
        catch {
        }
    }

    return [ordered]@{
        iso_timestamp = $writerNow.ToString('o')
        utc_datetime = $writerNow
        timestamp_source = 'writer-clock'
        clock_skew_ms = 0
    }
}

function Build-CommunicationMetadata {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Payload,

        [Parameter()]
        [hashtable]$PreviousEntry
    )

    $userLanguage = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('userLanguage'), @('user_language'), @('communication','user_language')
    ) -Default $null)
    $agentLanguage = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('agentLanguage'), @('agent_language'), @('communication','agent_language'), @('language')
    ) -Default $null)
    $logLanguage = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('logLanguage'), @('log_language'), @('communication','log_language'), @('language')
    ) -Default $null)
    $locale = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('locale'), @('communication','locale')
    ) -Default $null)
    $notes = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('communication','notes')
    ) -Default $null)

    $previousCommunication = $null
    if ($null -ne $PreviousEntry -and $PreviousEntry.ContainsKey('communication')) {
        $previousCommunication = $PreviousEntry.communication
    }

    if (-not (Test-MeaningfulString -Value $userLanguage)) {
        $userLanguage = [string](Get-OptionalPropertyValue -InputObject $previousCommunication -PropertyName 'user_language')
    }
    if (-not (Test-MeaningfulString -Value $agentLanguage)) {
        $agentLanguage = [string](Get-OptionalPropertyValue -InputObject $previousCommunication -PropertyName 'agent_language')
    }
    if (-not (Test-MeaningfulString -Value $logLanguage)) {
        $logLanguage = [string](Get-OptionalPropertyValue -InputObject $previousCommunication -PropertyName 'log_language')
    }
    if (-not (Test-MeaningfulString -Value $locale)) {
        $locale = [string](Get-OptionalPropertyValue -InputObject $previousCommunication -PropertyName 'locale')
    }

    $communication = [ordered]@{}
    Add-IfPresent -Target $communication -Name 'user_language' -Value $(if (Test-MeaningfulString -Value $userLanguage) { $userLanguage } else { $null })
    Add-IfPresent -Target $communication -Name 'agent_language' -Value $(if (Test-MeaningfulString -Value $agentLanguage) { $agentLanguage } else { $null })
    Add-IfPresent -Target $communication -Name 'log_language' -Value $(if (Test-MeaningfulString -Value $logLanguage) { $logLanguage } else { $null })
    Add-IfPresent -Target $communication -Name 'locale' -Value $(if (Test-MeaningfulString -Value $locale) { $locale } else { $null })

    $consistency = 'unknown'
    if ((Test-MeaningfulString -Value $userLanguage) -and (Test-MeaningfulString -Value $agentLanguage)) {
        if ($userLanguage.Trim().Equals($agentLanguage.Trim(), [System.StringComparison]::OrdinalIgnoreCase)) {
            $consistency = 'matched'
        }
        else {
            $consistency = 'shifted'
        }
    }
    elseif ((Test-MeaningfulString -Value $agentLanguage) -and (Test-MeaningfulString -Value $logLanguage)) {
        if ($agentLanguage.Trim().Equals($logLanguage.Trim(), [System.StringComparison]::OrdinalIgnoreCase)) {
            $consistency = 'matched'
        }
        else {
            $consistency = 'mixed'
        }
    }
    Add-IfPresent -Target $communication -Name 'consistency' -Value $consistency
    Add-IfPresent -Target $communication -Name 'notes' -Value $(if (Test-MeaningfulString -Value $notes) { $notes } else { $null })

    if ($communication.Count -eq 0) {
        return $null
    }

    return $communication
}

function Build-CollaborationMetadata {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Payload
    )

    $userSolutionAdopted = Convert-ToBooleanOrNull (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('userSolutionAdopted'), @('user_solution_adopted'), @('collaboration','user_solution_adopted')
    ) -Default $null)
    $userSolutionImproved = Convert-ToBooleanOrNull (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('userSolutionImprovedAgentProposal'), @('user_solution_improved_agent_proposal'), @('collaboration','user_solution_improved_agent_proposal')
    ) -Default $null)
    $adoptedSource = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('adoptedSolutionSource'), @('adopted_solution_source'), @('collaboration','adopted_solution_source')
    ) -Default $null)
    $userProposalSummary = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('userProposalSummary'), @('user_proposal_summary'), @('collaboration','user_proposal_summary')
    ) -Default $null)
    $agentProposalSummary = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('agentProposalSummary'), @('agent_proposal_summary'), @('collaboration','agent_proposal_summary')
    ) -Default $null)
    $adoptedSolutionSummary = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('adoptedSolutionSummary'), @('adopted_solution_summary'), @('collaboration','adopted_solution_summary')
    ) -Default $null)
    $improvementSummary = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('improvementSummary'), @('improvement_summary'), @('collaboration','improvement_summary')
    ) -Default $null)

    $collaboration = [ordered]@{}
    Add-IfPresent -Target $collaboration -Name 'user_solution_adopted' -Value $userSolutionAdopted
    Add-IfPresent -Target $collaboration -Name 'user_solution_improved_agent_proposal' -Value $userSolutionImproved
    Add-IfPresent -Target $collaboration -Name 'adopted_solution_source' -Value $(if (Test-MeaningfulString -Value $adoptedSource) { $adoptedSource.ToLowerInvariant() } elseif ($userSolutionAdopted -eq $true) { 'user' } else { $null })
    Add-IfPresent -Target $collaboration -Name 'user_proposal_summary' -Value $(if (Test-MeaningfulString -Value $userProposalSummary) { $userProposalSummary } else { $null })
    Add-IfPresent -Target $collaboration -Name 'agent_proposal_summary' -Value $(if (Test-MeaningfulString -Value $agentProposalSummary) { $agentProposalSummary } else { $null })
    Add-IfPresent -Target $collaboration -Name 'adopted_solution_summary' -Value $(if (Test-MeaningfulString -Value $adoptedSolutionSummary) { $adoptedSolutionSummary } else { $null })
    Add-IfPresent -Target $collaboration -Name 'improvement_summary' -Value $(if (Test-MeaningfulString -Value $improvementSummary) { $improvementSummary } else { $null })

    if ($collaboration.Count -eq 0) {
        return $null
    }

    return $collaboration
}

function Build-PositiveFeedbackMetadata {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Payload,

        [Parameter()]
        [object]$Communication,

        [Parameter()]
        [object]$Collaboration,

        [Parameter()]
        [object[]]$Validation,

        [Parameter()]
        [AllowNull()]
        [string]$Status
    )

    $explicitSummary = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('positiveFeedbackSummary'), @('positive_feedback','summary'), @('whatWorkedWell'), @('successSummary')
    ) -Default $null)
    $explicitSignals = Convert-ToStringArray -Value (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('positiveFeedbackSignals'), @('positive_feedback','signals'), @('whatWorkedWellItems'), @('wins'), @('successSignals'), @('strengths')
    ) -Default @())

    $signalList = New-Object System.Collections.Generic.List[string]
    foreach ($signal in $explicitSignals) {
        if ([string]::IsNullOrWhiteSpace($signal)) {
            continue
        }

        $normalizedSignal = $signal.Trim()
        if ($normalizedSignal.Length -gt 200) {
            $normalizedSignal = $normalizedSignal.Substring(0, 200)
        }
        if (-not $signalList.Contains($normalizedSignal)) {
            $signalList.Add($normalizedSignal)
        }
    }

    $source = $null
    if ((Test-MeaningfulString -Value $explicitSummary) -or $signalList.Count -gt 0) {
        $source = 'payload'
    }

    $consistency = [string](Get-OptionalPropertyValue -InputObject $Communication -PropertyName 'consistency')
    if ($consistency -eq 'matched') {
        $signal = 'User and agent stayed in the same working language.'
        if (-not $signalList.Contains($signal)) {
            $signalList.Add($signal)
        }
        if ($source -eq 'payload') {
            $source = 'mixed'
        }
        elseif (-not (Test-MeaningfulString -Value $source)) {
            $source = 'inferred'
        }
    }

    $userImprovedAgent = Convert-ToBooleanOrNull (Get-OptionalPropertyValue -InputObject $Collaboration -PropertyName 'user_solution_improved_agent_proposal')
    if ($userImprovedAgent -eq $true) {
        $signal = 'The user proposed an improvement that beat the earlier agent proposal.'
        if (-not $signalList.Contains($signal)) {
            $signalList.Add($signal)
        }
        if ($source -eq 'payload') {
            $source = 'mixed'
        }
        elseif (-not (Test-MeaningfulString -Value $source)) {
            $source = 'inferred'
        }
    }

    $passedValidationCount = 0
    $nonPassingValidationCount = 0
    foreach ($entry in @($Validation)) {
        $result = [string](Get-OptionalPropertyValue -InputObject $entry -PropertyName 'result')
        if ([string]::IsNullOrWhiteSpace($result)) {
            continue
        }

        switch ($result.Trim().ToLowerInvariant()) {
            'passed' { $passedValidationCount++ }
            'failed' { $nonPassingValidationCount++ }
            'inconclusive' { $nonPassingValidationCount++ }
        }
    }
    if ($passedValidationCount -gt 0 -and $nonPassingValidationCount -eq 0) {
        $signal = 'Focused validation checks passed for this step.'
        if (-not $signalList.Contains($signal)) {
            $signalList.Add($signal)
        }
        if ($source -eq 'payload') {
            $source = 'mixed'
        }
        elseif (-not (Test-MeaningfulString -Value $source)) {
            $source = 'inferred'
        }
    }

    if (-not (Test-MeaningfulString -Value $explicitSummary) -and $signalList.Count -eq 0) {
        return $null
    }

    $summary = $explicitSummary
    if (-not (Test-MeaningfulString -Value $summary)) {
        $signalPreview = @($signalList.ToArray() | Select-Object -First 2)
        $summary = [string]::Join(' ', @('Positive signals observed:', [string]::Join(' ', $signalPreview)))
    }
    if ($summary.Length -gt 500) {
        $summary = $summary.Substring(0, 500)
    }

    $positiveFeedback = [ordered]@{}
    Add-IfPresent -Target $positiveFeedback -Name 'source' -Value $(if (Test-MeaningfulString -Value $source) { $source } else { 'inferred' })
    Add-IfPresent -Target $positiveFeedback -Name 'summary' -Value $summary
    if ($signalList.Count -gt 0) {
        $positiveFeedback['signals'] = [object[]]@($signalList.ToArray() | Select-Object -First 5)
    }

    return $positiveFeedback
}

function Resolve-RequestClass {
    param(
        [Parameter()]
        [AllowNull()]
        [string]$ExplicitClass,

        [Parameter()]
        [AllowNull()]
        [string]$TaskSummary,

        [Parameter()]
        [AllowNull()]
        [string]$ToolName,

        [Parameter()]
        [AllowNull()]
        [string]$PreviousClass
    )

    $allowed = @("implementation", "review", "debugging", "architecture", "documentation", "research", "operations", "other")
    if (Test-MeaningfulString -Value $ExplicitClass) {
        $candidate = $ExplicitClass.Trim().ToLowerInvariant()
        if ($allowed -contains $candidate) {
            return $candidate
        }
    }

    if (Test-MeaningfulString -Value $PreviousClass) {
        return $PreviousClass.Trim().ToLowerInvariant()
    }

    $surface = (([string]$TaskSummary) + " " + ([string]$ToolName)).ToLowerInvariant()
    if ($surface -match 'review|audit|inspect') { return 'review' }
    if ($surface -match 'debug|trace|investigat|root cause|failure|error|bug') { return 'debugging' }
    if ($surface -match 'architect|design|adr|boundary|interface') { return 'architecture' }
    if ($surface -match 'readme|document|docs|documentation') { return 'documentation' }
    if ($surface -match 'research|explore|compare|analy') { return 'research' }
    if ($surface -match 'implement|create|build|add|change|modify|refactor|update|fix') { return 'implementation' }
    return 'operations'
}

function Resolve-EventPhase {
    param(
        [Parameter(Mandatory = $true)]
        [string]$EventName
    )

    switch ($EventName) {
        'UserPromptSubmit' { return 'prompt' }
        'PreToolUse' { return 'tool' }
        'PostToolUse' { return 'tool' }
        'SubagentStart' { return 'subagent' }
        'SubagentStop' { return 'subagent' }
        'PreCompact' { return 'compaction' }
        'Stop' { return 'session' }
        default { return 'other' }
    }
}

function Resolve-CheckpointKind {
    param(
        [Parameter(Mandatory = $true)]
        [string]$EventName
    )

    switch ($EventName) {
        'UserPromptSubmit' { return 'start' }
        'PreToolUse' { return 'start' }
        'SubagentStart' { return 'start' }
        'PostToolUse' { return 'progress' }
        'PreCompact' { return 'progress' }
        'SubagentStop' { return 'stop' }
        'Stop' { return 'stop' }
        default { return 'progress' }
    }
}

function Resolve-Status {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Payload,

        [Parameter(Mandatory = $true)]
        [string]$EventName,

        [Parameter()]
        [hashtable]$PreviousEntry
    )

    $explicitStatus = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('status'), @('result'), @('toolResult','status'), @('tool_result','status')
    ) -Default $null)
    if (Test-MeaningfulString -Value $explicitStatus) {
        $candidate = $explicitStatus.Trim().ToLowerInvariant()
        switch -Regex ($candidate) {
            'complete|completed|success|succeeded|ok|passed' { return 'completed' }
            'partial|warning|inconclusive' { return 'partial' }
            'block|blocked|needs-input' { return 'blocked' }
            'fail|failed|error|denied' { return 'failed' }
        }
    }

    if ($EventName -eq 'PreToolUse' -or $EventName -eq 'UserPromptSubmit' -or $EventName -eq 'SubagentStart') {
        return 'partial'
    }

    if ($null -ne $PreviousEntry -and $PreviousEntry.ContainsKey('status') -and $EventName -eq 'Stop') {
        return [string]$PreviousEntry.status
    }

    return 'completed'
}

function Build-DefaultActions {
    param(
        [Parameter(Mandatory = $true)]
        [string]$EventName,

        [Parameter()]
        [string]$ToolName,

        [Parameter()]
        [string]$AgentName,

        [Parameter()]
        [string]$PromptSummary,

        [Parameter()]
        [bool]$UsedPreviousContext
    )

    $actions = New-Object System.Collections.Generic.List[string]
    switch ($EventName) {
        'UserPromptSubmit' { $actions.Add('Captured user prompt for agent workflow') }
        'PreToolUse' { $actions.Add('Tool execution started') }
        'PostToolUse' { $actions.Add('Tool execution finished') }
        'SubagentStart' { $actions.Add('Subagent execution started') }
        'SubagentStop' { $actions.Add('Subagent execution finished') }
        'PreCompact' { $actions.Add('Conversation compaction checkpoint captured') }
        'Stop' { $actions.Add('Conversation stop checkpoint captured') }
        default { $actions.Add("Hook captured $EventName") }
    }

    if (Test-MeaningfulString -Value $ToolName) {
        $actions.Add("Observed tool $ToolName")
    }
    if ((Test-MeaningfulString -Value $AgentName -Disallowed $script:PlaceholderAgents) -and ($EventName -eq 'SubagentStart' -or $EventName -eq 'SubagentStop')) {
        $actions.Add("Tracked subagent $AgentName")
    }
    if (Test-MeaningfulString -Value $PromptSummary -Disallowed $script:PlaceholderSummaries) {
        $actions.Add("Resolved task summary from $(if ($UsedPreviousContext) { 'recent context' } else { 'payload' })")
    }

    return ,($actions.ToArray())
}

function Should-RecordEvent {
    param(
        [Parameter(Mandatory = $true)]
        [string]$EventName,

        [Parameter()]
        [string]$ToolName,

        [Parameter()]
        [string]$PromptSummary,

        [Parameter()]
        [string]$AgentName,

        [Parameter()]
        [hashtable]$PreviousEntry,

        [Parameter()]
        [bool]$UsedPreviousContext
    )

    if ($script:IntermediateEvents -notcontains $EventName -and $EventName -ne 'Stop') {
        return $false
    }

    if ($EventName -eq 'UserPromptSubmit') {
        return (Test-MeaningfulString -Value $PromptSummary -Disallowed $script:PlaceholderSummaries)
    }

    if ($EventName -eq 'PreToolUse' -or $EventName -eq 'PostToolUse') {
        return (Test-MeaningfulString -Value $ToolName)
    }

    if ($EventName -eq 'SubagentStart' -or $EventName -eq 'SubagentStop') {
        return (Test-MeaningfulString -Value $AgentName -Disallowed $script:PlaceholderAgents) -or $UsedPreviousContext
    }

    if ($EventName -eq 'PreCompact') {
        return $true
    }

    return $true
}

function Test-IsRichEntry {
    param(
        [Parameter()]
        [hashtable]$Entry
    )

    if ($null -eq $Entry) {
        return $false
    }

    if (Test-MeaningfulString -Value ([string]$Entry.agent) -Disallowed $script:PlaceholderAgents) {
        return $true
    }

    if (Test-MeaningfulString -Value ([string]$Entry.task_summary) -Disallowed $script:PlaceholderSummaries) {
        return $true
    }

    foreach ($propertyName in @('tools_used', 'files_touched', 'validation', 'telemetry')) {
        if ($Entry.ContainsKey($propertyName) -and $null -ne $Entry[$propertyName]) {
            if ($Entry[$propertyName] -is [System.Collections.IEnumerable] -and -not ($Entry[$propertyName] -is [string])) {
                foreach ($item in $Entry[$propertyName]) {
                    return $true
                }
            }
            else {
                return $true
            }
        }
    }

    return $false
}

function Get-JsonFragments {
    param(
        [Parameter()]
        [string]$Line
    )

    if ([string]::IsNullOrWhiteSpace($Line)) {
        return @()
    }

    return [regex]::Split($Line.Trim(), '(?<=\})\s*(?=\{)')
}

function Get-LogEntriesFromFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath
    )

    $entries = New-Object System.Collections.Generic.List[hashtable]
    foreach ($line in Get-Content -LiteralPath $FilePath) {
        foreach ($fragment in (Get-JsonFragments -Line $line)) {
            if ([string]::IsNullOrWhiteSpace($fragment)) {
                continue
            }

            try {
                $entry = $fragment | ConvertFrom-Json -Depth 32 -AsHashtable
                if ($entry -is [hashtable]) {
                    $entries.Add($entry)
                }
            }
            catch {
                continue
            }
        }
    }

    return $entries.ToArray()
}

function Get-RecentContextEntry {
    param(
        [Parameter(Mandatory = $true)]
        [string]$LogDirectory,

        [Parameter()]
        [string]$SessionId,

        [Parameter()]
        [string]$RunId
    )

    if (-not (Test-Path -LiteralPath $LogDirectory)) {
        return $null
    }

    $files = Get-ChildItem -LiteralPath $LogDirectory -Filter *.ndjson -File | Sort-Object LastWriteTimeUtc -Descending
    foreach ($file in $files) {
        $entries = Get-LogEntriesFromFile -FilePath $file.FullName
        [array]::Reverse($entries)
        foreach ($entry in $entries) {
            $sameRun = (Test-MeaningfulString -Value $RunId) -and ($entry.run_id -eq $RunId)
            $sameSession = (Test-MeaningfulString -Value $SessionId) -and ($entry.conversation_id -eq $SessionId)
            if ((-not $sameRun) -and (-not $sameSession)) {
                continue
            }

            if (Test-IsRichEntry -Entry $entry) {
                return $entry
            }
        }
    }

    return $null
}

function Build-Telemetry {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Payload,

        [Parameter()]
        [hashtable]$PreviousEntry,

        [Parameter()]
        [string[]]$Actions,

        [Parameter()]
        [string[]]$ToolsUsed,

        [Parameter()]
        [string[]]$FilesTouched,

        [Parameter()]
        [object[]]$Validation,

        [Parameter()]
        [string[]]$Risks,

        [Parameter()]
        [string]$ToolName,

        [Parameter()]
        [bool]$UsedPreviousContext,

        [Parameter(Mandatory = $true)]
        [string]$EventName,

        [Parameter(Mandatory = $true)]
        [string]$Status,

        [Parameter(Mandatory = $true)]
        [string]$TimestampSource,

        [Parameter()]
        [double]$ClockSkewMs = 0
    )

    $telemetry = [ordered]@{}
    $contextSource = if ($UsedPreviousContext) { 'carried-forward' } else { 'hook-derived' }

    $modelName = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('modelName'), @('model_name'), @('model'), @('model','name'), @('llm','model'), @('telemetry','model','name'), @('metrics','model','name')
    ) -Default $null)
    $modelProvider = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('modelProvider'), @('model_provider'), @('provider'), @('model','provider'), @('telemetry','model','provider'), @('metrics','model','provider')
    ) -Default $null)
    $modelDeployment = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('deployment'), @('deploymentName'), @('deployment_name'), @('model','deployment'), @('telemetry','model','deployment')
    ) -Default $null)
    $modelVersion = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('modelVersion'), @('model_version'), @('model','version'), @('telemetry','model','version')
    ) -Default $null)

    $tokenInput = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('usage','prompt_tokens'), @('usage','input_tokens'), @('tokenUsage','promptTokens'), @('tokenUsage','inputTokens'), @('telemetry','tokens','input'), @('metrics','tokens','input'), @('token_usage','input_tokens'), @('inputTokens'), @('promptTokens'), @('prompt_tokens'), @('input_tokens')
    ))
    $tokenOutput = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('usage','completion_tokens'), @('usage','output_tokens'), @('tokenUsage','completionTokens'), @('tokenUsage','outputTokens'), @('telemetry','tokens','output'), @('metrics','tokens','output'), @('token_usage','output_tokens'), @('outputTokens'), @('completionTokens'), @('completion_tokens'), @('output_tokens')
    ))
    $tokenTotal = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('usage','total_tokens'), @('tokenUsage','totalTokens'), @('telemetry','tokens','total'), @('metrics','tokens','total'), @('token_usage','total_tokens'), @('totalTokens'), @('total_tokens')
    ))
    if ($null -eq $tokenTotal -and ($null -ne $tokenInput -or $null -ne $tokenOutput)) {
        $tokenTotal = [int64](($tokenInput | ForEach-Object { if ($null -eq $_) { 0 } else { $_ } }) + ($tokenOutput | ForEach-Object { if ($null -eq $_) { 0 } else { $_ } }))
    }
    $tokenCached = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('usage','cached_tokens'), @('telemetry','tokens','cached'), @('cachedTokens'), @('cached_tokens')
    ))
    $tokenReasoning = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('usage','reasoning_tokens'), @('telemetry','tokens','reasoning'), @('reasoningTokens'), @('reasoning_tokens')
    ))
    $tokenRequestedMax = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('maxTokens'), @('max_tokens'), @('requestedMaxTokens'), @('requested_max_tokens'), @('telemetry','tokens','requested_max')
    ))
    $tokenContextWindow = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('contextWindow'), @('context_window'), @('telemetry','tokens','context_window')
    ))

    $durationMs = Convert-ToNullableNumber (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('durationMs'), @('duration_ms'), @('elapsedMs'), @('elapsed_ms'), @('latencyMs'), @('latency_ms'), @('telemetry','performance','duration_ms'), @('metrics','performance','duration_ms')
    ))
    if ($null -eq $durationMs) {
        $durationSeconds = Convert-ToNullableNumber (Get-FirstNestedValue -InputObject $Payload -Paths @(
            @('duration_seconds')
        ) -Default $null)
        if ($null -ne $durationSeconds) {
            $durationMs = [math]::Round($durationSeconds * 1000, 2)
        }
    }
    $queueMs = Convert-ToNullableNumber (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('queueMs'), @('queue_ms'), @('telemetry','performance','queue_ms')
    ))
    $executionMs = Convert-ToNullableNumber (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('executionMs'), @('execution_ms'), @('telemetry','performance','execution_ms')
    ))
    $modelMs = Convert-ToNullableNumber (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('modelMs'), @('model_ms'), @('telemetry','performance','model_ms')
    ))
    $toolMs = Convert-ToNullableNumber (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('toolMs'), @('tool_ms'), @('telemetry','performance','tool_ms')
    ))
    $toolCallCount = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('toolCallCount'), @('tool_call_count'), @('telemetry','performance','tool_call_count'), @('metrics','performance','tool_call_count')
    ))

    $currency = [string](Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('cost','currency'), @('telemetry','cost','currency'), @('currency')
    ) -Default $null)
    $costEstimatedTotal = Convert-ToNullableNumber (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('cost','estimated_total'), @('telemetry','cost','estimated_total'), @('token_usage','estimated_cost_usd'), @('estimatedCost'), @('estimated_cost')
    ))
    $costEstimatedInput = Convert-ToNullableNumber (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('cost','estimated_input'), @('telemetry','cost','estimated_input')
    ))
    $costEstimatedOutput = Convert-ToNullableNumber (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('cost','estimated_output'), @('telemetry','cost','estimated_output')
    ))

    if (Test-MeaningfulString -Value $modelName) {
        $contextSource = 'payload'
    }
    if ($null -ne $tokenInput -or $null -ne $tokenOutput -or $null -ne $tokenTotal -or $null -ne $durationMs -or $null -ne $toolCallCount) {
        $contextSource = 'payload'
    }

    $previousTelemetry = $null
    $previousModel = $null
    if ($null -ne $PreviousEntry -and $PreviousEntry.ContainsKey('telemetry')) {
        $previousTelemetry = $PreviousEntry.telemetry
        if ($null -ne $previousTelemetry -and $previousTelemetry -is [System.Collections.IDictionary] -and $previousTelemetry.Contains('model')) {
            $previousModel = $previousTelemetry.model
        }
        elseif ($null -ne $previousTelemetry -and $previousTelemetry.PSObject.Properties['model']) {
            $previousModel = $previousTelemetry.model
        }
    }

    $telemetry['context_source'] = $contextSource
    $telemetry['timestamp_source'] = $TimestampSource
    if ($TimestampSource -eq 'payload') {
        $telemetry['clock_skew_ms'] = $ClockSkewMs
    }
    $telemetry['primary_operation'] = if (Test-MeaningfulString -Value $ToolName) { $ToolName } else { [string](Get-FirstNestedValue -InputObject $Payload -Paths @(@('operation'), @('primaryOperation'), @('primary_operation')) -Default 'lifecycle') }

    $model = [ordered]@{}
    Add-IfPresent -Target $model -Name 'provider' -Value $(if (Test-MeaningfulString -Value $modelProvider) { $modelProvider } else { Get-OptionalPropertyValue -InputObject $previousModel -PropertyName 'provider' })
    Add-IfPresent -Target $model -Name 'name' -Value $(if (Test-MeaningfulString -Value $modelName) { $modelName } else { Get-OptionalPropertyValue -InputObject $previousModel -PropertyName 'name' })
    Add-IfPresent -Target $model -Name 'deployment' -Value $(if (Test-MeaningfulString -Value $modelDeployment) { $modelDeployment } else { Get-OptionalPropertyValue -InputObject $previousModel -PropertyName 'deployment' })
    Add-IfPresent -Target $model -Name 'version' -Value $(if (Test-MeaningfulString -Value $modelVersion) { $modelVersion } else { Get-OptionalPropertyValue -InputObject $previousModel -PropertyName 'version' })
    if ($model.Count -gt 0) {
        $telemetry['model'] = $model
    }

    $tokens = [ordered]@{}
    Add-IfPresent -Target $tokens -Name 'input' -Value $tokenInput
    Add-IfPresent -Target $tokens -Name 'output' -Value $tokenOutput
    Add-IfPresent -Target $tokens -Name 'total' -Value $tokenTotal
    Add-IfPresent -Target $tokens -Name 'cached' -Value $tokenCached
    Add-IfPresent -Target $tokens -Name 'reasoning' -Value $tokenReasoning
    Add-IfPresent -Target $tokens -Name 'requested_max' -Value $tokenRequestedMax
    Add-IfPresent -Target $tokens -Name 'context_window' -Value $tokenContextWindow
    if ($tokens.Count -gt 0) {
        $telemetry['tokens'] = $tokens
    }

    $performance = [ordered]@{}
    Add-IfPresent -Target $performance -Name 'duration_ms' -Value $durationMs
    Add-IfPresent -Target $performance -Name 'queue_ms' -Value $queueMs
    Add-IfPresent -Target $performance -Name 'execution_ms' -Value $executionMs
    Add-IfPresent -Target $performance -Name 'model_ms' -Value $modelMs
    Add-IfPresent -Target $performance -Name 'tool_ms' -Value $toolMs
    Add-IfPresent -Target $performance -Name 'tool_call_count' -Value $(if ($null -ne $toolCallCount) { $toolCallCount } elseif ($ToolsUsed.Count -gt 0) { $ToolsUsed.Count } else { $null })
    if ($performance.Count -gt 0) {
        $telemetry['performance'] = $performance
    }

    $cost = [ordered]@{}
    Add-IfPresent -Target $cost -Name 'currency' -Value $(if (Test-MeaningfulString -Value $currency) { $currency } else { $null })
    Add-IfPresent -Target $cost -Name 'estimated_total' -Value $costEstimatedTotal
    Add-IfPresent -Target $cost -Name 'estimated_input' -Value $costEstimatedInput
    Add-IfPresent -Target $cost -Name 'estimated_output' -Value $costEstimatedOutput
    if ($cost.Count -gt 0) {
        $telemetry['cost'] = $cost
    }

    $stats = [ordered]@{
        actions_count = $Actions.Count
        tools_used_count = $ToolsUsed.Count
        files_touched_count = $FilesTouched.Count
        validation_count = $Validation.Count
        risk_count = $Risks.Count
    }
    $telemetry['stats'] = $stats

    $sequence = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('sequence'), @('eventSequence'), @('event_sequence'), @('telemetry','progress','sequence')
    ))
    $completedSteps = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('completedSteps'), @('completed_steps'), @('telemetry','progress','completed_steps')
    ))
    $totalSteps = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('totalSteps'), @('total_steps'), @('telemetry','progress','total_steps')
    ))
    $progressPct = Convert-ToNullableNumber (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('progressPct'), @('progress_pct'), @('progress_percent'), @('telemetry','progress','progress_pct')
    ))
    if ($null -eq $progressPct -and $null -ne $completedSteps -and $null -ne $totalSteps -and $totalSteps -gt 0) {
        $progressPct = [math]::Round(($completedSteps / $totalSteps) * 100, 2)
    }
    $retryCount = Convert-ToNullableInteger (Get-FirstNestedValue -InputObject $Payload -Paths @(
        @('retryCount'), @('retry_count'), @('telemetry','progress','retry_count')
    ))

    $progress = [ordered]@{
        event_phase = Resolve-EventPhase -EventName $EventName
        checkpoint_kind = Resolve-CheckpointKind -EventName $EventName
        tool_status = $(if ($EventName -eq 'PreToolUse') { 'running' } elseif ($EventName -eq 'PostToolUse') { if ($Status -eq 'failed') { 'failed' } else { 'succeeded' } } elseif ($EventName -eq 'Stop' -and $Status -eq 'failed') { 'failed' } elseif ($EventName -eq 'Stop') { 'succeeded' } else { 'unknown' })
    }
    Add-IfPresent -Target $progress -Name 'sequence' -Value $sequence
    Add-IfPresent -Target $progress -Name 'completed_steps' -Value $completedSteps
    Add-IfPresent -Target $progress -Name 'total_steps' -Value $totalSteps
    Add-IfPresent -Target $progress -Name 'progress_pct' -Value $progressPct
    Add-IfPresent -Target $progress -Name 'retry_count' -Value $retryCount
    $telemetry['progress'] = $progress

    $isDegraded = -not (Test-MeaningfulString -Value ([string]$telemetry['primary_operation'])) -and -not $telemetry.Contains('model') -and -not $telemetry.Contains('tokens') -and -not $telemetry.Contains('performance')
    if (-not (Test-MeaningfulString -Value ([string]$telemetry['primary_operation']) -Disallowed @('lifecycle'))) {
        $isDegraded = $isDegraded -and ($Actions.Count -le 1) -and ($FilesTouched.Count -eq 0) -and ($Validation.Count -eq 0)
    }
    $telemetry['degraded'] = $isDegraded

    return $telemetry
}

function Should-SkipEntry {
    param(
        [Parameter()]
        [hashtable]$PreviousEntry,

        [Parameter(Mandatory = $true)]
        [string]$AgentName,

        [Parameter(Mandatory = $true)]
        [string]$TaskSummary,

        [Parameter()]
        [string[]]$ToolsUsed,

        [Parameter()]
        [string[]]$FilesTouched,

        [Parameter()]
        [object[]]$Validation,

        [Parameter(Mandatory = $true)]
        [hashtable]$Telemetry,

        [Parameter()]
        [bool]$UsedPreviousContext
    )

    if (-not $UsedPreviousContext -or $null -eq $PreviousEntry) {
        return $false
    }

    if ($ToolsUsed.Count -gt 0 -or $FilesTouched.Count -gt 0 -or $Validation.Count -gt 0) {
        return $false
    }

    if ($Telemetry.ContainsKey('performance') -or $Telemetry.ContainsKey('tokens') -or $Telemetry.ContainsKey('cost')) {
        return $false
    }

    return ($PreviousEntry.agent -eq $AgentName) -and ($PreviousEntry.task_summary -eq $TaskSummary)
}

try {
    $rawInput = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($rawInput)) {
        return
    }

    $payload = $rawInput | ConvertFrom-Json -Depth 32 -AsHashtable
    $resolvedRoot = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
    $logDirectory = Join-Path -Path $resolvedRoot -ChildPath ".artifacts/agent-logs"
    $null = New-Item -ItemType Directory -Force -Path $logDirectory

    $timestampInfo = Resolve-EntryTimestampInfo -Payload $payload
    $timestamp = [string]$timestampInfo.iso_timestamp
    $logDate = [datetime]$timestampInfo.utc_datetime

    $eventName = Get-NestedValue -InputObject $payload -Path @("hookEventName") -Default $null
    if ([string]::IsNullOrWhiteSpace([string]$eventName)) {
        $eventName = Get-NestedValue -InputObject $payload -Path @("hook_event_name") -Default "Stop"
    }

    $sessionId = Get-NestedValue -InputObject $payload -Path @("sessionId") -Default $null
    if ([string]::IsNullOrWhiteSpace([string]$sessionId)) {
        $sessionId = Get-NestedValue -InputObject $payload -Path @("session_id") -Default ""
    }

    $runId = Get-NestedValue -InputObject $payload -Path @("runId") -Default $null
    if ([string]::IsNullOrWhiteSpace([string]$runId)) {
        $runId = Get-NestedValue -InputObject $payload -Path @("requestId") -Default $sessionId
    }
    if ([string]::IsNullOrWhiteSpace($runId)) {
        $runId = [guid]::NewGuid().ToString()
    }

    $previousEntry = Get-RecentContextEntry -LogDirectory $logDirectory -SessionId $sessionId -RunId $runId

    $agentName = Get-NestedValue -InputObject $payload -Path @("agentName") -Default $null
    if ([string]::IsNullOrWhiteSpace([string]$agentName)) {
        $agentName = Get-NestedValue -InputObject $payload -Path @("agent", "name") -Default $null
    }
    if ([string]::IsNullOrWhiteSpace([string]$agentName)) {
        $agentName = Get-NestedValue -InputObject $payload -Path @("agent") -Default $null
    }

    $toolName = Get-NestedValue -InputObject $payload -Path @("toolName") -Default $null
    if ([string]::IsNullOrWhiteSpace([string]$toolName)) {
        $toolName = Get-NestedValue -InputObject $payload -Path @("tool_name") -Default ""
    }

    $permissionDecision = Get-NestedValue -InputObject $payload -Path @("hookSpecificOutput", "permissionDecision") -Default $null
    if ([string]::IsNullOrWhiteSpace([string]$permissionDecision)) {
        $permissionDecision = Get-NestedValue -InputObject $payload -Path @("permissionDecision") -Default $null
    }
    if ([string]::IsNullOrWhiteSpace([string]$permissionDecision)) {
        $permissionDecision = Get-NestedValue -InputObject $payload -Path @("decision") -Default ""
    }

    $promptSummary = Get-NestedValue -InputObject $payload -Path @("prompt") -Default $null
    if ([string]::IsNullOrWhiteSpace([string]$promptSummary)) {
        $promptSummary = Get-NestedValue -InputObject $payload -Path @("task") -Default $null
    }
    if ([string]::IsNullOrWhiteSpace([string]$promptSummary)) {
        $promptSummary = Get-NestedValue -InputObject $payload -Path @("summary") -Default $null
    }

    $requestClass = [string](Get-FirstNestedValue -InputObject $payload -Paths @(
        @('requestClass'), @('request_class'), @('classification'), @('telemetry','request_class')
    ) -Default $null)

    $sourceWorkspace = [string](Get-FirstNestedValue -InputObject $payload -Paths @(
        @('workspaceRoot'), @('workspace_root'), @('workspace'), @('repoRoot'), @('repositoryRoot')
    ) -Default $null)
    $sourceRepo = [string](Get-FirstNestedValue -InputObject $payload -Paths @(
        @('repository'), @('repositoryName'), @('repository_name'), @('repoName'), @('repo_name')
    ) -Default $null)

    $eventName = [string]$eventName
    $sessionId = [string]$sessionId
    $runId = [string]$runId
    $agentName = [string]$agentName
    $toolName = [string]$toolName
    $permissionDecision = [string]$permissionDecision
    $promptSummary = [string]$promptSummary
    if ($promptSummary.Length -gt 500) {
        $promptSummary = $promptSummary.Substring(0, 500)
    }

    $usedPreviousContext = $false
    if (-not (Test-MeaningfulString -Value $agentName -Disallowed $script:PlaceholderAgents) -and $null -ne $previousEntry) {
        $agentName = [string]$previousEntry.agent
        $usedPreviousContext = $true
    }
    if (-not (Test-MeaningfulString -Value $promptSummary -Disallowed $script:PlaceholderSummaries) -and $null -ne $previousEntry) {
        $promptSummary = [string]$previousEntry.task_summary
        $usedPreviousContext = $true
    }
    if (-not (Test-MeaningfulString -Value $sourceWorkspace) -and $null -ne $previousEntry -and $previousEntry.ContainsKey('source_workspace')) {
        $sourceWorkspace = [string]$previousEntry.source_workspace
        $usedPreviousContext = $true
    }
    if (-not (Test-MeaningfulString -Value $sourceRepo) -and $null -ne $previousEntry -and $previousEntry.ContainsKey('source_repo')) {
        $sourceRepo = [string]$previousEntry.source_repo
        $usedPreviousContext = $true
    }

    if (-not (Test-MeaningfulString -Value $agentName -Disallowed $script:PlaceholderAgents)) {
        $agentName = 'unknown'
    }
    if (-not (Test-MeaningfulString -Value $promptSummary -Disallowed $script:PlaceholderSummaries)) {
        $promptSummary = "$eventName event"
    }

    $mode = 'hook'
    $status = Resolve-Status -Payload $payload -EventName $eventName -PreviousEntry $previousEntry

    $toolInputFilePath = Get-NestedValue -InputObject $payload -Path @("toolInput", "filePath") -Default $null
    $toolInputSnakeFilePath = Get-NestedValue -InputObject $payload -Path @("tool_input", "filePath") -Default $null
    $rootFilePath = Get-NestedValue -InputObject $payload -Path @("filePath") -Default $null
    $payloadFilesTouched = Get-FirstNestedValue -InputObject $payload -Paths @(@('filesTouched'), @('files_touched')) -Default @()
    $filesModified = Get-FirstNestedValue -InputObject $payload -Paths @(@('files_modified')) -Default @()
    $filesCreated = Get-FirstNestedValue -InputObject $payload -Paths @(@('files_created')) -Default @()
    $fileCandidates = @($toolInputFilePath, $toolInputSnakeFilePath, $rootFilePath, $payloadFilesTouched, $filesModified, $filesCreated)
    $filesTouched = Convert-ToWorkspacePathArray -Candidates $fileCandidates -WorkspaceRoot $resolvedRoot

    $actions = Convert-ToStringArray -Value (Get-FirstNestedValue -InputObject $payload -Paths @(@('actions'), @('activity')) -Default @())
    if ($actions.Count -eq 0) {
        $actions = Build-DefaultActions -EventName $eventName -ToolName $toolName -AgentName $agentName -PromptSummary $promptSummary -UsedPreviousContext:$usedPreviousContext
    }

    $validation = Convert-ToCanonicalValidationArray -Value (Get-FirstNestedValue -InputObject $payload -Paths @(@('validation'), @('validations'), @('validation_steps'), @('validations_performed')) -Default @())
    $risks = Convert-ToStringArray -Value (Get-FirstNestedValue -InputObject $payload -Paths @(@('risks'), @('residual_risks')) -Default @())
    if ($risks.Count -eq 0 -and [string]::IsNullOrWhiteSpace($toolName)) {
        $risks += "Lifecycle payload did not include a primary tool name"
    }
    if ($timestampInfo.timestamp_source -eq 'payload' -and [math]::Abs([double]$timestampInfo.clock_skew_ms) -gt 21600000) {
        $risks += "Payload timestamp differs from writer clock by more than 6 hours; check consumer clock or file partitioning logic"
    }
    if ($usedPreviousContext) {
        $risks += "Hook reused recent enriched context because the lifecycle payload was incomplete"
    }

    $previousRequestClass = $null
    if ($null -ne $previousEntry -and $previousEntry.ContainsKey('request_class')) {
        $previousRequestClass = [string]$previousEntry.request_class
    }
    $requestClass = Resolve-RequestClass -ExplicitClass $requestClass -TaskSummary $promptSummary -ToolName $toolName -PreviousClass $previousRequestClass

    $toolsUsed = Convert-ToStringArray -Value (Get-FirstNestedValue -InputObject $payload -Paths @(@('toolsUsed'), @('tools_used')) -Default @())
    if ($toolsUsed.Count -eq 0 -and -not [string]::IsNullOrWhiteSpace($toolName)) {
        $toolsUsed += $toolName
    }

    $artifacts = Convert-ToWorkspacePathArray -Candidates @(
        Get-FirstNestedValue -InputObject $payload -Paths @(@('artifacts')) -Default @()
    ) -WorkspaceRoot $resolvedRoot

    if (-not (Test-MeaningfulString -Value $sourceWorkspace)) {
        $sourceWorkspace = $resolvedRoot
    }
    $sourceWorkspace = Convert-ToRelativeWorkspacePath -Path $sourceWorkspace -WorkspaceRoot $resolvedRoot
    if (-not (Test-MeaningfulString -Value $sourceWorkspace)) {
        $sourceWorkspace = '.'
    }
    if (-not (Test-MeaningfulString -Value $sourceRepo)) {
        $sourceRepo = Split-Path -Path $resolvedRoot -Leaf
    }

    $communication = Build-CommunicationMetadata -Payload $payload -PreviousEntry $previousEntry
    $collaboration = Build-CollaborationMetadata -Payload $payload
    $decisionRationale = [string](Get-FirstNestedValue -InputObject $payload -Paths @(@('decision_rationale')) -Default $null)
    $nextSteps = Convert-ToStringArray -Value (Get-FirstNestedValue -InputObject $payload -Paths @(@('next_steps')) -Default @())
    $verificationEvidence = [string](Get-FirstNestedValue -InputObject $payload -Paths @(@('verification_evidence')) -Default $null)

    if (-not (Should-RecordEvent -EventName $eventName -ToolName $toolName -PromptSummary $promptSummary -AgentName $agentName -PreviousEntry $previousEntry -UsedPreviousContext:$usedPreviousContext)) {
        return
    }

    $telemetry = Build-Telemetry -Payload $payload -PreviousEntry $previousEntry -Actions $actions -ToolsUsed $toolsUsed -FilesTouched $filesTouched -Validation $validation -Risks $risks -ToolName $toolName -UsedPreviousContext:$usedPreviousContext -EventName $eventName -Status $status -TimestampSource $timestampInfo.timestamp_source -ClockSkewMs ([double]$timestampInfo.clock_skew_ms)
    if ((Should-SkipEntry -PreviousEntry $previousEntry -AgentName $agentName -TaskSummary $promptSummary -ToolsUsed $toolsUsed -FilesTouched $filesTouched -Validation $validation -Telemetry $telemetry -UsedPreviousContext:$usedPreviousContext)) {
        return
    }

    $actions = [object[]]@($actions)
    $toolsUsed = [object[]]@($toolsUsed)
    $filesTouched = [object[]]@($filesTouched)
    $validation = [object[]]@($validation)
    $risks = [object[]]@($risks)
    $artifacts = [object[]]@($artifacts)
    $nextSteps = [object[]]@($nextSteps)
    $positiveFeedback = Build-PositiveFeedbackMetadata -Payload $payload -Communication $communication -Collaboration $collaboration -Validation $validation -Status $status

    $entry = [ordered]@{
        timestamp = $timestamp
        run_id = $runId
        conversation_id = $sessionId
        source_workspace = $sourceWorkspace
        source_repo = $sourceRepo
        event_name = $eventName
        agent = $agentName
        mode = $mode
        task_summary = $promptSummary
        request_class = $requestClass
        status = $status
        actions = $actions
        tools_used = $toolsUsed
        tool_name = $toolName
        files_touched = $filesTouched
        validation = $validation
        outcome_summary = $(if ($usedPreviousContext) { "Hook-enriched lifecycle log entry using recent conversation context" } else { "Hook-generated lifecycle log entry with payload-derived telemetry" })
        risks = $risks
        artifacts = $artifacts
        telemetry = $telemetry
    }
    if ($null -ne $communication) {
        $entry.communication = $communication
    }
    if ($null -ne $collaboration) {
        $entry.collaboration = $collaboration
    }
    if ($null -ne $positiveFeedback) {
        $entry.positive_feedback = $positiveFeedback
    }
    if (Test-MeaningfulString -Value $decisionRationale) {
        $entry.decision_rationale = $decisionRationale
    }
    if ($nextSteps.Count -gt 0) {
        $entry.next_steps = $nextSteps
    }
    if (Test-MeaningfulString -Value $verificationEvidence) {
        $entry.verification_evidence = $verificationEvidence
    }
    if (-not [string]::IsNullOrWhiteSpace($permissionDecision)) {
        $entry.policy_decision = $permissionDecision
    }

    $logFile = Join-Path -Path $logDirectory -ChildPath ($logDate.ToString("yyyy-MM-dd") + ".ndjson")
    $line = ($entry | ConvertTo-Json -Compress -Depth 16) + [Environment]::NewLine
    [System.IO.File]::AppendAllText($logFile, $line, [System.Text.UTF8Encoding]::new($false))
}
catch {
    Write-Error "Failed to write agent operation log: $($_.Exception.Message)"
    exit 2
}