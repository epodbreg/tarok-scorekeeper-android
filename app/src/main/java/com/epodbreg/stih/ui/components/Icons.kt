package com.epodbreg.stih.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Filter1
import androidx.compose.material.icons.filled.Filter2
import androidx.compose.material.icons.filled.Filter3
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Looks3
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.outlined.BackHand
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.epodbreg.stih.model.BonusType
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.ui.theme.StihBlue
import com.epodbreg.stih.ui.theme.StihIndigo
import com.epodbreg.stih.ui.theme.StihOrange
import com.epodbreg.stih.ui.theme.StihPurple
import com.epodbreg.stih.ui.theme.StihTeal

/** Material icon equivalents of the SF Symbols used on iOS. */
val TarokContract.icon: ImageVector
    get() = when (this) {
        TarokContract.KLOP -> Icons.Filled.BugReport
        TarokContract.TRI -> Icons.Filled.Looks3
        TarokContract.DVE -> Icons.Filled.LooksTwo
        TarokContract.ENA -> Icons.Filled.LooksOne
        TarokContract.SOLO_TRI -> Icons.Filled.Filter3
        TarokContract.SOLO_DVE -> Icons.Filled.Filter2
        TarokContract.SOLO_ENA -> Icons.Filled.Filter1
        TarokContract.BERAC -> Icons.Filled.BackHand
        TarokContract.SOLO_BREZ -> Icons.Filled.Layers
        TarokContract.ODPRTI_BERAC -> Icons.Outlined.BackHand
        TarokContract.BARVNI_VALAT -> Icons.Filled.Palette
        TarokContract.VALAT -> Icons.Filled.AutoAwesome
        TarokContract.PENALTY_MOND -> Icons.Filled.NightsStay
        TarokContract.PENALTY_RENONS -> Icons.Filled.RemoveCircle
        TarokContract.PAGAT_BONUS -> Icons.Filled.AutoFixHigh
    }

val BonusType.icon: ImageVector
    get() = when (this) {
        BonusType.TRULA -> Icons.Filled.Layers
        BonusType.KRALJI -> Icons.Outlined.WorkspacePremium
        BonusType.KRALJ_ULTIMO -> Icons.Filled.WorkspacePremium
        BonusType.PAGAT_ULTIMO -> Icons.Filled.AutoFixHigh
        BonusType.MOND_KAZ -> Icons.Filled.Bedtime
    }

val BonusType.displayColor: Color
    get() = when (this) {
        BonusType.TRULA -> StihOrange
        BonusType.KRALJI -> StihTeal
        BonusType.KRALJ_ULTIMO -> StihPurple
        BonusType.PAGAT_ULTIMO -> StihBlue
        BonusType.MOND_KAZ -> StihIndigo
    }

/** Kontra escalation styling: kontra → re → sub → mort. */
data class KontraStyle(val name: String, val icon: ImageVector, val color: Color) {
    companion object {
        fun forLevel(level: Int): KontraStyle? = when (level) {
            1 -> KontraStyle("Kontra", Icons.Filled.Bolt, StihOrange)
            2 -> KontraStyle("Re", Icons.Filled.LocalFireDepartment, Color(0xFFFF3B30))
            3 -> KontraStyle("Sub", Icons.Filled.LocalFireDepartment, Color(0xFF8C081A))
            4 -> KontraStyle("Mort", Icons.Filled.Exposure, Color(0xFF111111))
            else -> null
        }
    }
}
