package com.example

import com.example.data.local.WatchlistDao
import com.example.data.local.WatchlistEntity
import com.example.data.model.MediaCategory
import com.example.data.repository.MediaRepository
import com.example.ui.viewmodel.MovieViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FakeWatchlistDao : WatchlistDao {
    private val items = mutableListOf<WatchlistEntity>()
    override fun getAllWatchlist(): Flow<List<WatchlistEntity>> = flowOf(items)
    override fun isWatchlisted(id: String): Flow<Boolean> = flowOf(items.any { it.id == id })
    override suspend fun addToWatchlist(item: WatchlistEntity) { items.add(item) }
    override suspend fun removeFromWatchlist(id: String) { items.removeAll { it.id == id } }
}

class ExampleUnitTest {
    @Test
    fun testCategorySubTagsUpdateCorrectly() {
        val repo = MediaRepository(FakeWatchlistDao())
        val vm = MovieViewModel(repo)

        // Initially Movies
        assertEquals(MediaCategory.MOVIES, vm.selectedCategory.value)
        val movieSubs = vm.subCategories.value
        assertEquals(listOf("Popular Movies", "Hollywood Movies", "Bollywood Movies", "For you"), movieSubs)

        // Switch to Series
        vm.selectCategory(MediaCategory.SERIES)
        assertEquals(MediaCategory.SERIES, vm.selectedCategory.value)
        val seriesSubs = vm.subCategories.value
        assertEquals(listOf("Trending drama", "Indian Drama", "Western Drama", "Korean Drama", "For you"), seriesSubs)

        // Switch to Anime
        vm.selectCategory(MediaCategory.ANIME)
        assertEquals(MediaCategory.ANIME, vm.selectedCategory.value)
        val animeSubs = vm.subCategories.value
        assertEquals(listOf("Trending Anime", "Burning with Ardour", "Adventurers' Saga", "For you"), animeSubs)

        // Switch to Short TV - subTags and subTagDetails are empty in JSON
        vm.selectCategory(MediaCategory.SHORT_TV)
        assertEquals(MediaCategory.SHORT_TV, vm.selectedCategory.value)
        assertTrue(vm.subCategories.value.isEmpty())
    }

    @Test
    fun testExcludedTagsDoNotAppear() = runBlocking {
        val repo = MediaRepository(FakeWatchlistDao())
        val tags = repo.getHomeLayoutTags()
        val tagNames = tags.map { it.tagName.lowercase() }
        val tagDisplayNames = tags.map { it.tagDisplayName.lowercase() }

        // Must not contain BuzzBox or Fun & Memes
        assertFalse(tagNames.contains("buzzbox"))
        assertFalse(tagDisplayNames.any { it.contains("fun") || it.contains("meme") })

        // Must not contain Sports
        assertFalse(tagNames.contains("sports"))
        assertFalse(tagDisplayNames.any { it.contains("sport") })

        // Must contain Movies, Series, Anime, Short TV in order
        val expectedCategories = listOf("movies", "series", "anime", "short tv")
        assertEquals(expectedCategories, tagNames)
    }

    @Test
    fun testRecommendMediaFetchesItems() = runBlocking {
        val repo = MediaRepository(FakeWatchlistDao())
        val items = repo.getMediaForCategoryAndSubTag(
            category = MediaCategory.MOVIES,
            subCategory = "Hollywood Movies",
            page = 1,
            pageSize = 10
        )
        assertNotNull(items)
        assertTrue("Items should not be empty", items.isNotEmpty())
        assertTrue("Must have valid title", items.first().title.isNotBlank())
        assertTrue("Must have real live ID", items.first().id.isNotBlank())

        // Verify rating format is 0.0
        val ratingRegex = Regex("^\\d+\\.\\d$")
        for (item in items) {
            assertTrue("Rating ${item.formattedRating} should match 0.0 format", item.formattedRating.matches(ratingRegex))
        }
    }

    @Test
    fun testPaginationLoadsMultiplePages() = runBlocking {
        val repo = MediaRepository(FakeWatchlistDao())
        val page1 = repo.getMediaPage(
            category = MediaCategory.MOVIES,
            subCategory = "Hollywood Movies",
            page = 1,
            pageSize = 10
        )
        assertTrue(page1.items.isNotEmpty())
        assertTrue("Page 1 should indicate hasMore", page1.hasMore)

        val page2 = repo.getMediaPage(
            category = MediaCategory.MOVIES,
            subCategory = "Hollywood Movies",
            page = 2,
            pageSize = 10
        )
        assertTrue(page2.items.isNotEmpty())
        // Items between page 1 and page 2 should have distinct IDs
        val page1Ids = page1.items.map { it.id }.toSet()
        val page2Ids = page2.items.map { it.id }.toSet()
        val intersection = page1Ids.intersect(page2Ids)
        assertTrue("Pages should have unique items", intersection.isEmpty())
    }
}

