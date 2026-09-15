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
}
