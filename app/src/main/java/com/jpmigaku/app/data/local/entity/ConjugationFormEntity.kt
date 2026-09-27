package com.jpmigaku.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "conjugation_forms",
    foreignKeys = [
        ForeignKey(
            entity = ConjugationPatternEntity::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["patternId", "formKey"], unique = true),
        Index(value = ["patternId"])
    ]
)
data class ConjugationFormEntity(
    @androidx.room.PrimaryKey val id: String,
    val patternId: String,
    val formKey: String,
    val displayName: String,
    val sourceFormKey: String = "DICTIONARY",
    val compositionJson: String = "{}",
    val whenTo: String = "",
    val howTo: String = ""
)
