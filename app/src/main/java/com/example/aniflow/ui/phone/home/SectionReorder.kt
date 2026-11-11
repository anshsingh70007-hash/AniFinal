package com.example.aniflow.ui.phone.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.aniflow.ui.phone.motion.Haptics
import com.example.aniflow.ui.phone.motion.PhoneMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** LazyColumn key prefix for movable Home sections. Anything else (the banner) never moves. */
internal const val SEC = "sec:"

/**
 * Hold a section's title to lift it, drag to move it, let go to drop it (motion board tile 17).
 *
 * The lifted section follows the finger 1:1 (its top stays at lift-top + finger delta, in viewport
 * coordinates, whatever the list scrolls). It swaps with a neighbour once its centre passes the
 * neighbour's centre, which cannot oscillate: swapping back needs the centre to travel a full
 * neighbour height the other way. Near the top or bottom edge the list scrolls itself.
 */
@Stable
class SectionReorderState internal constructor(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val haptics: Haptics,
    private val topEdgePx: Float,
    private val bottomEdgePx: Float,
    private val maxScrollPerFramePx: Float,
    private val onSwap: (key: String, withKey: String) -> Unit,
    private val onDrop: () -> Unit,
) {
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var settlingKey by mutableStateOf<String?>(null)
        private set
    /** Tap-to-move mode (the grip): which section shows Move up / Move down. */
    var moveKey by mutableStateOf<String?>(null)

    private var dragged by mutableFloatStateOf(0f)
    private var liftTop = 0f
    private var grabY = 0f
    private var awaitIndex = -1
    private var autoScroll: Job? = null
    internal val settleOffset = Animatable(0f)

    private fun info(key: String): LazyListItemInfo? =
        list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == SEC + key }

    private fun LazyListItemInfo.sectionKey(): String? = (key as? String)?.takeIf { it.startsWith(SEC) }?.removePrefix(SEC)

    /** Translation of the lifted section from its current slot; read in the draw phase only. */
    val dragOffset: Float
        get() {
            val k = draggingKey ?: return 0f
            val i = info(k) ?: return 0f
            return liftTop + dragged - i.offset
        }

    internal fun lift(key: String, touchYInSection: Float): Boolean {
        if (draggingKey != null) return false
        val i = info(key) ?: return false
        moveKey = null
        liftTop = i.offset.toFloat()
        grabY = touchYInSection
        dragged = 0f
        awaitIndex = -1
        draggingKey = key
        haptics.longPress()
        autoScroll?.cancel()
        autoScroll = scope.launch { autoScrollLoop() }
        return true
    }

    internal fun drag(dy: Float) {
        if (draggingKey == null) return
        dragged += dy
        checkSwap()
    }

    internal fun drop() {
        val k = draggingKey ?: return
        val from = dragOffset
        autoScroll?.cancel()
        autoScroll = null
        draggingKey = null
        settlingKey = k
        haptics.gestureEnd()
        onDrop()
        scope.launch {
            settleOffset.snapTo(from)
            settleOffset.animateTo(0f, PhoneMotion.smooth())
            if (settlingKey == k) settlingKey = null
        }
    }

    private fun checkSwap() {
        val k = draggingKey ?: return
        val items = list.layoutInfo.visibleItemsInfo
        val idx = items.indexOfFirst { it.key == SEC + k }
        if (idx < 0) return
        val cur = items[idx]
        // The list hasn't laid out the last swap yet; wait for it so one move never swaps twice.
        if (cur.index == awaitIndex) return
        awaitIndex = -1
        val mid = liftTop + dragged + cur.size / 2f
        val next = items.getOrNull(idx + 1)
        val prev = items.getOrNull(idx - 1)
        val nextKey = next?.sectionKey()
        val prevKey = prev?.sectionKey()
        when {
            next != null && nextKey != null && mid > next.offset + next.size / 2f -> swap(cur, k, nextKey)
            prev != null && prevKey != null && mid < prev.offset + prev.size / 2f -> swap(cur, k, prevKey)
        }
    }

    private fun swap(cur: LazyListItemInfo, key: String, with: String) {
        awaitIndex = cur.index
        // LazyColumn keeps the first visible item's *key* pinned; when that item takes part in a
        // swap the whole list would jump. Pin by index instead for this one move.
        list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        onSwap(key, with)
        haptics.segmentTick()
    }

    /** Move mode: one slot up (-1) or down (+1) among the sections currently shown. */
    fun moveBy(key: String, dir: Int, order: List<String>) {
        val i = order.indexOf(key)
        val j = i + dir
        if (i < 0 || j !in order.indices) return
        list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        onSwap(key, order[j])
        haptics.segmentTick()
        onDrop()
    }

    private suspend fun autoScrollLoop() {
        while (scope.isActive && draggingKey != null) {
            withFrameNanos { }
            val vp = list.layoutInfo
            val height = (vp.viewportEndOffset - vp.viewportStartOffset).toFloat()
            val finger = liftTop + dragged + grabY - vp.viewportStartOffset
            val speed = when {
                finger < topEdgePx -> -maxScrollPerFramePx * ((topEdgePx - finger) / topEdgePx).coerceIn(0f, 1f)
                finger > height - bottomEdgePx -> maxScrollPerFramePx * ((finger - (height - bottomEdgePx)) / bottomEdgePx).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                val used = list.scrollBy(speed)
                if (used != 0f) checkSwap()
            }
        }
    }

    internal fun abandon(key: String) {
        if (draggingKey == key) drop()
    }
}

