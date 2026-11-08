package com.example.aniflow.data

import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.WatchHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val now = 1_800_000_000_000L
    private fun anime(id: Int, vararg genres: String, score: Int = 75, recs: List<Anime> = emptyList()) =
        Anime(id = id, title = "A$id", coverImage = "c$id", genres = genres.toList(), averageScore = score, recommendations = recs)
    private fun watched(id: Int, ep: Int = 3, daysAgo: Int = 0, progress: Long = 600_000, duration: Long = 1_400_000) =
        WatchHistoryEntry(id, "A$id", "c$id", ep, "", progress, duration, now - daysAgo * 86_400_000L)

    @Test fun tasteFollowsWhatYouWatch() {
        val profile = Suggestions.genreProfile(listOf(anime(1, "Action", "Fantasy") to watched(1)), now)
        val ranked = Suggestions.rank(
            listOf(anime(10, "Romance", score = 90), anime(11, "Action", "Fantasy", score = 70)),
            profile, exclude = emptySet()
        )
        assertEquals(11, ranked.first().id)
    }

    @Test fun recentViewingOutweighsOld() {
        val profile = Suggestions.genreProfile(
            listOf(anime(1, "Horror") to watched(1, daysAgo = 90), anime(2, "Sports") to watched(2, daysAgo = 0)), now
        )
        assertTrue(profile.getValue("Sports") > profile.getValue("Horror"))
    }

    @Test fun excludedIdsNeverAppear() {
        val ranked = Suggestions.rank(listOf(anime(1, "Action"), anime(2, "Action")), mapOf("Action" to 1.0), exclude = setOf(1))
        assertEquals(listOf(2), ranked.map { it.id })
    }

    @Test fun moreLikeThisDropsFranchiseAndFinished() {
        val show = anime(1, "Action", recs = listOf(anime(2, "Action"), anime(3, "Action"), anime(4, "Action"), anime(1, "Action")))
        val out = Suggestions.moreLikeThis(show, franchiseIds = setOf(2), finishedIds = setOf(3))
        assertEquals(listOf(4), out.map { it.id })
    }

    @Test fun finishedMeansLastEpisodePlayedThrough() {
        assertTrue(Suggestions.isFinished(watched(1, ep = 12, progress = 1_350_000), totalEpisodes = 12))
        assertFalse(Suggestions.isFinished(watched(1, ep = 11, progress = 1_350_000), totalEpisodes = 12))
        assertFalse(Suggestions.isFinished(watched(1, ep = 12, progress = 300_000), totalEpisodes = 12))
        assertFalse(Suggestions.isFinished(watched(1, ep = 12, progress = 1_350_000), totalEpisodes = null))
    }

    @Test fun salvageKeepsGoodEntriesWithNestedObjects() {
        val raw = """[{"animeId":1,"title":"x","coverImage":"c","episodeNumber":2,"episodeName":"","progressMs":5,"durationMs":9,"lastWatchedTime":1},{"animeId":"bad"}]"""
        val out = salvageList<WatchHistoryEntry>(NetworkModule.json, raw)
        assertEquals(listOf(1), out.map { it.animeId })
    }
}
