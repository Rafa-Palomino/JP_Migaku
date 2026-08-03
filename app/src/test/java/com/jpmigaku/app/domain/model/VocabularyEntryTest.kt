package com.jpmigaku.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VocabularyEntryTest {
    @Test
    fun defaultsToSimpleSrsState() {
        val entry = VocabularyEntry(
            id = "1",
            japanese = "飲む",
            reading = "のむ",
            meaningEs = "beber",
            createdAt = 0L
        )

        assertEquals(1, entry.interval)
        assertEquals(2.5, entry.easeFactor, 0.0)
    }
}
