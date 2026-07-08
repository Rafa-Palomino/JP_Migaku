---
name: kotlin-best-practices
description: Best practices for modern Kotlin development. Use this skill whenever implementing or reviewing Kotlin code.
---

# Purpose

Produce idiomatic, maintainable and efficient Kotlin code.

Prioritize:

1. Readability
2. Simplicity
3. Correctness
4. Performance
5. Maintainability

Avoid unnecessary abstractions.

---

# Language Version
Assume modern Kotlin (2.x unless otherwise specified).
Prefer stable language features.
Avoid experimental APIs unless explicitly requested.

---

# General Principles
Write Kotlin as Kotlin.
Do not write Java-style code translated to Kotlin.
Prefer expressive language features instead of verbose implementations.

---

# Naming
Use descriptive names.
Classes:
- PascalCase
Functions:
- camelCase
Variables:
- camelCase
Constants:
- UPPER_SNAKE_CASE
Boolean variables should read naturally.
Good:
isReady
hasPermission
canRetry

Avoid generic names like:
data
item
value
object
manager
helper

unless they are genuinely descriptive.

---

# Immutability

Prefer immutable objects.
Use:
val
instead of:
var

Use mutable state only when necessary.

---

# Null Safety
Use Kotlin null safety correctly.
Prefer:
?.
?:
requireNotNull()
checkNotNull()
Avoid:
!!
unless absolutely unavoidable.

---

# Data Classes
Use data classes whenever representing data.
Avoid writing manual:
equals()
hashCode()
toString()
unless customization is required.

---

# Functions
Keep functions:
small
focused
single responsibility
Prefer expression bodies when appropriate.
Avoid long functions.
Split complex logic into smaller private functions.

---

# Extension Functions
Prefer extension functions when they improve readability.
Do not create meaningless extensions.
Extensions should represent natural behavior.

---

# Collections
Prefer Kotlin collection APIs.
Use:
map
filter
associate
groupBy
fold
firstOrNull
any
none
all
instead of manual loops whenever readability improves.

Avoid unnecessary intermediate collections.

Use sequences when processing large datasets.

---

# Control Flow
Prefer:
when
over long if-else chains.
Use early returns.
Avoid deeply nested blocks.

---

# Exceptions
Use exceptions only for exceptional situations.
Do not use exceptions for normal control flow.
Validate input with:
require()
check()
error()

when appropriate.

---

# Sealed Classes
Use sealed classes or sealed interfaces for finite state models.
Typical uses:
UI states
Results
Commands
Events
Avoid enums when associated data is required.

---

# Result Types
Prefer explicit success/failure models.
Example approaches:
Result<T>
sealed class Result
Avoid returning null to indicate failure.

---

# Coroutines
When asynchronous code is required:
Prefer Kotlin Coroutines.
Avoid blocking calls.
Never block the main thread.
Respect structured concurrency.
Avoid GlobalScope.

---

# Concurrency
Protect shared mutable state.
Prefer immutable objects.
Avoid unnecessary synchronization.

---

# Generics
Use generics where appropriate.
Avoid unsafe casts.
Prefer explicit type constraints.

---

# Smart Casts
Leverage Kotlin smart casts.
Avoid unnecessary explicit casting.

---

# Scope Functions
Use scope functions appropriately.
let
run
apply
also
with

Do not chain multiple scope functions if readability suffers.

Choose the one matching the intended purpose.

---

# Properties
Prefer properties instead of trivial getter/setter methods.
Avoid exposing mutable collections.
Return immutable views whenever possible.

---

# Visibility
Use the smallest possible visibility.
Prefer:
private
internal
before:
public
Avoid unnecessary public APIs.

---

# Interfaces
Introduce interfaces only when they provide value.
Do not create interfaces solely for future possibilities.

---

# Dependency Injection
Do not assume any DI framework.
Write code that can be instantiated manually unless the project specifies otherwise.

---

# Performance
Avoid premature optimization.
Optimize only when necessary.
Avoid unnecessary object allocations inside loops.
Prefer lazy evaluation when appropriate.

---

# Documentation
Document:
public APIs
complex algorithms
non-obvious decisions
Avoid documenting obvious code.
Good code should be self-explanatory.

---

# Testing
Design code to be testable.
Prefer dependency injection through constructors.
Avoid hidden global state.
Functions should be deterministic whenever possible.

---

# Style
Prefer Kotlin idioms.
Avoid Java patterns such as:
Factory classes without reason
Utility classes full of static methods
Mutable DTOs
Getter/setter boilerplate
Anonymous inner classes

---

# Code Review Checklist
Before considering implementation complete, verify:
✓ Idiomatic Kotlin
✓ Small focused functions
✓ Minimal mutable state
✓ Null safety respected
✓ No duplicated code
✓ Proper visibility
✓ Good naming
✓ Testable design
✓ Readable implementation
✓ No unnecessary abstraction

---

# Golden Rule
When multiple implementations are valid:
Choose the simplest solution that remains clear, maintainable and idiomatic Kotlin.