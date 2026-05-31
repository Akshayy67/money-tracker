package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A category definition. The app ships with a default catalog (see
 * [com.aimoneytracker.domain.categorize.CategoryCatalog]) which is seeded into this table; users
 * can add custom categories.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val key: String,             // stable key, e.g. "food"
    val displayName: String,
    val parentKey: String? = null,           // for subcategories
    val iconName: String? = null,            // material icon name
    val colorHex: String? = null,
    val isIncome: Boolean = false,
    val isTransfer: Boolean = false,
    val isSystem: Boolean = true,
    val sortOrder: Int = 0,
)
