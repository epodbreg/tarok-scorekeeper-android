package com.epodbreg.stih.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.epodbreg.stih.logic.PlayerCardSlot
import com.epodbreg.stih.logic.PlayerCardsLayout
import kotlinx.coroutines.launch

/**
 * Game screen cards: full-width cards (and details panels) take a row of their own, the rest
 * go two per row with equal heights. Every card stays the same composable while it changes
 * size or place (keyed by [PlayerCardSlot.id]), so cards slide instead of being swapped.
 * Port of PlayerCardsLayout.swift.
 */
@Composable
fun <P> PlayerCardsGrid(
    slots: List<PlayerCardSlot<P>>,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 10.dp,
    verticalSpacing: Dp = 16.dp,
    /** Above a details panel, so it sits right under the card it belongs to */
    attachedSpacing: Dp = 8.dp,
    card: @Composable (PlayerCardSlot<P>) -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            slots.forEach { slot ->
                key(slot.id) {
                    Box(Modifier.animatePlacement()) { card(slot) }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = horizontalSpacing.roundToPx()
        val half = maxOf(0, (width - gap) / 2)
        val rows = PlayerCardsLayout.rows(slots.map { it.isFullWidth })
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        val heights = rows.map { row ->
            if (row.isFull) {
                val index = row.indices.first()
                measurables[index].measure(Constraints(minWidth = width, maxWidth = width))
                    .also { placeables[index] = it }.height
            } else {
                // Both cards of a pair get the taller one's height
                val height = row.indices.maxOf { measurables[it].maxIntrinsicHeight(half) }
                row.indices.forEach { placeables[it] = measurables[it].measure(Constraints.fixed(half, height)) }
                height
            }
        }
        val gaps = rows.mapIndexed { number, row ->
            when {
                number == 0 -> 0
                slots[row.indices.first()].kind is PlayerCardSlot.Kind.Details -> attachedSpacing.roundToPx()
                else -> verticalSpacing.roundToPx()
            }
        }
        layout(width, heights.sum() + gaps.sum()) {
            var y = 0
            rows.forEachIndexed { number, row ->
                y += gaps[number]
                row.indices.forEachIndexed { position, index ->
                    placeables[index]?.place(x = if (row.isFull) 0 else position * (half + gap), y = y)
                }
                y += heights[number]
            }
        }
    }
}

/** Slides to a new place instead of jumping (one spring for the whole change) */
private fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(IntOffset.Zero) }
    var animatable by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val anim = animatable ?: Animatable(target, IntOffset.VectorConverter).also { animatable = it }
            if (anim.targetValue != target) {
                scope.launch { anim.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 250f)) }
            }
            anim.value - target
        }
}
