package com.example.aniflow.data

import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.WatchHistoryEntry
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * On-device taste model. Everything here is pure and cheap: no network, no state.
 *
 * Taste = genres of what you watched, weighted by how far you got and how recently. A candidate's
 * score blends how well its genres match that taste with its community score, so a perfect genre
 * match that is badly rated does not beat a strong near-match.
 */
object Suggestions {
    private const val DAY_MS = 86_400_000.0

    /** You finished it: the last episode the show has, played to (nearly) the end. */
    fun isFinished(h: WatchHistoryEntry, totalEpisodes: Int?): Boolean {
        val nearEnd = h.durationMs > 0 && h.progressMs >= h.durationMs - 120_000
        return nearEnd && totalEpisodes != null && totalEpisodes > 0 && h.episodeNumber >= totalEpisodes
    }

    /** genre -> weight in 0..1, from watched shows whose details are known. */
    fun genreProfile(watched: List<Pair<Anime, WatchHistoryEntry>>, now: Long = System.currentTimeMillis()): Map<String, Double> {
        val raw = HashMap<String, Double>()
        for ((anime, h) in watched) {
            if (anime.genres.isEmpty()) continue
            // Half-life of about two weeks; a few episodes in counts more than a sampled first one.
            val ageDays = ((now - h.lastWatchedTime).coerceAtLeast(0L)) / DAY_MS
            val recency = exp(-ageDays / 20.0)
            val depth = 0.5 + 0.5 * (h.episodeNumber.coerceIn(1, 6) / 6.0)
            val w = recency * depth / sqrt(anime.genres.size.toDouble())
            anime.genres.forEach { g -> raw[g] = (raw[g] ?: 0.0) + w }
        }
        val max = raw.values.maxOrNull() ?: return emptyMap()
        return raw.mapValues { it.value / max }
    }

    fun score(a: Anime, profile: Map<String, Double>): Double {
        val quality = (a.averageScore ?: 65).coerceIn(0, 100) / 100.0
        if (profile.isEmpty() || a.genres.isEmpty()) return quality * 0.5
        val affinity = a.genres.sumOf { profile[it] ?: 0.0 } / sqrt(a.genres.size.toDouble())
        return affinity.coerceAtMost(1.6) / 1.6 * 0.65 + quality * 0.35
    }

    /** Best-first picks from [pool] for this taste, never anything in [exclude]. */
    fun rank(pool: List<Anime>, profile: Map<String, Double>, exclude: Set<Int>, limit: Int = 20): List<Anime> =
        pool.asSequence()
            .filter { it.id !in exclude && it.coverImage.isNotBlank() }
            .distinctBy { it.id }
            .sortedByDescending { score(it, profile) }
            .take(limit)
            .toList()

    /**
     * "More Like This" on a detail page: AniList's recommendations re-ordered by genre overlap with
     * the show itself, nudged by the viewer's taste, minus other entries of the same franchise
     * (they have their own rows) and anything already finished.
     */
    fun moreLikeThis(
        anime: Anime,
        franchiseIds: Set<Int>,
        finishedIds: Set<Int>,
        taste: Map<String, Double> = emptyMap(),
    ): List<Anime> {
        val own = anime.genres.toSet()
        return anime.recommendations
            .asSequence()
            .filter { it.id != anime.id && it.id !in franchiseIds && it.id !in finishedIds }
            .distinctBy { it.id }
            .withIndex()
            .sortedByDescending { (i, a) ->
                val overlap = if (own.isEmpty() || a.genres.isEmpty()) 0.0
                else a.genres.count { it in own }.toDouble() / sqrt((own.size * a.genres.size).toDouble())
                // AniList's own order (by community votes) still counts for a lot.
                val community = 1.0 - i / 12.0
                overlap * 0.45 + community * 0.35 + (if (taste.isEmpty()) 0.0 else score(a, taste) * 0.2)
            }
            .map { it.value }
            .toList()
    }
}
