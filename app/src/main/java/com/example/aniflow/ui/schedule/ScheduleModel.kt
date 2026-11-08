package com.example.aniflow.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.aniflow.data.model.AiringAnime
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/** Shared by the TV and phone schedule screens so both read the week the same way. */

/** An episode counts as "live" for half an hour after it drops. */
const val LIVE_WINDOW_S = 30 * 60L

val LivePink = Color(0xFFFF7AA8)
val LiveDot = Color(0xFFFF4D8D)
val OutGreen = Color(0xFF7BE0B0)

data class ScheduleDay(val key: Int, val weekday: String, val date: Int, val isToday: Boolean, val items: List<AiringAnime>)

/** Local calendar day key: comparable, and stable across DST changes. */
fun dayKey(epochSec: Long, cal: Calendar = Calendar.getInstance()): Int {
    cal.timeInMillis = epochSec * 1000L
    return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}

/** Yesterday through five days ahead: yesterday's late-night drops are what people look for. */
fun buildScheduleDays(schedule: List<AiringAnime>): List<ScheduleDay> {
    val cal = Calendar.getInstance()
    val byDay = schedule.groupBy { dayKey(it.airingAt, cal) }
    return (-1..5).map { offset ->
        cal.timeInMillis = System.currentTimeMillis()
        cal.add(Calendar.DAY_OF_YEAR, offset)
        val key = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        ScheduleDay(
            key = key,
            weekday = if (offset == 0) "Today" else cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()).orEmpty(),
            date = cal.get(Calendar.DAY_OF_MONTH),
            isToday = offset == 0,
            items = byDay[key].orEmpty().sortedBy { it.airingAt },
        )
    }
}

fun countdownText(sec: Long): String {
    val m = (sec + 59) / 60
    return when {
        m < 60 -> "in ${m}m"
        m < 24 * 60 -> "in ${m / 60}h ${m % 60}m"
        else -> "in ${m / (24 * 60)}d ${(m / 60) % 24}h"
    }
}

/** Status label and tint for an episode relative to now. */
fun statusOf(airingAt: Long, nowSec: Long, upcomingTint: Color): Pair<String, Color> {
    val delta = airingAt - nowSec
    return when {
        delta > 0 -> countdownText(delta) to upcomingTint
        -delta < LIVE_WINDOW_S -> "LIVE · just out" to LivePink
        else -> "Out now" to OutGreen
    }
}

/** Index where the NOW divider goes in today's sorted list (size when everything has aired). */
fun nowSplit(items: List<AiringAnime>, nowSec: Long): Int =
    items.indexOfFirst { it.airingAt > nowSec }.let { if (it < 0) items.size else it }

/** Wall clock in epoch seconds, ticking every 30 s so countdowns stay honest. */
@Composable
fun rememberNowSec(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() / 1000 } }
    return now
}
