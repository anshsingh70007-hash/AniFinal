package com.example.aniflow.data.model

/**
 * One trailer on a title's page: the PV of exactly one AniList entry (a season, a movie, an OVA).
 * [youtubeId] always comes from that entry's own AniList record, so "Season 2" can only ever play
 * Season 2's trailer.
 */
data class TrailerClip(
    val animeId: Int,
    val youtubeId: String,
    /** "Season 2", "Mugen Train", or "Trailer" when the franchise is unknown. */
    val label: String,
    /** "TV · 2019 · 25 eps" */
    val meta: String,
    /** The title this page is about. */
    val isCurrent: Boolean,
) {
    val thumbnail: String get() = "https://i.ytimg.com/vi/$youtubeId/hqdefault.jpg"

    companion object {
        /**
         * Main-line seasons and movies in release order, each with its own trailer. When AniList
         * reuses one upload for several entries (it happens with franchise PVs), the clip stays on
         * the current title if it is one of them, else on the earliest entry — never duplicated.
         */
        fun build(current: Anime, franchise: List<FranchiseEntry>, trailers: Map<Int, String>): List<TrailerClip> {
            val entries = franchise.filter { it.mainLine || it.format == "MOVIE" || it.id == current.id }.sortedBy { it.sortKey }
            val clips = if (entries.isEmpty()) {
                val yt = trailers[current.id] ?: return emptyList()
                listOf(TrailerClip(current.id, yt, "Trailer", listOfNotNull(current.format?.replace('_', ' '), current.seasonYear?.toString()).joinToString(" · "), true))
            } else {
                entries.mapNotNull { e -> trailers[e.id]?.let { TrailerClip(e.id, it, e.label, e.meta, e.id == current.id) } }
            }
            return clips.groupBy { it.youtubeId }.values.map { same -> same.firstOrNull { it.isCurrent } ?: same.first() }
                .sortedBy { c -> clips.indexOf(c) }
        }
    }
}
