[CmdletBinding()]
param(
    [Parameter()]
    [string]$WorkspaceRoot = '.',

    [Parameter()]
    [string]$LogsPath,

    [Parameter()]
    [switch]$AsJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Split-JsonObjectStream {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Content,

        [Parameter(Mandatory = $true)]
        [string]$FilePath
    )

    $documents = New-Object System.Collections.Generic.List[string]
    $depth = 0
    $startIndex = -1
    $inString = $false
    $isEscaped = $false

    for ($index = 0; $index -lt $Content.Length; $index++) {
        $character = $Content[$index]

        if ($inString) {
            if ($isEscaped) {
                $isEscaped = $false
                continue
            }

            if ($character -eq '\\') {
                $isEscaped = $true
                continue
            }

            if ($character -eq '"') {
                $inString = $false
            }

            continue
        }

        switch ($character) {
            '"' {
                if ($depth -gt 0) {
                    $inString = $true
                }
            }
            '{' {
                if ($depth -eq 0) {
                    $startIndex = $index
                }
                $depth++
            }
            '}' {
                if ($depth -eq 0) {
                    continue
                }

                $depth--
                if ($depth -eq 0 -and $startIndex -ge 0) {
                    $documents.Add($Content.Substring($startIndex, ($index - $startIndex + 1)))
                    $startIndex = -1
                }
            }
        }
    }

    if ($depth -ne 0) {
        throw "Unbalanced JSON object stream in $FilePath"
    }

    return ,($documents.ToArray())
}

function Get-EntryDocuments {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath
    )

    $content = Get-Content -LiteralPath $FilePath -Raw -ErrorAction Stop
    if ([string]::IsNullOrWhiteSpace($content)) {
        return ,@()
    }

    return Split-JsonObjectStream -Content $content -FilePath $FilePath
}

function Get-SchemaMetadata {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SchemaPath
    )

    $schema = Get-Content -LiteralPath $SchemaPath -Raw -ErrorAction Stop | ConvertFrom-Json -AsHashtable -Depth 64
    return [pscustomobject]@{
        AllowedTopLevelProperties = @($schema.properties.Keys)
        RequiredTopLevelProperties = @($schema.required)
    }
}

function Get-TimestampDateString {
    param(
        [Parameter()]
        [AllowNull()]
        [object]$Timestamp
    )

    if ($null -eq $Timestamp) {
        return $null
    }

    $text = [string]$Timestamp
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }

    try {
        $parsed = [datetimeoffset]::Parse(
            $text,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::RoundtripKind
        )
        return $parsed.ToUniversalTime().ToString('yyyy-MM-dd')
    }
    catch {
        return $null
    }
}

function Add-Finding {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [object]$Findings,

        [Parameter(Mandatory = $true)]
        [string]$Severity,

        [Parameter(Mandatory = $true)]
        [string]$Code,

        [Parameter(Mandatory = $true)]
        [string]$File,

        [Parameter()]
        [int]$EntryIndex = -1,

        [Parameter(Mandatory = $true)]
        [string]$Message,

        [Parameter()]
        [AllowNull()]
        [string]$Timestamp = $null
    )

    $finding = [ordered]@{
        severity = $Severity
        code = $Code
        file = $File
        message = $Message
    }
    if ($EntryIndex -ge 0) {
        $finding.entry_index = $EntryIndex
    }
    if (-not [string]::IsNullOrWhiteSpace($Timestamp)) {
        $finding.timestamp = $Timestamp
    }

    $null = $Findings.Add([pscustomobject]$finding)
}

function Get-InferredPositiveSignals {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Entry
    )

    $signals = New-Object System.Collections.Generic.List[string]

    if ($Entry.ContainsKey('communication')) {
        $consistency = [string]$Entry.communication.consistency
        if ($consistency -eq 'matched') {
            $signals.Add('language-matched')
        }
    }

    if ($Entry.ContainsKey('collaboration')) {
        $userImproved = $Entry.collaboration.user_solution_improved_agent_proposal
        if ($userImproved -eq $true) {
            $signals.Add('user-improved-agent-proposal')
        }
    }

    if ($Entry.ContainsKey('validation')) {
        $validationItems = @($Entry.validation)
        if ($validationItems.Count -gt 0) {
            $failedCount = @($validationItems | Where-Object {
                ([string](Get-OptionalObjectPropertyValue -InputObject $_ -PropertyName 'result')) -in @('failed', 'inconclusive')
            }).Count
            $passedCount = @($validationItems | Where-Object {
                ([string](Get-OptionalObjectPropertyValue -InputObject $_ -PropertyName 'result')) -eq 'passed'
            }).Count
            if ($passedCount -gt 0 -and $failedCount -eq 0) {
                $signals.Add('validation-passed')
            }
        }
    }

    return ,($signals.ToArray())
}

function Get-OptionalEntryText {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Entry,

        [Parameter(Mandatory = $true)]
        [string]$PropertyName
    )

    if (-not $Entry.ContainsKey($PropertyName)) {
        return ''
    }

    $value = $Entry[$PropertyName]
    if ($null -eq $value) {
        return ''
    }

    return [string]$value
}

