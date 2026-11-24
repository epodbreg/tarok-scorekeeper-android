package com.epodbreg.stih.logic

import com.epodbreg.stih.ui.layout.FoldInfo
import com.epodbreg.stih.ui.layout.GameSplit
import com.epodbreg.stih.ui.layout.WideLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the game screen splits, and which way */
class WideLayoutTest {
    private fun fold(vertical: Boolean, half: Boolean = false) = FoldInfo(vertical, half, 0, 0)

    @Test fun phoneNeverSplits() {
        assertEquals(GameSplit.NONE, WideLayout.decide(411, 914, null))
        assertEquals(GameSplit.NONE, WideLayout.decide(914, 411, null))   // on its side
    }

    @Test fun flipPhoneHalfOpenStaysOneColumn() {
        assertEquals(GameSplit.NONE, WideLayout.decide(412, 750, fold(vertical = false, half = true)))
    }

    @Test fun bookFoldSplitsAlongTheFold() {
        assertEquals(GameSplit.SIDE_BY_SIDE, WideLayout.decide(841, 701, fold(vertical = true)))
        // Turned so the fold runs across, or half-folded like a laptop: players on top
        assertEquals(GameSplit.STACKED, WideLayout.decide(701, 841, fold(vertical = false, half = true)))
    }

    @Test fun tabletSplitsAcrossTheLongSide() {
        assertEquals(GameSplit.SIDE_BY_SIDE, WideLayout.decide(1280, 800, null))
        assertEquals(GameSplit.STACKED, WideLayout.decide(800, 1280, null))
    }

    @Test fun halfOfAWideScreenKeepsThePhoneLayout() {
        assertEquals(GameSplit.NONE, WideLayout.decide(420, 701, fold(vertical = true)))
    }
}
