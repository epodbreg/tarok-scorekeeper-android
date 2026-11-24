package com.epodbreg.stih.ui.layout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

/**
 * Wide screens (foldables unfolded, tablets): the game screen splits in two, players in one half
 * and game entry in the other. Port of the split in MainGameView.swift.
 */
enum class GameSplit { NONE, SIDE_BY_SIDE, STACKED }

/** Where the fold is, in window pixels */
data class FoldInfo(
    /** The fold runs top to bottom (halves left and right) */
    val vertical: Boolean,
    val halfOpened: Boolean,
    val topPx: Int,
    val bottomPx: Int,
)

object WideLayout {
    /** Both sides at least this long: big enough to split (not a phone on its side) */
    const val MIN_SPLIT_SIDE_DP = 600

    /**
     * Split along the fold when there is one; otherwise across the middle of the long side.
     * A fold that runs across means players on top and game entry on the bottom half
     * (also half-folded on a table).
     */
    fun decide(widthDp: Int, heightDp: Int, fold: FoldInfo?): GameSplit {
        if (minOf(widthDp, heightDp) < MIN_SPLIT_SIDE_DP) return GameSplit.NONE
        if (fold != null) return if (fold.vertical) GameSplit.SIDE_BY_SIDE else GameSplit.STACKED
        return if (widthDp > heightDp) GameSplit.SIDE_BY_SIDE else GameSplit.STACKED
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The device's fold, or null on a phone or tablet without one */
@Composable
fun rememberFoldInfo(): FoldInfo? {
    val activity = LocalContext.current.findActivity() ?: return null
    val flow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val layout: WindowLayoutInfo? by flow.collectAsState(initial = null)
    val fold = layout?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull() ?: return null
    return FoldInfo(
        vertical = fold.orientation == FoldingFeature.Orientation.VERTICAL,
        halfOpened = fold.state == FoldingFeature.State.HALF_OPENED,
        topPx = fold.bounds.top,
        bottomPx = fold.bounds.bottom,
    )
}
