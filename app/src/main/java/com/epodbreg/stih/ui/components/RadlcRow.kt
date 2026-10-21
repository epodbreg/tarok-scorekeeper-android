package com.epodbreg.stih.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.epodbreg.stih.ui.theme.StihBlue

private const val MAX_PER_ROW = 8

/**
 * Radlci display: circles, filled = used, outline = unused.
 * Port of EnhancedRadlcDisplayView.
 */
@Composable
fun RadlcRow(
    radlci: List<Boolean>,
    onTap: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (radlci.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        radlci.indices.chunked(MAX_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { index ->
                    val used = radlci[index]
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            // Full-strength fill and a mid gray outline: readable at a glance in both themes
                            .background(if (used) StihBlue else Color.Transparent)
                            .border(
                                width = 1.5.dp,
                                color = if (used) StihBlue else Color(0xFF8E8E93),
                                shape = CircleShape,
                            )
                            .let { m ->
                                if (onTap != null) m.clickable { onTap(index) } else m
                            }
                    )
                }
            }
        }
    }
}
