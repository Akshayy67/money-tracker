package com.aimoneytracker.ui.components

import androidx.compose.ui.graphics.Color
import com.aimoneytracker.domain.categorize.CategoryCatalog

/** Maps a category key to its display name and color for the UI. */
object CategoryVisuals {
    fun name(key: String): String = CategoryCatalog.displayNameOf(key)

    fun color(key: String): Color = runCatching {
        Color(android.graphics.Color.parseColor(CategoryCatalog.colorHexOf(key)))
    }.getOrDefault(Color(0xFF78909C))
}
