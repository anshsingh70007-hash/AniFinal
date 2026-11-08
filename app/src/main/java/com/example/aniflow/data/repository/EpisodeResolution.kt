package com.example.aniflow.data.repository

import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.AnimeIdentity
import com.example.aniflow.data.model.Episode
import com.example.aniflow.data.model.EpisodeLookupResult

/**
 * The single way an AniList entry becomes a playable episode list. Detail and Player both call
 * this, so they can never disagree about which provider series (and so which episodes) a show
 * maps to — they used to build identities and break ambiguity ties independently.
 *
 * Ambiguity is already resolved inside [AnimeRepository.getEpisodes] (it tries the top
 * candidates); an [EpisodeLookupResult.Ambiguous] here means none of them had episodes.
 */
suspend fun AnimeRepository.resolveEpisodes(anime: Anime): EpisodeLookupResult {
    val identity = AnimeIdentity(
        anilistId = anime.id,
        // Search both spellings: provider catalogues often list a show only under romaji.
        title = anime.romajiTitle ?: anime.title,
        englishTitle = anime.englishTitle,
        seasonYear = anime.seasonYear,
        // Unknown stays unknown: guessing "MOVIE" for any 1-episode entry made OVAs unmatchable.
        format = anime.format,
        expectedEpisodes = anime.episodes,
    )
    return when (val r = getEpisodes(identity)) {
        is EpisodeLookupResult.Matched -> r.copy(episodes = airedOnly(anime, r.episodes.distinctBy { it.number }.sortedBy { it.number }))
        else -> r
    }
}

/**
 * Hides episodes of an airing show that haven't aired yet (providers sometimes pre-list them).
 * Only applied when the provider numbers per season: with absolute numbering (a season listed as
 * 61-75 while AniList says "next: ep 8") the cut would have deleted every episode.
 */
internal fun airedOnly(anime: Anime, episodes: List<Episode>): List<Episode> {
    val next = anime.nextAiringEpisode ?: return episodes
    if (anime.status != "RELEASING" || next <= 1 || episodes.isEmpty()) return episodes
    val lastAired = next - 1
    if (episodes.first().number > lastAired) return episodes
    return episodes.filter { it.number <= lastAired }
}
