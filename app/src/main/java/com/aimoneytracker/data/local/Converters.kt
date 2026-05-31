package com.aimoneytracker.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room type converters. Enums are stored as their [name] strings (stable across reorderings as long
 * as names are not renamed); lists are stored as JSON.
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? =
        value?.let { json.encodeToString(ListSerializer(String.serializer()), it) }

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value?.let { json.decodeFromString(ListSerializer(String.serializer()), it) } ?: emptyList()

    @TypeConverter
    fun fromLongList(value: List<Long>?): String? =
        value?.let { json.encodeToString(ListSerializer(Long.serializer()), it) }

    @TypeConverter
    fun toLongList(value: String?): List<Long> =
        value?.let { json.decodeFromString(ListSerializer(Long.serializer()), it) } ?: emptyList()
}
