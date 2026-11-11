package com.example.aniflow.data

import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.data.model.continueEpisode
import com.example.aniflow.data.model.finished
import com.example.aniflow.data.model.freshEpisodes
import com.example.aniflow.data.model.newEpisodes
import com.example.aniflow.data.model.newEpisodesLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodesTest {
    private fun entry(ep: Int, progress: Long, duration: Long = 1_440_000L) =
        WatchHistoryEntry(1, "Show", "", ep, "", progress, duration, 0L)

    @Test fun `nothing new when you are on the latest episode`() {
        assertEquals(0, entry(5, 100_000).newEpisodes(5))
        assertEquals(0, entry(5, 100_000).newEpisodes(null))
        assertNull(newEpisodesLabel(0))
    }

    @Test fun `counts episodes out past yours`() {
        assertEquals(1, entry(5, 100_000).newEpisodes(6))
        assertEquals(3, entry(5, 100_000).newEpisodes(8))
        assertEquals("New episode", newEpisodesLabel(1))
        assertEquals("3 new episodes", newEpisodesLabel(3))
    }

    @Test fun `badge new episodes only once yours is finished`() {
        assertEquals(0, entry(1, 100_000).freshEpisodes(1180))     // mid E1 of a long show: not "1179 new"
        assertEquals(1179, entry(1, 1_400_000).freshEpisodes(1180))
        assertEquals(0, entry(5, 1_400_000).freshEpisodes(5))
    }

    @Test fun `finished means 90 percent`() {
        assertTrue(entry(5, 1_300_000).finished)
        assertFalse(entry(5, 1_200_000).finished)
        assertFalse(entry(5, 0, duration = 0).finished)
    }

    @Test fun `continue opens the next episode only once yours is finished and it is out`() {
        assertEquals(6, entry(5, 1_400_000).continueEpisode(6))   // finished, E6 out
        assertEquals(5, entry(5, 600_000).continueEpisode(6))     // mid-episode: resume it
        assertEquals(5, entry(5, 1_400_000).continueEpisode(5))   // finished, nothing new yet
        assertEquals(5, entry(5, 1_400_000).continueEpisode(null))
    }
}
