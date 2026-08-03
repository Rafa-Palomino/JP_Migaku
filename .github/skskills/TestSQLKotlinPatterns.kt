// Test SQL and Kotlin patterns combining best practices from SKILL.md files  
package skills.testpatterns

import java.sql.*; // Java DB for SQL demonstration purposes (not yet in project)


/**
 * Demonstrates combined application of:
 * - SQL column-select pattern (no SELECT *) per skill guidelines 
 * - Idiomatic Kotlin data classes, Result types and coroutines from SKILL.md  
 */
data class User( val id: Long? = null var name: String?, var email: String?, val isActive: Boolean ) : Comparable<User> {
    override fun compareTo(other: User) = if (this.id == other?.id) 
        this.name!!.compareTo(other.name!!) else 0.toLong()
}


/**
 * SQL pattern demonstrating best practice selection by column name only, not SELECT *:
 */  
object RepositoryPattern : Comparable<RepositoryPattern> {
    // Idempotent: select columns explicitly - never use wildcard to avoid extra data or null handling issues per skill guidelines
