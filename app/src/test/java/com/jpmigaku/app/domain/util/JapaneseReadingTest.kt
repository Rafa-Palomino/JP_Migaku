package com.jpmigaku.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class JapaneseReadingTest {
    @Test
    fun convertsKanaToRomaji() {
        assertEquals("gakkou", "がっこう".toRomaji())
        assertEquals("konnichiha", "こんにちは".toRomaji())
        assertEquals("supa", "スーパー".toRomaji().replace("ー", ""))
    }
}
