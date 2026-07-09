package com.example.aniflow.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamingSource(
    val url: String,
    val quality: String,
    val isM3U8: Boolean = true,
    val headers: Map<String, String>? = null
)

@Serializable
data class SubtitleTrack(
    val url: String,
    val lang: String,
    val label: String
)

@Serializable
data class EpisodeSourcesResponse(
    val sources: List<StreamingSource>,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val headers: Map<String, String>? = null
)
