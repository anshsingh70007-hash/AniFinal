package com.example.aniflow.ui.home

import com.example.aniflow.data.repository.HomeShelf

/**
 * The Home rows, shared by phone and TV: which rows exist, what they are called and their default
 * order. Both screens must show the same rows from the same data under these names; only how a
 * row is drawn (and, on the phone, the person's own drag-to-reorder) differs.
 */
object HomeRowSpec {
    const val CONTINUE = "cont"
    const val PICKS = "picks"
    const val BECAUSE = "because"
    const val AIRING = "airing"
    const val YOUR_PICKS = "yourpicks"
    const val TRENDING = "trending"
    const val FRESH = "recent"
    const val POPULAR = "popular"
    const val TOP = "top"
    const val SEASONAL = "seasonal"
    const val ACTION = "action"
    const val ROMANCE = "romance"
    const val UPCOMING = "upcoming"
    private const val SHELF = "shelf_"

    const val T_CONTINUE = "Continue watching"
    const val T_PICKS = "Top picks for you"
    fun becauseTitle(seed: String) = "Because you watched $seed"
    const val T_AIRING = "Airing today"
    const val T_YOUR_PICKS = "Your picks"
    const val T_TRENDING = "Trending now"
    const val T_FRESH = "Fresh episodes"
    const val T_POPULAR = "Popular all time"
    const val T_TOP = "Highest rated"
    const val T_SEASONAL = "Airing this season"
    const val T_ACTION = "Action & adventure"
    const val T_ROMANCE = "Romance picks"
    const val T_UPCOMING = "Coming soon"

    fun shelfKey(shelf: HomeShelf) = SHELF + shelf.key

    /** Default order of every row, shelves from the feed included. */
    fun order(shelves: List<HomeShelf>): List<String> =
        listOf(CONTINUE, PICKS, BECAUSE, AIRING, YOUR_PICKS, TRENDING, FRESH) +
            shelves.filter { !it.late }.map(::shelfKey) +
            listOf(POPULAR, TOP, SEASONAL, ACTION, ROMANCE) +
            shelves.filter { it.late }.map(::shelfKey) +
            UPCOMING
}
