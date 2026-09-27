package com.jpmigaku.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations for the personal study database.
 *
 * These migrations are intentionally additive: existing cards, decks,
 * relationships, and review statistics are preserved.
 */
object DatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE decks ADD COLUMN kind TEXT NOT NULL DEFAULT 'VOCABULARY'"
            )
            db.execSQL(
                """
                UPDATE decks
                SET kind = 'KANJI'
                WHERE id IN (
                    SELECT DISTINCT card_decks.deckId
                    FROM card_decks
                    INNER JOIN study_cards ON study_cards.id = card_decks.cardId
                    WHERE study_cards.kind = 'KANJI'
                )
                """.trimIndent()
            )
            db.execSQL(
                "ALTER TABLE study_cards ADD COLUMN jlptLevel TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conjugation_patterns (
                    id TEXT NOT NULL,
                    wordClass TEXT NOT NULL,
                    conjugationType TEXT NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                index_conjugation_patterns_wordClass_conjugationType
                ON conjugation_patterns(wordClass, conjugationType)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conjugation_forms (
                    id TEXT NOT NULL,
                    patternId TEXT NOT NULL,
                    formKey TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    sourceFormKey TEXT NOT NULL,
                    compositionJson TEXT NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(patternId) REFERENCES conjugation_patterns(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                index_conjugation_forms_patternId_formKey
                ON conjugation_forms(patternId, formKey)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_conjugation_forms_patternId
                ON conjugation_forms(patternId)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conjugation_review_stats (
                    conjugationFormId TEXT NOT NULL,
                    attempts INTEGER NOT NULL,
                    correctCount INTEGER NOT NULL,
                    incorrectCount INTEGER NOT NULL,
                    currentStreak INTEGER NOT NULL,
                    bestStreak INTEGER NOT NULL,
                    lastCorrectAt INTEGER,
                    lastIncorrectAt INTEGER,
                    lastReviewedAt INTEGER,
                    intervalDays INTEGER NOT NULL,
                    easeFactor REAL NOT NULL,
                    repetitions INTEGER NOT NULL,
                    dueAt INTEGER,
                    PRIMARY KEY(conjugationFormId),
                    FOREIGN KEY(conjugationFormId) REFERENCES conjugation_forms(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE conjugation_forms ADD COLUMN whenTo TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE conjugation_forms ADD COLUMN howTo TEXT NOT NULL DEFAULT ''")
        }
    }
}
