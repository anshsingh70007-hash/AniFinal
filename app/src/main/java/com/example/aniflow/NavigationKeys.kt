package com.example.aniflow

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Main : NavKey

@Serializable
data class Detail(val animeId: Int) : NavKey

@Serializable
data class Player(val animeId: Int, val episodeNumber: Int) : NavKey
