package com.example.aniflow.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.aniflow.data.model.FranchiseEntry
import com.example.aniflow.theme.PrimaryAccent
import com.example.aniflow.theme.PrimaryAccentLight
import com.example.aniflow.theme.SurfaceCard
import com.example.aniflow.theme.TextPrimary
import com.example.aniflow.theme.TextTertiary

/**
 * How a franchise splits into rows: "Seasons" (main line) + "Movies, OVAs & specials" when the
 * story has two or more main entries; otherwise everything in one "Related" row.
 */
internal fun franchiseRows(entries: List<FranchiseEntry>): List<Pair<String, List<FranchiseEntry>>> {
    if (entries.size < 2) return emptyList()
    val main = entries.filter { it.mainLine }
    val side = entries.filter { !it.mainLine }
    return if (main.size >= 2) {
        listOfNotNull("Seasons" to main, side.takeIf { it.isNotEmpty() }?.let { "Movies, OVAs & specials" to it })
    } else listOf("Related" to entries)
}

/** Phone detail: franchise rows. Tapping another entry switches the page to it. */
@Composable
fun PhoneFranchiseSection(entries: List<FranchiseEntry>, currentId: Int, horizontalPadding: Dp, onSelect: (Int) -> Unit) {
    franchiseRows(entries).forEach { (title, items) ->
        Spacer(Modifier.height(28.dp))
        Text(
            text = title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )
        Spacer(Modifier.height(12.dp))
        val start = items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0))
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { _, e ->
                val current = e.id == currentId
                Column(
                    Modifier
                        .width(112.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !current) { onSelect(e.id) }
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard)
                            .border(if (current) 2.dp else 0.dp, if (current) PrimaryAccentLight else Color.Transparent, RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = e.coverImage, contentDescription = e.title,
                            contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()
                        )
                        val badge = if (current) "NOW VIEWING" else e.relationLabel?.uppercase()
                        if (badge != null) {
                            Text(
                                badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .background(
                                        if (current) Brush.horizontalGradient(listOf(PrimaryAccent, PrimaryAccentLight))
                                        else Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.75f))),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        e.label, color = if (current) PrimaryAccentLight else TextPrimary, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(e.meta, color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}
