package com.example.data.model

enum class MediaCategory(val title: String, val rawTagName: String) {
    MOVIES("Movies", "Movies"),
    SERIES("Series", "Series"),
    ANIME("Anime", "Anime"),
    SHORT_TV("Short TV", "Short TV");

    companion object {
        fun fromTagName(name: String): MediaCategory? {
            return values().firstOrNull {
                it.rawTagName.equals(name, ignoreCase = true) ||
                it.title.equals(name, ignoreCase = true) ||
                it.name.equals(name, ignoreCase = true)
            }
        }
    }
}

data class CastMember(
    val name: String,
    val role: String,
    val avatarUrl: String = ""
)

data class EpisodeItem(
    val episodeNumber: Int,
    val title: String,
    val duration: String,
    val description: String = ""
)

data class MediaItem(
    val id: String,
    val title: String,
    val category: MediaCategory,
    val subCategory: String,
    val rank: Int,
    val year: Int,
    val country: String,
    val rating: Float,
    val durationOrEpisodes: String,
    val ageRating: String,
    val genres: List<String>,
    val synopsis: String,
    val posterUrl: String,
    val backdropUrl: String,
    val cast: List<CastMember> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val totalViews: String = "1.2M",
    val isTrending: Boolean = true
) {
    val formattedRating: String
        get() = String.format(java.util.Locale.US, "%.1f", rating)
}
