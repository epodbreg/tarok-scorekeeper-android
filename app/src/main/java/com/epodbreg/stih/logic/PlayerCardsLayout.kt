package com.epodbreg.stih.logic

import com.epodbreg.stih.model.Player

/**
 * How the game screen groups player cards into rows. Port of PlayerCardsLayout.swift:
 * full-width cards take a row of their own, the rest go two per row.
 */
object PlayerCardsLayout {

    data class Row(val indices: List<Int>, val isFull: Boolean)

    /** Cards in order: a full-width card closes the current pair and takes its own row */
    fun rows(fullWidth: List<Boolean>): List<Row> {
        val rows = mutableListOf<Row>()
        val pending = mutableListOf<Int>()
        fun flush() {
            if (pending.isNotEmpty()) rows += Row(pending.toList(), isFull = false)
            pending.clear()
        }
        fullWidth.forEachIndexed { index, isFull ->
            if (isFull) {
                flush()
                rows += Row(listOf(index), isFull = true)
            } else {
                pending += index
                if (pending.size == 2) flush()
            }
        }
        flush()
        return rows
    }

    /**
     * The compact screen: the sole leader full width (none when the top is tied), everyone else
     * two per row in [order]. An opened half card keeps its place and its details open under
     * its row, so the pairs never break.
     */
    fun <P> compactSlots(order: List<P>, soleLeader: P?, opened: Set<P>): List<PlayerCardSlot<P>> {
        val slots = mutableListOf<PlayerCardSlot<P>>()
        val pair = mutableListOf<P>()
        fun closePair() {
            pair.forEach { slots += PlayerCardSlot(it, PlayerCardSlot.Kind.Card(fullWidth = false)) }
            pair.forEachIndexed { position, player ->
                if (player in opened) {
                    slots += PlayerCardSlot(player, PlayerCardSlot.Kind.Details(underLeftCard = position == 0))
                }
            }
            pair.clear()
        }
        for (player in order) {
            if (player == soleLeader) {
                closePair()
                slots += PlayerCardSlot(player, PlayerCardSlot.Kind.Card(fullWidth = true))
            } else {
                pair += player
                if (pair.size == 2) closePair()
            }
        }
        closePair()
        return slots
    }

    /** Every card full width, one per row (the non-compact screen) */
    fun <P> fullSlots(order: List<P>): List<PlayerCardSlot<P>> =
        order.map { PlayerCardSlot(it, PlayerCardSlot.Kind.Card(fullWidth = true)) }
}

/** One item on the game screen: a player's card, or the details panel of an opened half card */
data class PlayerCardSlot<P>(val player: P, val kind: Kind) {
    sealed interface Kind {
        data class Card(val fullWidth: Boolean) : Kind
        /** [underLeftCard]: which card of the row above it belongs to */
        data class Details(val underLeftCard: Boolean) : Kind
    }

    /** Takes a whole row: a full card or a details panel */
    val isFullWidth: Boolean get() = (kind as? Kind.Card)?.fullWidth ?: true

    /** The same for a card whether it's half or full width, so it moves instead of being replaced */
    val id: Pair<P, Boolean> get() = player to (kind is Kind.Details)
}

/** The order of the player cards */
object Standings {
    /** Best score first, ties in seat order. Equal scores share a place: 0, 0, 2. */
    fun of(players: List<Player>): List<Pair<Player, Int>> {
        val ordered = players.withIndex()
            .sortedWith(compareByDescending<IndexedValue<Player>> { it.value.score }.thenBy { it.index })
            .map { it.value }
        return ordered.map { player -> player to ordered.indexOfFirst { it.score == player.score } }
    }

    /** The player alone in first place; null while the top is tied (e.g. everyone on 0) */
    fun <P> soleLeader(standings: List<Pair<P, Int>>): P? =
        standings.filter { it.second == 0 }.singleOrNull()?.first
}
