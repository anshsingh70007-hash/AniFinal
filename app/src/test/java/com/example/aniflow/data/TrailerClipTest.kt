package com.example.aniflow.data

import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.FranchiseEntry
import com.example.aniflow.data.model.TrailerClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerClipTest {
    private fun entry(id: Int, label: String, year: Int, format: String = "TV", mainLine: Boolean = true) = FranchiseEntry(
        id = id, title = label, format = format, status = "FINISHED", episodes = 12, coverImage = null,
        startYear = year, startMonth = 1, startDay = 1, mainLine = mainLine, label = label,
        relation = if (mainLine) null else "SIDE_STORY",
    )

    private val s2 = Anime(id = 2, title = "Show S2", coverImage = "")

    @Test fun eachSeasonGetsOnlyItsOwnTrailerInReleaseOrder() {
        val franchise = listOf(entry(3, "Season 3", 2022), entry(1, "Season 1", 2018), entry(2, "Season 2", 2020), entry(9, "The Movie", 2021, "MOVIE", mainLine = false))
        val clips = TrailerClip.build(s2, franchise, mapOf(1 to "aaaaaaaaaaa", 2 to "bbbbbbbbbbb", 9 to "mmmmmmmmmmm"))
        assertEquals(listOf("Season 1", "Season 2", "The Movie"), clips.map { it.label })
        assertEquals(mapOf(1 to "aaaaaaaaaaa", 2 to "bbbbbbbbbbb", 9 to "mmmmmmmmmmm"), clips.associate { it.animeId to it.youtubeId })
        assertTrue(clips.single { it.isCurrent }.animeId == 2)
    }

    @Test fun reusedUploadStaysOnCurrentTitleAndSideStoriesAreSkipped() {
        val franchise = listOf(entry(1, "Season 1", 2018), entry(2, "Season 2", 2020), entry(7, "Recap", 2019, mainLine = false))
        val clips = TrailerClip.build(s2, franchise, mapOf(1 to "sameeeeeeee", 2 to "sameeeeeeee", 7 to "recapppppp1"))
        assertEquals(listOf(2), clips.map { it.animeId })
    }

    @Test fun noFranchiseFallsBackToOwnTrailerOnly() {
        assertEquals(listOf("Trailer"), TrailerClip.build(s2, emptyList(), mapOf(2 to "bbbbbbbbbbb", 5 to "ccccccccccc")).map { it.label })
        assertEquals(emptyList<TrailerClip>(), TrailerClip.build(s2, emptyList(), emptyMap()))
    }
}
