package com.example.aniflow.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val updateUrl: String,
    val updateNotes: String? = null,
    val forceUpdate: Boolean = false,
    val silentUpdate: Boolean = false,
    val maintenance: Boolean = false,
    /** Every place the APK can be fetched, best first (one per mirror). */
    val apkUrls: List<String> = emptyList(),
    /** The APK must hash to this; from the signed manifest. */
    val apkSha256: String? = null,
    val apkSize: Long? = null,
)
