package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaCategory
import com.example.ui.components.CategoryTabBar
import com.example.ui.components.FilterChipRow
import com.example.ui.components.MediaCard
import com.example.ui.components.MediaDetailDialog
import com.example.ui.components.SearchBarView
import com.example.ui.components.VideoPlayerSheet
import com.example.ui.viewmodel.MovieViewModel

@Composable
fun MovieAppScreen(
    viewModel: MovieViewModel,
    modifier: Modifier = Modifier
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSubCategory by viewModel.selectedSubCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val mediaList by viewModel.filteredMedia.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val selectedItemForDetail by viewModel.selectedItemForDetail.collectAsState()
    val activePlayingItem by viewModel.activePlayingItem.collectAsState()
    val homeTags by viewModel.homeTags.collectAsState()
    val isLoadingHomeLayout by viewModel.isLoadingHomeLayout.collectAsState()
    val isLoadingMedia by viewModel.isLoadingMedia.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    val subCategories by viewModel.subCategories.collectAsState()

    val gridState = rememberLazyGridState()

    // Reset scroll when category or subcategory changes
    LaunchedEffect(selectedCategory, selectedSubCategory) {
        gridState.scrollToItem(0)
    }

    // Trigger next page fetch automatically when approaching bottom of the list
    LaunchedEffect(gridState, mediaList.size, hasMore, isLoadingMore) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to total
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 4 && hasMore && !isLoadingMore && searchQuery.isBlank()) {
                viewModel.loadNextPage()
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search or Ask Anything Bar
            SearchBarView(
                query = searchQuery,
                onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                onClear = { viewModel.clearSearch() }
            )

            // Dynamic Category Tabs from API Home Layout Data (Movies, Series, Anime, Short TV)
            CategoryTabBar(
                selectedCategory = selectedCategory,
                onCategorySelected = { viewModel.selectCategory(it) },
                homeTags = homeTags
            )

            // Sub-category / Tag Filter Row (when not in search mode)
            if (searchQuery.isBlank() && subCategories.isNotEmpty()) {
                FilterChipRow(
                    chips = subCategories,
                    selectedChip = selectedSubCategory,
                    onChipSelected = { viewModel.selectSubCategory(it) }
                )
            }

            // Subtle loading bar when fetching new subtag media
            if (isLoadingMedia) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color(0xFF10B981),
                    trackColor = Color(0xFF1E293B)
                )
            }

            // Results count or search banner when searching
            if (searchQuery.isNotBlank()) {
                Text(
                    text = "Showing ${mediaList.size} results for \"$searchQuery\"",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Media Grid (2 Columns matching the screenshot)
            if (mediaList.isEmpty()) {
                // Empty state or Loading
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isLoadingMedia) {
                            CircularProgressIndicator(
                                color = Color(0xFF10B981),
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Fetching recommendations...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Icon(
                                imageVector = if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.MovieFilter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (searchQuery.isNotBlank()) "No titles found"
                                else "No content in this category",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (searchQuery.isNotBlank()) "Try checking the spelling or searching for another title, character, or genre."
                                else "Explore other categories above.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (searchQuery.isNotBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.clearSearch() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Clear Search")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("media_grid")
                ) {
                    items(
                        items = mediaList,
                        key = { it.id }
                    ) { item ->
                        val isWatchlisted = watchlist.any { it.id == item.id }

                        MediaCard(
                            item = item,
                            isWatchlisted = isWatchlisted,
                            onItemClick = { viewModel.openDetail(it) },
                            onToggleWatchlist = { viewModel.toggleWatchlist(it) }
                        )
                    }

                    if (isLoadingMore) {
                        item(span = { GridItemSpan(2) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Detail Dialog
        selectedItemForDetail?.let { item ->
            val isWatchlisted = watchlist.any { it.id == item.id }

            MediaDetailDialog(
                item = item,
                isWatchlisted = isWatchlisted,
                onDismiss = { viewModel.closeDetail() },
                onPlay = {
                    viewModel.closeDetail()
                    viewModel.playMedia(it)
                },
                onToggleWatchlist = { viewModel.toggleWatchlist(it) }
            )
        }

        // Video Player Sheet
        activePlayingItem?.let { item ->
            VideoPlayerSheet(
                item = item,
                onClose = { viewModel.closePlayer() }
            )
        }
    }
}
