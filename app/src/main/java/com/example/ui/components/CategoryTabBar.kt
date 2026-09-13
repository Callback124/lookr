package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaCategory
import com.example.data.remote.model.HomeTagItem

@Composable
fun CategoryTabBar(
    selectedCategory: MediaCategory,
    onCategorySelected: (MediaCategory) -> Unit,
    homeTags: List<HomeTagItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val activeColor = Color(0xFF10B981) // Emerald Green

    // Build the ordered list of categories strictly containing Movies, Series, Anime, Short TV
    val categories = remember(homeTags) {
        if (homeTags.isNotEmpty()) {
            val validCategories = homeTags
                .sortedBy { it.order }
                .mapNotNull { tag -> MediaCategory.fromTagName(tag.tagName) }
                .distinct()
            if (validCategories.isNotEmpty()) validCategories else MediaCategory.values().toList()
        } else {
            MediaCategory.values().toList()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { category ->
            val isSelected = category == selectedCategory
            val textColor = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)

            val matchingTag = homeTags.firstOrNull {
                it.tagName.equals(category.rawTagName, ignoreCase = true)
            }
            val titleText = matchingTag?.tagDisplayName ?: category.title

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .testTag("category_tab_${category.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = titleText,
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Emerald green indicator bar
                    if (isSelected) {
                        Surface(
                            modifier = Modifier
                                .width(24.dp)
                                .height(3.5.dp),
                            shape = RoundedCornerShape(2.dp),
                            color = activeColor
                        ) {}
                    } else {
                        Spacer(modifier = Modifier.height(3.5.dp))
                    }
                }
            }
        }
    }
}

