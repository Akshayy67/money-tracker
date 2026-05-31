package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The learned merchant dictionary: maps a normalized merchant key or a UPI handle to a category.
 * Crucial for India, where many shops/autos use personal `@ok…` handles — once the user tags a
 * handle as "Business/Shop -> Groceries", every future payment to it auto-categorizes silently.
 */
@Entity(
    tableName = "merchant_map",
    indices = [Index(value = ["key"], unique = true)]
)
data class MerchantMapEntity(
    @PrimaryKey val key: String,         // normalized merchant or handle (lowercase)
    val displayName: String,
    val category: String,
    val subcategory: String? = null,
    val isBusiness: Boolean = true,
    val hitCount: Int = 1,
    val updatedAt: Long = 0,
)
