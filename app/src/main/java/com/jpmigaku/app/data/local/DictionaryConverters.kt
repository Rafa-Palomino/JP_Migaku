package com.jpmigaku.app.data.local

import androidx.room.TypeConverter

class DictionaryConverters {
    @TypeConverter
    fun fromList(values: List<String>): String = values.joinToString(LIST_SEPARATOR)

    @TypeConverter
    fun toList(value: String): List<String> =
        value.split(LIST_SEPARATOR).filter(String::isNotBlank)

    private companion object {
        const val LIST_SEPARATOR = "\u001F"
    }
}
