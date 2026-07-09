package com.example.aniflow.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val updateUrl: String,
    val updateNotes: String? = null,
    val forceUpdate: Boolean = false,
    val silentUpdate: Boolean = false
)

