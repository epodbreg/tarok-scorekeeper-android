package com.epodbreg.stih.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epodbreg.stih.ui.theme.StihBlue

private val UnusedColor = Color(0xFF8E8E93)

/**
 * Radlci on one line, never wrapping: every pip while they fit ([maxPips]), otherwise up to
 * [pipsPerGroup] pips per kind followed by "+N" in the same colour — ○○○ +5  ●●● +2 (unused
 * first, then used). Filled = used. Port of RadlcRow in RadlcDisplayView.swift.
 *
 * [onTap]: manual radlci (auto off). When grouped, tapping an unused pip uses the next one and
 * tapping a used one frees the last one.
 */
@Composable
fun RadlcRow(
    radlci: List<Boolean>,
    modifier: Modifier = Modifier,
    maxPips: Int = 10,
    size: Dp = 18.dp,
    pipsPerGroup: Int = 3,
    onTap: ((Int) -> Unit)? = null,
) {
    if (radlci.isEmpty()) return
    val unused = radlci.indices.filter { !radlci[it] }
    val used = radlci.indices.filter { radlci[it] }
    val description = "Radlci: ${unused.size} neuporabljenih, ${used.size} uporabljenih"

    if (radlci.size <= maxPips) {
        Row(
            modifier = modifier.semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(size * 0.25f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            radlci.forEachIndexed { index, isUsed ->
                Pip(isUsed, size, onTap?.let { tap -> { tap(index) } })
            }
        }
    } else {
        Row(
            modifier = modifier.semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(size * 0.6f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (unused.isNotEmpty()) {
                PipGroup(unused.size, isUsed = false, size, pipsPerGroup, onTap?.let { tap -> { tap(unused.first()) } })
            }
            if (used.isNotEmpty()) {
                PipGroup(used.size, isUsed = true, size, pipsPerGroup, onTap?.let { tap -> { tap(used.last()) } })
            }
        }
    }
}

@Composable
private fun PipGroup(count: Int, isUsed: Boolean, size: Dp, pipsPerGroup: Int, onTap: (() -> Unit)?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(size * 0.25f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(minOf(count, pipsPerGroup)) { Pip(isUsed, size, onTap) }
        if (count > pipsPerGroup) {
            Text(
                "+${count - pipsPerGroup}",
                fontSize = (size.value * 0.8f).sp,
                fontWeight = FontWeight.Bold,
                color = if (isUsed) StihBlue else UnusedColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun Pip(isUsed: Boolean, size: Dp, onTap: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            // Full-strength fill and a mid grey outline: readable at a glance in both themes
            .background(if (isUsed) StihBlue else Color.Transparent)
            .border(
                width = maxOf(1.5.dp, size / 9),
                color = if (isUsed) StihBlue else UnusedColor,
                shape = CircleShape,
            )
            .let { m -> if (onTap != null) m.clickable(onClick = onTap) else m },
    )
}
