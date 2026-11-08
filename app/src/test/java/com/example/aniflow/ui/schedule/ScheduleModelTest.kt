package com.example.aniflow.ui.schedule

import com.example.aniflow.data.model.AiringAnime
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.remote.currentCour
import com.example.aniflow.ui.tv.home.comingLabel
import com.example.aniflow.ui.tv.home.episodeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleModelTest {
    private fun ep(at: Long, id: Int = 1, n: Int = 1) = AiringAnime(id, "Show $id", "", at, n)

    @Test fun countdownRoundsUpToTheMinute() {
        assertEquals("in 1m", countdownText(1))
        assertEquals("in 59m", countdownText(59 * 60))
        assertEquals("in 1h 0m", countdownText(60 * 60))
        assertEquals("in 2h 13m", countdownText(2 * 3600 + 13 * 60))
        assertEquals("in 1d 2h", countdownText(26 * 3600))
    }

    @Test fun statusIsCountdownThenLiveThenOut() {
        val now = 1_000_000L
        val upcoming = Color0
        assertEquals("in 5m", statusOf(now + 300, now, upcoming).first)
        assertEquals("LIVE · just out", statusOf(now - 60, now, upcoming).first)
        assertEquals("Out now", statusOf(now - LIVE_WINDOW_S - 1, now, upcoming).first)
    }

    @Test fun nowDividerSitsBeforeTheFirstUpcomingEpisode() {
        val now = 1_000L
        assertEquals(0, nowSplit(listOf(ep(1_100), ep(1_200)), now))
        assertEquals(1, nowSplit(listOf(ep(900), ep(1_200)), now))
        assertEquals(2, nowSplit(listOf(ep(900), ep(950)), now)) // everything aired: divider at the end
        assertEquals(0, nowSplit(emptyList(), now))
    }

    @Test fun weekStartsYesterdayAndBucketsByLocalDay() {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0) }
        val noonToday = cal.timeInMillis / 1000
        val days = buildScheduleDays(listOf(ep(noonToday, 1), ep(noonToday - 86_400, 2), ep(noonToday + 2 * 86_400, 3)))
        assertEquals(7, days.size)
        assertEquals("Today", days[1].weekday)
        assertTrue(days[1].isToday)
        assertEquals(listOf(2), days[0].items.map { it.mediaId })
        assertEquals(listOf(1), days[1].items.map { it.mediaId })
        assertEquals(listOf(3), days[3].items.map { it.mediaId })
    }

    @Test fun courFollowsTheBroadcastQuarters() {
        // java.util.Calendar months are 0-based.
        assertEquals(listOf("WINTER", "WINTER", "WINTER"), (Calendar.JANUARY..Calendar.MARCH).map { currentCour(it) })
        assertEquals(listOf("SPRING", "SPRING", "SPRING"), (Calendar.APRIL..Calendar.JUNE).map { currentCour(it) })
        assertEquals(listOf("SUMMER", "SUMMER", "SUMMER"), (Calendar.JULY..Calendar.SEPTEMBER).map { currentCour(it) })
        // December is still Fall: it used to map to Winter and show last January's line-up.
        assertEquals(listOf("FALL", "FALL", "FALL"), (Calendar.OCTOBER..Calendar.DECEMBER).map { currentCour(it) })
    }

    @Test fun episodeLabelsNeverLie() {
        val base = Anime(id = 1, title = "x", coverImage = "")
        assertNull(base.copy(episodes = 0).episodeLabel())
        assertNull(base.copy(episodes = null).episodeLabel())
        assertNull(base.copy(episodes = 1, format = "MOVIE").episodeLabel())
        assertEquals("1 episode", base.copy(episodes = 1, format = "OVA").episodeLabel())
        assertEquals("24 eps", base.copy(episodes = 24).episodeLabel(short = true))
    }

    @Test fun comingLabelOnlyForAnnouncedTitles() {
        val base = Anime(id = 1, title = "x", coverImage = "")
        assertNull(base.comingLabel())
        assertEquals("Coming Spring 2027", base.copy(status = "NOT_YET_RELEASED", season = "SPRING", seasonYear = 2027).comingLabel())
        assertEquals("Coming 2027", base.copy(status = "NOT_YET_RELEASED", seasonYear = 2027).comingLabel())
        assertEquals("Coming soon", base.copy(status = "NOT_YET_RELEASED").comingLabel())
    }

    private companion object { val Color0 = androidx.compose.ui.graphics.Color(0xFF000000) }
}
