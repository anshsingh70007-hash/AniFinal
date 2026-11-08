package com.example.aniflow.data.model

/**
 * One title in an anime's franchise — a season, movie, OVA, ONA or special — built from AniList
 * relations (data/remote/AniListApi.getFranchise). Not persisted.
 */
data class FranchiseEntry(
    val id: Int,
    val title: String,
    val format: String?,
    val status: String?,
    val episodes: Int?,
    val coverImage: String?,
    val startYear: Int?,
    val startMonth: Int?,
    val startDay: Int?,
    /** On the story's main line (chained by sequel/prequel) rather than a side entry. */
    val mainLine: Boolean,
    /** Short name inside the franchise: "Season 2", "The Final Season", "Mugen Train". */
    val label: String,
    /** Side entries only: SIDE_STORY, SUMMARY, SPIN_OFF, ALTERNATIVE, COMPILATION, CONTAINS, PARENT. */
    val relation: String?,
) {
    val sortKey: Int get() = (startYear ?: 9999) * 10_000 + (startMonth ?: 12) * 100 + (startDay ?: 31)

    val formatLabel: String get() = formatLabel(format)

    /** "TV · 2019 · 25 eps" */
    val meta: String
        get() = listOfNotNull(
            formatLabel.takeIf { it.isNotEmpty() },
            if (status == "NOT_YET_RELEASED") "Upcoming" else startYear?.toString(),
            episodes?.takeIf { it > 1 }?.let { "$it eps" },
        ).joinToString(" · ")

    val relationLabel: String?
        get() = when (relation) {
            "SIDE_STORY" -> "Side story"
            "SUMMARY" -> "Recap"
            "SPIN_OFF" -> "Spin-off"
            "ALTERNATIVE" -> "Alternate"
            "COMPILATION" -> "Compilation"
            "CONTAINS" -> "Included"
            "PARENT" -> "Main story"
            else -> null
        }

    companion object {
        fun formatLabel(format: String?): String = when (format) {
            "TV" -> "TV"
            "TV_SHORT" -> "TV Short"
            "MOVIE" -> "Movie"
            "SPECIAL" -> "Special"
            "OVA" -> "OVA"
            "ONA" -> "ONA"
            "MUSIC" -> "Music"
            else -> ""
        }
    }
}
