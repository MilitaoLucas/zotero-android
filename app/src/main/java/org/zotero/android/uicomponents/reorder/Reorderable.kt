package org.zotero.android.uicomponents.reorder

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.consumeDownChange
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

@Composable
fun rememberReorderState(
    listState: LazyListState = rememberLazyListState(),
) = remember { ReorderableState(listState) }

class ReorderableState(val listState: LazyListState) {
    var draggedIndex by mutableStateOf<Int?>(null)

    internal val ch = Channel<StartDrag>()

    @Suppress("MemberVisibilityCanBePrivate")
    val draggedOffset by derivedStateOf {
        draggedIndex
            ?.let { listState.layoutInfo.itemInfoByIndex(it) }
            ?.let { (selected?.offset?.toFloat() ?: 0f) + movedDist - it.offset }
    }

    fun offsetByIndex(index: Int) =
        if (draggedIndex == index) draggedOffset else null

    internal var selected by mutableStateOf<LazyListItemInfo?>(null)
    internal var movedDist by mutableFloatStateOf(0f)
}

fun Modifier.reorderable(
    state: ReorderableState,
    onMove: (from: Int, to: Int) -> (Unit),
    canDragOver: ((index: Int, dragDirection: DragDirection) -> Boolean)? = null,
    onDragStart: ((index: Int) -> (Unit))? = null,
    onDragEnd: ((startIndex: Int, endIndex: Int) -> (Unit))? = null,
    maxScrollPerFrame: Dp = 20.dp,
) = composed {
    val job: MutableState<Job?> = remember { mutableStateOf(null) }
    val maxScroll = with(LocalDensity.current) { maxScrollPerFrame.toPx() }
    val logic = remember {
        ReorderLogic(
            state = state,
            onMove = onMove,
            canDragOver = canDragOver,
            onDragStart = onDragStart,
            onDragEnd = onDragEnd
        )
    }
    val scope = rememberCoroutineScope()
    val interactions = remember { MutableSharedFlow<ReorderAction>(extraBufferCapacity = 16) }
    val vibrator = LocalContext.current.getSystemService(Vibrator::class.java)
    fun cancelAutoScroll() {
        job.value = job.value?.let {
            it.cancel()
            null
        }
    }
    LaunchedEffect(state) {
        merge(
            interactions,
            snapshotFlow { state.listState.layoutInfo }
                .distinctUntilChanged { old, new ->
                    old.visibleItemsInfo.firstOrNull()?.key == new.visibleItemsInfo.firstOrNull()?.key &&
                        old.visibleItemsInfo.lastOrNull()?.key == new.visibleItemsInfo.lastOrNull()?.key
                }
                .map { ReorderAction.Drag(0f) }
        )
            .collect { event ->
                when (event) {
                    is ReorderAction.End -> {
                        cancelAutoScroll()
                        logic.endDrag()
                    }
                    is ReorderAction.Start -> {
                        if (Build.VERSION.SDK_INT >= 26) {
                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            vibrator.vibrate(50)
                        }
                        logic.startDrag(event.key)
                    }
                    is ReorderAction.Drag -> {
                        if (logic.dragBy(event.amount) && job.value?.isActive != true) {
                            val scrollOffset = logic.calcAutoScrollOffset(0, maxScroll)
                            if (scrollOffset != 0f) {
                                job.value =
                                    scope.launch {
                                        var scroll = scrollOffset
                                        var start = 0L
                                        while (scroll != 0f && job.value?.isActive == true) {
                                            withFrameMillis {
                                                if (start == 0L) {
                                                    start = it
                                                } else {
                                                    scroll = logic.calcAutoScrollOffset(it - start, maxScroll)
                                                }
                                            }
                                            if (logic.scrollBy(scroll) != scroll) {
                                                scroll = 0f
                                            }
                                        }
                                    }
                            } else {
                                cancelAutoScroll()
                            }
                        }
                    }
                }
            }
    }

    Modifier.pointerInput(Unit) {
        forEachGesture {
            val dragStart = state.ch.receive()
            val down = awaitPointerEventScope {
                currentEvent.changes.fastFirstOrNull { it.id == dragStart.id }
            }
            val item = down?.position?.let { position ->
                val off = state.listState.layoutInfo.viewportStartOffset + position.y.toInt()
                state.listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { off in it.offset..(it.offset + it.size) }
            }
            if (down != null && item != null) {
                interactions.tryEmit(ReorderAction.Start(item.key))
                dragStart.offset?.also {
                    interactions.tryEmit(ReorderAction.Drag(it.y))
                }
                detectDrag(
                    down.id,
                    onDragEnd = { interactions.tryEmit(ReorderAction.End) },
                    onDragCancel = { interactions.tryEmit(ReorderAction.End) },
                    onDrag = { change, dragAmount ->
                        change.consumeAllChanges()
                        interactions.tryEmit(ReorderAction.Drag(dragAmount.y))
                    }
                )
            }
        }
    }
}

private suspend fun PointerInputScope.detectDrag(
    down: PointerId,
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitPointerEventScope {
        if (
            drag(down) {
                onDrag(it, it.positionChange())
                it.consumePositionChange()
            }
        ) {
            // consume up if we quit drag gracefully with the up
            currentEvent.changes.forEach {
                if (it.changedToUp()) {
                    it.consumeDownChange()
                }
            }
            onDragEnd()
        } else {
            onDragCancel()
        }
    }
}

private sealed class ReorderAction {
    class Start(val key: Any) : ReorderAction()
    class Drag(val amount: Float) : ReorderAction()
    object End : ReorderAction()
}

internal data class StartDrag(val id: PointerId, val offset: Offset? = null)
