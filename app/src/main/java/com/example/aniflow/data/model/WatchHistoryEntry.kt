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

/** Watched to the credits (90%+). */
val WatchHistoryEntry.finished: Boolean get() = durationMs > 0 && progressMs >= durationMs * 9 / 10

/** How many episodes are out past the one you were on, given the newest aired one; 0 when none. */
fun WatchHistoryEntry.newEpisodes(latestAired: Int?): Int = latestAired?.let { (it - episodeNumber).coerceAtLeast(0) } ?: 0

/** What to badge as new: only once the episode you were on is finished. Mid-episode on E1 of a long
 *  show is not "1179 new episodes"; it is E1 with time left. */
fun WatchHistoryEntry.freshEpisodes(latestAired: Int?): Int = if (finished) newEpisodes(latestAired) else 0

/** The episode "continue" should open: the next one once this one is finished and it's out. */
fun WatchHistoryEntry.continueEpisode(latestAired: Int?): Int =
    if (finished && newEpisodes(latestAired) > 0) episodeNumber + 1 else episodeNumber

/** "New episode" / "3 new episodes"; null when nothing new. */
fun newEpisodesLabel(count: Int): String? = when {
    count <= 0 -> null
    count == 1 -> "New episode"
    else -> "$count new episodes"
}
