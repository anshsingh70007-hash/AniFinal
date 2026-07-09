package com.example.aniflow.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Episode(
    val id: String,
    val name: String,
    val number: Int,
    val description: String? = null,
    val thumbnail: String? = null
)
