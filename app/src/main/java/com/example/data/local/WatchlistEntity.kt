package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val category: String,
    val subCategory: String,
    val rank: Int,
    val year: Int,
    val country: String,
    val rating: Float,
    val durationOrEpisodes: String,
    val ageRating: String,
    val genres: String, // comma-separated
    val synopsis: String,
    val posterUrl: String,
    val backdropUrl: String,
    val addedAt: Long = System.currentTimeMillis()
)
