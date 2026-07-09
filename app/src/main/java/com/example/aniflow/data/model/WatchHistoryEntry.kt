package com.example.aniflow.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WatchHistoryEntry(
    val animeId: Int,
    val title: String,
    val coverImage: String,
    val episodeNumber: Int,
    val episodeName: String,
    val progressMs: Long,
    val durationMs: Long,
    val lastWatchedTime: Long
)
