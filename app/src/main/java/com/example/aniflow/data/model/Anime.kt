package com.example.aniflow.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Anime(
    val id: Int,
    val title: String,
    val englishTitle: String? = null,
    val coverImage: String,
    val bannerImage: String? = null,
    val description: String? = null,
    val episodes: Int? = null,
    val averageScore: Int? = null,
    val genres: List<String> = emptyList(),
    val status: String = "FINISHED",
    val season: String? = null,
    val seasonYear: Int? = null,
    val studioName: String? = null,
    val nextAiringEpisode: Int? = null,
    val nextAiringAt: Long? = null,
    val trailerUrl: String? = null
)

@Serializable
data class AiringAnime(
    val mediaId: Int,
    val title: String,
    val coverImageUrl: String,
    val airingAt: Long,
    val episode: Int
)
