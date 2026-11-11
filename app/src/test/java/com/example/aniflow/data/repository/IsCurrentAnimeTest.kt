package com.example.aniflow.data.repository

import com.example.aniflow.data.model.Anime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsCurrentAnimeTest {
    private fun a(status: String, year: Int?) = Anime(id = 1, title = "x", coverImage = "", status = status, seasonYear = year)

    @Test fun airingIsCurrentWhateverTheYear() = assertTrue(isCurrentAnime(a("RELEASING", 1999), 2026)) // ONE PIECE

    @Test fun thisAndLastYearAreCurrent() {
        assertTrue(isCurrentAnime(a("FINISHED", 2026), 2026))
        assertTrue(isCurrentAnime(a("FINISHED", 2025), 2026))
    }

    @Test fun evergreensAreNot() {
        assertFalse(isCurrentAnime(a("FINISHED", 2004), 2026)) // Bleach
        assertFalse(isCurrentAnime(a("FINISHED", 2024), 2026))
        assertFalse(isCurrentAnime(a("FINISHED", null), 2026))
    }

    @Test fun notStartedIsNot() = assertFalse(isCurrentAnime(a("NOT_YET_RELEASED", 2026), 2026))
}
