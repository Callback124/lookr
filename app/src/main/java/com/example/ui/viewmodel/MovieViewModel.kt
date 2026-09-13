package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.MediaCategory
import com.example.data.model.MediaItem
import com.example.data.remote.model.HomeTagItem
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MovieViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _homeTags = MutableStateFlow<List<HomeTagItem>>(emptyList())
    val homeTags: StateFlow<List<HomeTagItem>> = _homeTags.asStateFlow()

    private val _isLoadingHomeLayout = MutableStateFlow(false)
    val isLoadingHomeLayout: StateFlow<Boolean> = _isLoadingHomeLayout.asStateFlow()

    private val _isLoadingMedia = MutableStateFlow(false)
    val isLoadingMedia: StateFlow<Boolean> = _isLoadingMedia.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var currentPage = 1

    private val _selectedCategory = MutableStateFlow(MediaCategory.MOVIES)
    val selectedCategory: StateFlow<MediaCategory> = _selectedCategory.asStateFlow()

    private val _subCategories = MutableStateFlow<List<String>>(repository.getSubCategories(MediaCategory.MOVIES))
    val subCategories: StateFlow<List<String>> = _subCategories.asStateFlow()

    private val _selectedSubCategory = MutableStateFlow("Popular Movies")
    val selectedSubCategory: StateFlow<String> = _selectedSubCategory.asStateFlow()

    private val _mediaList = MutableStateFlow<List<MediaItem>>(
        repository.getFallbackMedia(MediaCategory.MOVIES, "Popular Movies")
    )
    val mediaList: StateFlow<List<MediaItem>> = _mediaList.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedItemForDetail = MutableStateFlow<MediaItem?>(null)
    val selectedItemForDetail: StateFlow<MediaItem?> = _selectedItemForDetail.asStateFlow()

    private val _activePlayingItem = MutableStateFlow<MediaItem?>(null)
    val activePlayingItem: StateFlow<MediaItem?> = _activePlayingItem.asStateFlow()

    val watchlist: StateFlow<List<MediaItem>> = repository.getWatchlistFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadHomeLayoutData()
    }

    fun loadHomeLayoutData() {
        viewModelScope.launch {
            _isLoadingHomeLayout.value = true
            val tags = repository.getHomeLayoutTags()
            _homeTags.value = tags
            _isLoadingHomeLayout.value = false

            val currentSubs = repository.getSubCategories(_selectedCategory.value)
            _subCategories.value = currentSubs
            if (_selectedSubCategory.value.isBlank() || _selectedSubCategory.value !in currentSubs) {
                _selectedSubCategory.value = currentSubs.firstOrNull() ?: ""
            }
            loadMediaForSelection()
        }
    }

    fun loadMediaForSelection() {
        viewModelScope.launch {
            _isLoadingMedia.value = true
            currentPage = 1
            _hasMore.value = true
            val firstPage = repository.getMediaPage(
                category = _selectedCategory.value,
                subCategory = _selectedSubCategory.value,
                page = 1,
                pageSize = 10
            )
            _mediaList.value = firstPage.items
            _hasMore.value = firstPage.hasMore
            _isLoadingMedia.value = false

            // Pre-fetch page 2 so user has ample items to scroll immediately
            if (firstPage.hasMore && firstPage.items.isNotEmpty()) {
                val secondPage = repository.getMediaPage(
                    category = _selectedCategory.value,
                    subCategory = _selectedSubCategory.value,
                    page = 2,
                    pageSize = 10
                )
                if (secondPage.items.isNotEmpty()) {
                    val currentIds = _mediaList.value.map { it.id }.toSet()
                    val newUnique = secondPage.items.filter { it.id !in currentIds }
                    _mediaList.value = _mediaList.value + newUnique
                    currentPage = 2
                    _hasMore.value = secondPage.hasMore
                }
            }
        }
    }

    fun loadNextPage() {
        if (_isLoadingMedia.value || _isLoadingMore.value || !_hasMore.value || _searchQuery.value.isNotBlank()) {
            return
        }
        viewModelScope.launch {
            _isLoadingMore.value = true
            val nextPage = currentPage + 1
            val pageResult = repository.getMediaPage(
                category = _selectedCategory.value,
                subCategory = _selectedSubCategory.value,
                page = nextPage,
                pageSize = 10
            )
            if (pageResult.items.isNotEmpty()) {
                val currentIds = _mediaList.value.map { it.id }.toSet()
                val newUnique = pageResult.items.filter { it.id !in currentIds }
                if (newUnique.isNotEmpty()) {
                    _mediaList.value = _mediaList.value + newUnique
                    currentPage = nextPage
                }
                _hasMore.value = pageResult.hasMore
            } else {
                _hasMore.value = false
            }
            _isLoadingMore.value = false
        }
    }

    val filteredMedia: StateFlow<List<MediaItem>> = combine(
        _mediaList,
        _searchQuery
    ) { items, query ->
        if (query.isBlank()) {
            items
        } else {
            val cleanQuery = query.trim().lowercase()
            val pool = if (items.isNotEmpty()) items else repository.getMediaItems()
            pool.filter { item ->
                item.title.lowercase().contains(cleanQuery) ||
                        item.genres.any { it.lowercase().contains(cleanQuery) } ||
                        item.country.lowercase().contains(cleanQuery) ||
                        item.subCategory.lowercase().contains(cleanQuery) ||
                        item.synopsis.lowercase().contains(cleanQuery) ||
                        item.cast.any { it.name.lowercase().contains(cleanQuery) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _mediaList.value)

    fun selectCategory(category: MediaCategory) {
        _selectedCategory.value = category
        val subCats = repository.getSubCategories(category)
        _subCategories.value = subCats
        _selectedSubCategory.value = subCats.firstOrNull() ?: ""
        loadMediaForSelection()
    }

    fun selectSubCategory(subCat: String) {
        _selectedSubCategory.value = subCat
        loadMediaForSelection()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun openDetail(item: MediaItem) {
        _selectedItemForDetail.value = item
    }

    fun closeDetail() {
        _selectedItemForDetail.value = null
    }

    fun playMedia(item: MediaItem) {
        _activePlayingItem.value = item
    }

    fun closePlayer() {
        _activePlayingItem.value = null
    }

    fun toggleWatchlist(item: MediaItem) {
        viewModelScope.launch {
            val isCurrentlySaved = watchlist.value.any { it.id == item.id }
            repository.toggleWatchlist(item, isCurrentlySaved)
        }
    }

    fun isItemInWatchlist(itemId: String): Boolean {
        return watchlist.value.any { it.id == itemId }
    }

    fun getSubCategories(): List<String> {
        return repository.getSubCategories(_selectedCategory.value)
    }

    class Factory(private val repository: MediaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MovieViewModel(repository) as T
        }
    }
}

