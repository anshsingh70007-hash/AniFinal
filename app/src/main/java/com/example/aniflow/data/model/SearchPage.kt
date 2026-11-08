package com.example.aniflow.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchPage(
    val results: List<Anime>,
    val hasNextPage: Boolean,
    val currentPage: Int
)
