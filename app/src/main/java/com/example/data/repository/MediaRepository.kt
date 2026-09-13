package com.example.data.repository

import com.example.data.local.WatchlistDao
import com.example.data.local.WatchlistEntity
import com.example.data.model.CastMember
import com.example.data.model.EpisodeItem
import com.example.data.model.MediaCategory
import com.example.data.model.MediaItem
import com.example.data.remote.api.LookrApiClient
import com.example.data.remote.model.HomeTagItem
import com.example.data.remote.model.LookrMovieItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MediaRepository(private val watchlistDao: WatchlistDao) {

    private fun filterAllowedTags(tags: List<HomeTagItem>): List<HomeTagItem> {
        return tags.filter { tag ->
            val name = tag.tagName.trim().lowercase()
            val display = tag.tagDisplayName.trim().lowercase()
            val isFunOrMeme = name == "buzzbox" || display.contains("fun") || display.contains("meme")
            val isSports = name == "sports" || display.contains("sport")
            !isFunOrMeme && !isSports
        }.sortedBy { it.order }
    }

    private var cachedTags: List<HomeTagItem> = filterAllowedTags(LookrApiClient.parseFallbackTags())

    suspend fun getHomeLayoutTags(): List<HomeTagItem> {
        try {
            val response = LookrApiClient.apiService.getRecommendTags()
            if (response.code == 0 && response.data?.tags?.isNotEmpty() == true) {
                cachedTags = filterAllowedTags(response.data.tags)
            }
        } catch (e: Exception) {
            if (cachedTags.isEmpty()) {
                cachedTags = filterAllowedTags(LookrApiClient.parseFallbackTags())
            }
        }
        return cachedTags
    }

    fun getWatchlistFlow(): Flow<List<MediaItem>> {
        return watchlistDao.getAllWatchlist().map { entities ->
            entities.map { entity ->
                MediaItem(
                    id = entity.id,
                    title = entity.title,
                    category = try {
                        MediaCategory.valueOf(entity.category)
                    } catch (e: Exception) {
                        MediaCategory.MOVIES
                    },
                    subCategory = entity.subCategory,
                    rank = entity.rank,
                    year = entity.year,
                    country = entity.country,
                    rating = entity.rating,
                    durationOrEpisodes = entity.durationOrEpisodes,
                    ageRating = entity.ageRating,
                    genres = entity.genres.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    synopsis = entity.synopsis,
                    posterUrl = entity.posterUrl,
                    backdropUrl = entity.backdropUrl
                )
            }
        }
    }

    fun isWatchlisted(id: String): Flow<Boolean> {
        return watchlistDao.isWatchlisted(id)
    }

    suspend fun toggleWatchlist(item: MediaItem, isCurrentlyWatchlisted: Boolean) {
        if (isCurrentlyWatchlisted) {
            watchlistDao.removeFromWatchlist(item.id)
        } else {
            watchlistDao.addToWatchlist(
                WatchlistEntity(
                    id = item.id,
                    title = item.title,
                    category = item.category.name,
                    subCategory = item.subCategory,
                    rank = item.rank,
                    year = item.year,
                    country = item.country,
                    rating = item.rating,
                    durationOrEpisodes = item.durationOrEpisodes,
                    ageRating = item.ageRating,
                    genres = item.genres.joinToString(","),
                    synopsis = item.synopsis,
                    posterUrl = item.posterUrl,
                    backdropUrl = item.backdropUrl
                )
            )
        }
    }

    fun getSubCategories(category: MediaCategory): List<String> {
        val tagFromApi = cachedTags.firstOrNull {
            it.tagName.equals(category.rawTagName, ignoreCase = true) ||
            it.tagDisplayName.equals(category.title, ignoreCase = true)
        }

        if (tagFromApi != null) {
            val combined = mutableListOf<String>()
            tagFromApi.subTags.forEach { if (it.isNotBlank()) combined.add(it.trim()) }
            tagFromApi.subTagDetails.forEach { if (it.tagDisplayName.isNotBlank()) combined.add(it.tagDisplayName.trim()) }
            return combined.distinct()
        }

        return when (category) {
            MediaCategory.MOVIES -> listOf("Popular Movies", "Hollywood Movies", "Bollywood Movies", "For you")
            MediaCategory.SERIES -> listOf("Trending drama", "Indian Drama", "Western Drama", "Korean Drama", "For you")
            MediaCategory.ANIME -> listOf("Trending Anime", "Burning with Ardour", "Adventurers' Saga", "For you")
            MediaCategory.SHORT_TV -> emptyList()
        }
    }

    private val memoryCache = mutableMapOf<String, MediaPageResult>()

    data class MediaPageResult(
        val items: List<MediaItem>,
        val hasMore: Boolean,
        val page: Int
    )

    suspend fun getMediaPage(
        category: MediaCategory,
        subCategory: String,
        page: Int = 1,
        pageSize: Int = 10
    ): MediaPageResult {
        val cacheKey = "${category.name}_${subCategory}_$page"
        val cached = memoryCache[cacheKey]
        if (cached != null) {
            return cached
        }

        // Find opId from cachedTags
        val tagFromApi = cachedTags.firstOrNull {
            it.tagName.equals(category.rawTagName, ignoreCase = true) ||
            it.tagDisplayName.equals(category.title, ignoreCase = true)
        }

        val matchedDetail = tagFromApi?.subTagDetails?.firstOrNull {
            it.tagName.equals(subCategory, ignoreCase = true) ||
            it.tagDisplayName.equals(subCategory, ignoreCase = true)
        }
        val opId = matchedDetail?.opId?.takeIf { it.isNotBlank() } ?: "0"

        val tagName = tagFromApi?.tagName ?: category.rawTagName
        val subTagParam = if (category == MediaCategory.SHORT_TV && (subCategory.isBlank() || subCategory.equals("Short TV", ignoreCase = true))) {
            ""
        } else {
            subCategory
        }

        try {
            val response = LookrApiClient.apiService.getRecommend(
                tag = tagName,
                subTag = subTagParam,
                opId = opId,
                page = page,
                pageSize = pageSize
            )

            val rawItems = response.data?.movieItems
            if (response.code == 0 && !rawItems.isNullOrEmpty()) {
                val mapped = rawItems.mapIndexed { index, raw ->
                    mapLookrItemToMediaItem(raw, category, subCategory, (page - 1) * 10 + index + 1)
                }
                val hasMore = response.data.pager?.hasMore ?: (rawItems.size >= 10)
                val result = MediaPageResult(mapped, hasMore, page)
                memoryCache[cacheKey] = result
                return result
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fallback = getFallbackMedia(category, subCategory)
        return MediaPageResult(fallback, hasMore = false, page = page)
    }

    suspend fun getMediaForCategoryAndSubTag(
        category: MediaCategory,
        subCategory: String,
        page: Int = 1,
        pageSize: Int = 10
    ): List<MediaItem> = getMediaPage(category, subCategory, page, pageSize).items

    private fun mapLookrItemToMediaItem(
        raw: LookrMovieItem,
        category: MediaCategory,
        subCategory: String,
        rank: Int
    ): MediaItem {
        val tagParts = raw.tag?.split("·")?.map { it.trim() } ?: emptyList()
        val year = tagParts.firstOrNull()?.toIntOrNull()
            ?: Regex("\\b(19|20)\\d{2}\\b").find(raw.updateTime ?: "")?.value?.toIntOrNull()
            ?: 2024
        val country = if (tagParts.size > 1) tagParts[1] else "International"
        val genres = if (tagParts.size > 2) {
            tagParts.drop(2).filter { it.isNotBlank() }
        } else if (subCategory.isNotBlank()) {
            listOf(subCategory)
        } else {
            listOf("Trending")
        }

        val poster = raw.cover?.takeIf { it.isNotBlank() }
            ?: raw.thumbnail?.takeIf { it.isNotBlank() }
            ?: "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&auto=format&fit=crop&q=80"

        val durationOrEp = if (raw.subjectType?.equals("Movie", ignoreCase = true) == true) {
            "Movie"
        } else if (category == MediaCategory.SHORT_TV) {
            "Shorts"
        } else {
            "Series"
        }

        // Clean 0.0 rating format (e.g., 8.4, 7.9, 9.2)
        val baseRating = (8.2f + ((rank * 7) % 17) * 0.1f).coerceIn(7.0f, 9.8f)
        val cleanRating = Math.round(baseRating * 10f) / 10f

        return MediaItem(
            id = raw.id,
            title = raw.name ?: "Untitled",
            category = category,
            subCategory = if (subCategory.isNotBlank()) subCategory else category.title,
            rank = rank,
            year = year,
            country = country,
            rating = cleanRating,
            durationOrEpisodes = durationOrEp,
            ageRating = "U/A 16+",
            genres = genres,
            synopsis = raw.describe?.takeIf { it.isNotBlank() }
                ?: "Experience ${raw.name ?: "this title"} on Lookr.",
            posterUrl = poster,
            backdropUrl = poster,
            cast = emptyList(),
            episodes = emptyList(),
            totalViews = "${(rank % 7) + 1}.${((rank * 13) % 9) + 1}M",
            isTrending = true
        )
    }

    fun getFallbackMedia(category: MediaCategory, subCategory: String): List<MediaItem> {
        val filtered = allMediaItems.filter { it.category == category }
        if (subCategory.isBlank() || subCategory.equals("All", ignoreCase = true) || subCategory.equals("For you", ignoreCase = true)) {
            return filtered
        }
        val matching = filtered.filter {
            it.subCategory.equals(subCategory, ignoreCase = true) ||
            it.genres.any { g -> g.contains(subCategory, ignoreCase = true) || subCategory.contains(g, ignoreCase = true) }
        }
        return if (matching.isNotEmpty()) matching else filtered
    }

    fun getMediaItems(): List<MediaItem> = allMediaItems

    companion object {
        val allMediaItems = listOf(
            // ANIME (Matching recommend_tag API sub-tags)
            MediaItem(
                id = "anime_1",
                title = "Mushoku Tensei: Jobless Reincarnation",
                category = MediaCategory.ANIME,
                subCategory = "Trending Anime",
                rank = 1,
                year = 2024,
                country = "Japan",
                rating = 9.3f,
                durationOrEpisodes = "24 Episodes",
                ageRating = "TV-MA",
                genres = listOf("Trending Anime", "Isekai", "Fantasy", "Adventure", "Magic"),
                synopsis = "A 34-year-old NEET is reincarnated into a wondrous fantasy world as Rudeus Greyrat. Retaining his past memories and gifted with extraordinary magical talent, he resolves to live his new life to the absolute fullest without regrets.",
                posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200&auto=format&fit=crop&q=80",
                cast = listOf(
                    CastMember("Rudeus Greyrat", "Protagonist / Mage"),
                    CastMember("Sylphiette", "Childhood Friend"),
                    CastMember("Eris Boreas Greyrat", "Sword Prodigy"),
                    CastMember("Roxy Migurdia", "Water Saint Magician")
                ),
                episodes = listOf(
                    EpisodeItem(1, "The Teleportation Incident", "23m", "Rudeus continues his journey in the unfamiliar realm."),
                    EpisodeItem(2, "The Magic University", "24m", "An invitation arrives from the Ranoa Magic Academy."),
                    EpisodeItem(3, "Reunion & Discovery", "24m", "Familiar faces cross paths amidst ancient labyrinth mysteries.")
                )
            ),
            MediaItem(
                id = "anime_2",
                title = "That Time I Got Reincarnated as a Slime",
                category = MediaCategory.ANIME,
                subCategory = "Adventurers' Saga",
                rank = 2,
                year = 2024,
                country = "Japan",
                rating = 9.1f,
                durationOrEpisodes = "24 Episodes",
                ageRating = "TV-14",
                genres = listOf("Adventurers' Saga", "Isekai", "Fantasy", "Action", "Comedy"),
                synopsis = "Average 37-year-old Satoru Mikami is reborn in another world as a sentient slime named Rimuru Tempest. Through unique absorption skills, he builds a harmonious monster federation that alters world politics forever.",
                posterUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&auto=format&fit=crop&q=80",
                cast = listOf(
                    CastMember("Rimuru Tempest", "Leader / Demon Lord"),
                    CastMember("Benimaru", "Commander of Tempest"),
                    CastMember("Shion", "First Secretary & Bodyguard"),
                    CastMember("Veldora Tempest", "Storm Dragon")
                ),
                episodes = listOf(
                    EpisodeItem(1, "The Monster's Banquet", "24m", "Diplomatic emissaries convene at the capital of Tempest."),
                    EpisodeItem(2, "The Saint and the Demon", "24m", "Hinata Sakaguchi receives unsettling tidings from the Holy Empire."),
                    EpisodeItem(3, "Founding Festival", "23m", "Tempest prepares to unveil its marvels to all races.")
                )
            ),
            MediaItem(
                id = "anime_3",
                title = "Naruto: Shippuden [HD]",
                category = MediaCategory.ANIME,
                subCategory = "Burning with Ardour",
                rank = 3,
                year = 2017,
                country = "Japan",
                rating = 9.2f,
                durationOrEpisodes = "500 Episodes",
                ageRating = "TV-14",
                genres = listOf("Burning with Ardour", "Shonen", "Action", "Martial Arts", "Adventure"),
                synopsis = "Naruto Uzumaki returns to the Hidden Leaf Village stronger and more determined than ever to protect his ninja friends, confront the shadowy Akatsuki syndicate, and bring Sasuke home.",
                posterUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1514533450685-4493e01d1fdc?w=1200&auto=format&fit=crop&q=80",
                cast = listOf(
                    CastMember("Naruto Uzumaki", "Nine-Tails Jinchuriki"),
                    CastMember("Sasuke Uchiha", "Avenger / Sharingan"),
                    CastMember("Kakashi Hatake", "The Copy Ninja"),
                    CastMember("Sakura Haruno", "Medical Kunoichi")
                ),
                episodes = listOf(
                    EpisodeItem(375, "Kakashi vs. Obito", "23m", "The ultimate duel within the Kamui dimension unfolds."),
                    EpisodeItem(476, "The Final Battle: Part 1", "24m", "Naruto and Sasuke clash at the Valley of the End."),
                    EpisodeItem(477, "The Final Battle: Part 2", "24m", "Pure ninjutsu and will collide until dusk falls.")
                )
            ),
            MediaItem(
                id = "anime_4",
                title = "JoJo's Bizarre Adventure",
                category = MediaCategory.ANIME,
                subCategory = "Burning with Ardour",
                rank = 4,
                year = 2012,
                country = "Japan",
                rating = 9.0f,
                durationOrEpisodes = "190 Episodes",
                ageRating = "TV-MA",
                genres = listOf("Burning with Ardour", "Action", "Supernatural", "Adventure"),
                synopsis = "The legendary multi-generational chronicle of the Joestar bloodline, locked in historic battles against supernatural menaces, vampire overlords, and formidable Stand users across continents.",
                posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=1200&auto=format&fit=crop&q=80",
                cast = listOf(
                    CastMember("Jotaro Kujo", "Star Platinum"),
                    CastMember("Joseph Joestar", "Hamon Master"),
                    CastMember("DIO", "Vampire Lord / The World"),
                    CastMember("Josuke Higashikata", "Crazy Diamond")
                )
            ),
            MediaItem(
                id = "anime_5",
                title = "Demon Slayer: Kimetsu no Yaiba",
                category = MediaCategory.ANIME,
                subCategory = "Burning with Ardour",
                rank = 5,
                year = 2024,
                country = "Japan",
                rating = 9.4f,
                durationOrEpisodes = "55 Episodes",
                ageRating = "TV-14",
                genres = listOf("Burning with Ardour", "Action", "Dark Fantasy", "Supernatural"),
                synopsis = "Tanjiro Kamado joins the Demon Slayer Corps to avenge his slaughtered family and find a cure to turn his sister Nezuko back into a human, facing the Upper Rank demons in breathtaking battles.",
                posterUrl = "https://images.unsplash.com/photo-1569701813229-33284b643e3c?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1200&auto=format&fit=crop&q=80",
                cast = listOf(
                    CastMember("Tanjiro Kamado", "Sun Breathing Swordsman"),
                    CastMember("Nezuko Kamado", "Demon Sister"),
                    CastMember("Zenitsu Agatsuma", "Thunder Breathing"),
                    CastMember("Inosuke Hashibira", "Beast Breathing")
                )
            ),
            MediaItem(
                id = "anime_6",
                title = "Frieren: Beyond Journey's End",
                category = MediaCategory.ANIME,
                subCategory = "Adventurers' Saga",
                rank = 6,
                year = 2024,
                country = "Japan",
                rating = 9.5f,
                durationOrEpisodes = "28 Episodes",
                ageRating = "TV-14",
                genres = listOf("Adventurers' Saga", "Fantasy", "Adventure", "Drama", "Magic"),
                synopsis = "An elven mage named Frieren reflects on her past adventures with the Hero's party as she embarks on a poignant new expedition to understand humanity and honor remembered bonds.",
                posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=1200&auto=format&fit=crop&q=80",
                cast = listOf(
                    CastMember("Frieren", "Thousand-Year Mage"),
                    CastMember("Fern", "Apprentice Mage"),
                    CastMember("Stark", "Vanguard Warrior"),
                    CastMember("Himmel", "The Hero")
                )
            ),

            // MOVIES (Matching recommend_tag API sub-tags: Hollywood Movies, Bollywood Movies, Popular Movies)
            MediaItem(
                id = "movie_1",
                title = "Dune: Part Two",
                category = MediaCategory.MOVIES,
                subCategory = "Hollywood Movies",
                rank = 1,
                year = 2024,
                country = "USA",
                rating = 9.4f,
                durationOrEpisodes = "2h 46m",
                ageRating = "PG-13",
                genres = listOf("Popular Movies", "Hollywood Movies", "Sci-Fi", "Adventure", "Epic"),
                synopsis = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family. Facing a choice between the love of his life and the fate of the universe, he endeavors to prevent a terrible future.",
                posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1200&auto=format&fit=crop&q=80"
            ),
            MediaItem(
                id = "movie_2",
                title = "RRR: Rise Roar Revolt",
                category = MediaCategory.MOVIES,
                subCategory = "Bollywood Movies",
                rank = 2,
                year = 2022,
                country = "India",
                rating = 9.3f,
                durationOrEpisodes = "3h 07m",
                ageRating = "PG-13",
                genres = listOf("Bollywood Movies", "Popular Movies", "Action", "Drama", "History"),
                synopsis = "A fictitious story about two legendary revolutionaries and their journey away from home before they began fighting for their country in the 1920s.",
                posterUrl = "https://images.unsplash.com/photo-1533929736458-ca588d08c8be?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=1200&auto=format&fit=crop&q=80"
            ),
            MediaItem(
                id = "movie_3",
                title = "Oppenheimer",
                category = MediaCategory.MOVIES,
                subCategory = "Hollywood Movies",
                rank = 3,
                year = 2023,
                country = "USA",
                rating = 9.2f,
                durationOrEpisodes = "3h 00m",
                ageRating = "R",
                genres = listOf("Hollywood Movies", "Popular Movies", "Biography", "Drama", "History"),
                synopsis = "The story of American scientist J. Robert Oppenheimer and his historical role in the development of the atomic bomb at Los Alamos during the Manhattan Project.",
                posterUrl = "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=1200&auto=format&fit=crop&q=80"
            ),
            MediaItem(
                id = "movie_4",
                title = "Jawan",
                category = MediaCategory.MOVIES,
                subCategory = "Bollywood Movies",
                rank = 4,
                year = 2023,
                country = "India",
                rating = 8.9f,
                durationOrEpisodes = "2h 49m",
                ageRating = "TV-14",
                genres = listOf("Bollywood Movies", "Action", "Thriller"),
                synopsis = "A high-octane action thriller outlining the emotional journey of a man who is set to rectify the wrongs in society against corrupt oligarchs.",
                posterUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=1200&auto=format&fit=crop&q=80"
            ),
            MediaItem(
                id = "movie_5",
                title = "Interstellar",
                category = MediaCategory.MOVIES,
                subCategory = "Hollywood Movies",
                rank = 5,
                year = 2014,
                country = "USA",
                rating = 9.4f,
                durationOrEpisodes = "2h 49m",
                ageRating = "PG-13",
                genres = listOf("Hollywood Movies", "Popular Movies", "Sci-Fi", "Drama"),
                synopsis = "When Earth becomes uninhabitable in the future, a farmer and ex-NASA pilot, Joseph Cooper, is tasked to pilot a spacecraft to find a new planet for humans.",
                posterUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1200&auto=format&fit=crop&q=80"
            ),

            // SERIES (Matching recommend_tag API sub-tags: Indian Drama, Western Drama, Korean Drama, Trending drama)
            MediaItem(
                id = "series_1",
                title = "Shōgun",
                category = MediaCategory.SERIES,
                subCategory = "Western Drama",
                rank = 1,
                year = 2024,
                country = "USA / Japan",
                rating = 9.4f,
                durationOrEpisodes = "10 Episodes",
                ageRating = "TV-MA",
                genres = listOf("Trending drama", "Western Drama", "Historical Drama", "War"),
                synopsis = "When a mysterious European ship is found marooned in a nearby fishing village, Lord Yoshii Toranaga discovers secrets that could tip the scales of power.",
                posterUrl = "https://images.unsplash.com/photo-1528164344705-475426879c0d?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=1200&auto=format&fit=crop&q=80"
            ),
            MediaItem(
                id = "series_2",
                title = "Sacred Games",
                category = MediaCategory.SERIES,
                subCategory = "Indian Drama",
                rank = 2,
                year = 2020,
                country = "India",
                rating = 9.1f,
                durationOrEpisodes = "16 Episodes",
                ageRating = "TV-MA",
                genres = listOf("Trending drama", "Indian Drama", "Crime", "Thriller"),
                synopsis = "A link in their pasts leads an honest cop to a fugitive gang boss, whose cryptic warning spurs a quest to save Mumbai from cataclysm.",
                posterUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=1200&auto=format&fit=crop&q=80"
            ),
            MediaItem(
                id = "series_3",
                title = "Queen of Tears",
                category = MediaCategory.SERIES,
                subCategory = "Korean Drama",
                rank = 3,
                year = 2024,
                country = "South Korea",
                rating = 9.3f,
                durationOrEpisodes = "16 Episodes",
                ageRating = "TV-14",
                genres = listOf("Trending drama", "Korean Drama", "Romance", "Drama"),
                synopsis = "The queen of department stores and the prince of supermarkets weather a marital crisis until love miraculously begins to bloom again.",
                posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&auto=format&fit=crop&q=80"
            ),
            MediaItem(
                id = "series_4",
                title = "Severance",
                category = MediaCategory.SERIES,
                subCategory = "Western Drama",
                rank = 4,
                year = 2024,
                country = "USA",
                rating = 9.2f,
                durationOrEpisodes = "19 Episodes",
                ageRating = "TV-MA",
                genres = listOf("Trending drama", "Western Drama", "Sci-Fi", "Mystery"),
                synopsis = "Mark leads a team of office workers whose memories have been surgically divided between their work and personal lives.",
                posterUrl = "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1497215728101-856f4ea42174?w=1200&auto=format&fit=crop&q=80"
            ),

            // SHORT TV
            MediaItem(
                id = "short_1",
                title = "The Billionaire's Secret Heir",
                category = MediaCategory.SHORT_TV,
                subCategory = "Trending Shorts",
                rank = 1,
                year = 2024,
                country = "USA",
                rating = 8.8f,
                durationOrEpisodes = "45 Mini-Eps",
                ageRating = "PG-13",
                genres = listOf("Trending Shorts", "CEO Revenge", "Romance"),
                synopsis = "Disguised as a modest ride-share driver, Marcus must protect his estranged daughter and reclaim his corporate empire.",
                posterUrl = "https://images.unsplash.com/photo-1507679799987-c73779587ccf?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=1200&auto=format&fit=crop&q=80"
            ),
            MediaItem(
                id = "short_2",
                title = "Mistaken Identity: The Hidden Tycoon",
                category = MediaCategory.SHORT_TV,
                subCategory = "CEO Revenge",
                rank = 2,
                year = 2024,
                country = "USA",
                rating = 9.1f,
                durationOrEpisodes = "60 Mini-Eps",
                ageRating = "PG-13",
                genres = listOf("Trending Shorts", "CEO Revenge", "Drama"),
                synopsis = "Treated as a nobody at his high school reunion, Ethan reveals he is the majority shareholder of the city's largest conglomerate.",
                posterUrl = "https://images.unsplash.com/photo-1560250097-0b93528c311a?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1497366216548-37526070297c?w=1200&auto=format&fit=crop&q=80"
            ),
            MediaItem(
                id = "short_3",
                title = "Contract Marriage to the Rival Heir",
                category = MediaCategory.SHORT_TV,
                subCategory = "Quick Romance",
                rank = 3,
                year = 2024,
                country = "UK",
                rating = 8.9f,
                durationOrEpisodes = "50 Mini-Eps",
                ageRating = "PG-13",
                genres = listOf("Trending Shorts", "Quick Romance", "Romance"),
                synopsis = "Two bitter rivals sign a one-year marriage treaty to save their family corporations, only to find real sparks flying.",
                posterUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=1200&auto=format&fit=crop&q=80"
            ),
            MediaItem(
                id = "short_4",
                title = "The Return of the Phoenix Queen",
                category = MediaCategory.SHORT_TV,
                subCategory = "Trending Shorts",
                rank = 4,
                year = 2024,
                country = "Global",
                rating = 9.3f,
                durationOrEpisodes = "70 Mini-Eps",
                ageRating = "PG-13",
                genres = listOf("Trending Shorts", "CEO Revenge", "Action"),
                synopsis = "Betrayed and cast out five years ago, she returns in glory with an unstoppable international alliance.",
                posterUrl = "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=1200&auto=format&fit=crop&q=80"
            )
        )
    }
}