@Composable
fun rememberSectionReorderState(
    list: LazyListState,
    haptics: Haptics,
    bottomChromePx: Float,
    onSwap: (key: String, withKey: String) -> Unit,
    onDrop: () -> Unit,
): SectionReorderState {
    val scope = rememberCoroutineScope()
    val d = LocalDensity.current
    val swapRef = remember { arrayOf(onSwap) }.also { it[0] = onSwap }
    val dropRef = remember { arrayOf(onDrop) }.also { it[0] = onDrop }
    return remember(list, haptics) {
        with(d) {
            SectionReorderState(
                list, scope, haptics,
                topEdgePx = 96.dp.toPx(),
                bottomEdgePx = bottomChromePx + 96.dp.toPx(),
                maxScrollPerFramePx = 16.dp.toPx(),
                onSwap = { a, b -> swapRef[0](a, b) },
                onDrop = { dropRef[0]() },
            )
        }
    }
}



/**
 * The hold gesture, attached to a section header. Lifts after 450 ms of holding still; moving
 * more than the touch slop first (a scroll), lifting the finger, or anyone else consuming the
 * touch cancels it. Finger movement is measured in root coordinates, because the header itself
 * moves under the finger once lifted.
 */
@Composable
fun Modifier.liftToMove(key: String, state: SectionReorderState, pressing: MutableState<Boolean>): Modifier {
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { coords[0] = it }
        .pointerInput(key, state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressing.value = true
                var cancelled = false
                withTimeoutOrNull(LIFT_MS) {
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed || ch.isConsumed ||
                            (ch.position - down.position).getDistance() > viewConfiguration.touchSlop
                        ) {
                            cancelled = true
                            break
                        }
                    }
                }
                pressing.value = false
                if (cancelled) return@awaitEachGesture
                val c = coords[0] ?: return@awaitEachGesture
                if (!state.lift(key, down.position.y)) return@awaitEachGesture
                try {
                    var lastY = c.localToRoot(down.position).y
                    drag(down.id) { ch ->
                        val y = (coords[0] ?: c).localToRoot(ch.position).y
                        state.drag(y - lastY)
                        lastY = y
                        ch.consume()
                    }
                } finally {
                    state.abandon(key)
                }
            }
        }
}

private const val LIFT_MS = 450L

/** The saved order applied to the sections present now; new sections keep their default place. */
internal fun mergeOrder(saved: List<String>, defaults: List<String>): List<String> {
    if (saved.isEmpty()) return defaults
    val result = saved.filter { it in defaults }.distinct().toMutableList()
    defaults.forEachIndexed { i, k ->
        if (k !in result) {
            val pred = defaults.subList(0, i).lastOrNull { it in result }
            result.add(if (pred == null) 0 else result.indexOf(pred) + 1, k)
        }
    }
    return result
}

/** What to save: the new visible order, with sections not shown right now kept beside their neighbours. */
internal fun persistOrder(saved: List<String>, shown: List<String>): List<String> {
    val result = shown.toMutableList()
    saved.forEachIndexed { i, k ->
        if (k !in result) {
            val pred = saved.subList(0, i).lastOrNull { it in result }
            result.add(if (pred == null) 0 else result.indexOf(pred) + 1, k)
        }
    }
    return result
}

internal fun List<String>.swapped(a: String, b: String): List<String> {
    val i = indexOf(a)
    val j = indexOf(b)
    if (i < 0 || j < 0) return this
    return toMutableList().also { it[i] = b; it[j] = a }
}