function Get-OptionalObjectPropertyValue {
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

$resolvedRoot = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
if ([string]::IsNullOrWhiteSpace($LogsPath)) {
    $resolvedLogsPath = Join-Path -Path $resolvedRoot -ChildPath '.artifacts/agent-logs'
}
else {
    $resolvedLogsPath = (Resolve-Path -LiteralPath $LogsPath).Path
}

$schemaMetadata = Get-SchemaMetadata -SchemaPath (Join-Path -Path $resolvedRoot -ChildPath '.github/contracts/agent-operation-log.schema.json')
$allowedTopLevelProperties = $schemaMetadata.AllowedTopLevelProperties
$requiredTopLevelProperties = $schemaMetadata.RequiredTopLevelProperties

$files = @(Get-ChildItem -LiteralPath $resolvedLogsPath -Filter '*.ndjson' -File | Sort-Object Name)
$findings = New-Object System.Collections.Generic.List[object]

$stats = [ordered]@{
    files_scanned = $files.Count
    entries_parsed = 0
    parse_errors = 0
    filename_timestamp_mismatches = 0
    schema_warnings = 0
    language_warnings = 0
    collaboration_warnings = 0
    positive_feedback_missing = 0
    positive_feedback_logged = 0
}

foreach ($file in $files) {
    try {
        $documents = Get-EntryDocuments -FilePath $file.FullName
    }
    catch {
        $stats.parse_errors++
        Add-Finding -Findings $findings -Severity 'error' -Code 'parse-error' -File $file.Name -Message $_.Exception.Message
        continue
    }

    $fileDate = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    for ($entryIndex = 0; $entryIndex -lt $documents.Count; $entryIndex++) {
        $document = $documents[$entryIndex]
        $entry = $null
        try {
            $entry = $document | ConvertFrom-Json -AsHashtable -Depth 64
        }
        catch {
            $stats.parse_errors++
            Add-Finding -Findings $findings -Severity 'error' -Code 'entry-parse-error' -File $file.Name -EntryIndex ($entryIndex + 1) -Message $_.Exception.Message
            continue
        }

        $stats.entries_parsed++
        $timestamp = [string]$entry.timestamp
        $timestampDate = Get-TimestampDateString -Timestamp $timestamp
        if (-not [string]::IsNullOrWhiteSpace($timestampDate) -and $timestampDate -ne $fileDate) {
            $stats.filename_timestamp_mismatches++
            Add-Finding -Findings $findings -Severity 'warning' -Code 'filename-timestamp-mismatch' -File $file.Name -EntryIndex ($entryIndex + 1) -Timestamp $timestamp -Message "Entry timestamp resolves to $timestampDate but file name is $fileDate."
        }

        $unknownFields = @($entry.Keys | Where-Object { $_ -notin $allowedTopLevelProperties })
        if ($unknownFields.Count -gt 0) {
            $stats.schema_warnings++
            Add-Finding -Findings $findings -Severity 'warning' -Code 'unknown-top-level-fields' -File $file.Name -EntryIndex ($entryIndex + 1) -Timestamp $timestamp -Message ("Unknown top-level fields: " + ($unknownFields -join ', '))
        }

        $missingRequired = @($requiredTopLevelProperties | Where-Object { $_ -notin $entry.Keys })
        if ($missingRequired.Count -gt 0) {
            $stats.schema_warnings++
            Add-Finding -Findings $findings -Severity 'warning' -Code 'missing-required-fields' -File $file.Name -EntryIndex ($entryIndex + 1) -Timestamp $timestamp -Message ("Missing required fields: " + ($missingRequired -join ', '))
        }

        if ($entry.ContainsKey('communication')) {
            $consistency = [string]$entry.communication.consistency
            if ($consistency -in @('mixed', 'shifted')) {
                $stats.language_warnings++
                Add-Finding -Findings $findings -Severity 'warning' -Code 'language-consistency' -File $file.Name -EntryIndex ($entryIndex + 1) -Timestamp $timestamp -Message "communication.consistency is $consistency"
            }
        }

        $collaborationSignal = $false
        if ($entry.ContainsKey('collaboration')) {
            if ($entry.collaboration.user_solution_adopted -eq $true -or $entry.collaboration.user_solution_improved_agent_proposal -eq $true) {
                $collaborationSignal = $true
            }
        }
        else {
            $searchText = @(
                Get-OptionalEntryText -Entry $entry -PropertyName 'task_summary'
                Get-OptionalEntryText -Entry $entry -PropertyName 'decision_rationale'
                Get-OptionalEntryText -Entry $entry -PropertyName 'outcome_summary'
            ) -join ' '
            if ($searchText -match '(?i)(user proposal|user solution|propuesta del usuario|solucion del usuario|adopted.*user)') {
                $stats.collaboration_warnings++
                Add-Finding -Findings $findings -Severity 'warning' -Code 'collaboration-metadata-missing' -File $file.Name -EntryIndex ($entryIndex + 1) -Timestamp $timestamp -Message 'Text suggests a user-originated solution decision, but collaboration metadata is missing.'
            }
        }

        $positiveSignals = Get-InferredPositiveSignals -Entry $entry
        if ($entry.ContainsKey('positive_feedback')) {
            $stats.positive_feedback_logged++
        }
        elseif ($positiveSignals.Count -gt 0) {
            $stats.positive_feedback_missing++
            Add-Finding -Findings $findings -Severity 'warning' -Code 'positive-feedback-missing' -File $file.Name -EntryIndex ($entryIndex + 1) -Timestamp $timestamp -Message ("Positive signals detected without positive_feedback metadata: " + ($positiveSignals -join ', '))
        }
    }
}

$report = [ordered]@{
    workspace_root = $resolvedRoot
    logs_path = $resolvedLogsPath
    stats = $stats
    findings = @($findings.ToArray())
}

if ($AsJson) {
    $report | ConvertTo-Json -Depth 8
}
else {
    [pscustomobject]$report
}