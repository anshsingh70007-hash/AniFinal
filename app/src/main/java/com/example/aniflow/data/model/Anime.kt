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
    val format: String? = null,
    val averageScore: Int? = null,
    val genres: List<String> = emptyList(),
    val status: String = "FINISHED",
    val season: String? = null,
    val seasonYear: Int? = null,
    val studioName: String? = null,
    val nextAiringEpisode: Int? = null,
    val nextAiringAt: Long? = null,
    val trailerUrl: String? = null,
    val recommendations: List<Anime> = emptyList()
)

@Serializable
data class AiringAnime(
    val mediaId: Int,
    val title: String,
    val coverImageUrl: String,
    val airingAt: Long,
    val episode: Int
)

fun formatAiringSchedule(airingAtEpochSeconds: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = airingAtEpochSeconds - now
    if (diff <= 0) return "Airing Now"

    val calendar = java.util.Calendar.getInstance()
    val currentDayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
    val currentYear = calendar.get(java.util.Calendar.YEAR)

    val targetCal = java.util.Calendar.getInstance().apply {
        timeInMillis = airingAtEpochSeconds * 1000
    }
    val targetDayOfYear = targetCal.get(java.util.Calendar.DAY_OF_YEAR)
    val targetYear = targetCal.get(java.util.Calendar.YEAR)

    val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    val formattedTime = timeFormat.format(java.util.Date(airingAtEpochSeconds * 1000))

    return when {
        currentYear == targetYear && currentDayOfYear == targetDayOfYear -> {
            if (diff < 3600) {
                val mins = (diff / 60).coerceAtLeast(1)
                "Today in ${mins}m"
            } else if (diff < 3600 * 6) {
                val hours = diff / 3600
                val mins = (diff % 3600) / 60
                "Today in ${hours}h ${mins}m"
            } else {
                "Today at $formattedTime"
            }
        }
        currentYear == targetYear && targetDayOfYear == currentDayOfYear + 1 -> {
            "Tomorrow at $formattedTime"
        }
        diff < 7 * 86400 -> {
            val dayFormat = java.text.SimpleDateFormat("EEE at h:mm a", java.util.Locale.getDefault())
            dayFormat.format(java.util.Date(airingAtEpochSeconds * 1000))
        }
        else -> {
            val fullFormat = java.text.SimpleDateFormat("MMM d at h:mm a", java.util.Locale.getDefault())
            fullFormat.format(java.util.Date(airingAtEpochSeconds * 1000))
        }
    }
}

fun formatRecentlyAired(airedEpochSeconds: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = (now - airedEpochSeconds).coerceAtLeast(0)
    val hours = diff / 3600
    val days = diff / 86400

    return when {
        diff < 3600 -> {
            val mins = (diff / 60).coerceAtLeast(1)
            "${mins}m ago"
        }
        hours < 24 -> {
            "${hours}h ago"
        }
        days == 1L -> {
            "Yesterday"
        }
        days < 7 -> {
            "${days}d ago"
        }
        else -> {
            val dateFormat = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            dateFormat.format(java.util.Date(airedEpochSeconds * 1000))
        }
    }
}
