package com.epodbreg.stih.logic

import com.epodbreg.stih.model.BonusType
import com.epodbreg.stih.model.GameSession
import com.epodbreg.stih.model.HandOutcome
import com.epodbreg.stih.model.TarokContract
import com.epodbreg.stih.model.TarokStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** CSV export of a session — port of GameSession.csvExport() from SessionDetailView.swift. */
fun GameSession.csvExport(): String {
    val sorted = games.sortedBy { it.timestamp }
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun q(s: String) = "\"${s.replace("\"", "\"\"")}\""

    fun contractAbbr(c: TarokContract) = when (c) {
        TarokContract.VALAT -> "V"
        TarokContract.BARVNI_VALAT -> "BV"
        TarokContract.ODPRTI_BERAC -> "OB"
        TarokContract.BERAC -> "B"
        TarokContract.ENA -> "1"
        TarokContract.DVE -> "2"
        TarokContract.TRI -> "3"
        TarokContract.SOLO_ENA -> "S1"
        TarokContract.SOLO_DVE -> "S2"
        TarokContract.SOLO_TRI -> "S3"
        TarokContract.SOLO_BREZ -> "SB"
        TarokContract.PENALTY_MOND -> "MO"
        TarokContract.PAGAT_BONUS -> "PG"
        TarokContract.KLOP -> "K"
        TarokContract.PENALTY_RENONS -> "RE"
    }

    fun bonusAbbr(b: BonusType) = b.abbreviation

    fun outcomeAbbr(o: HandOutcome) = when (o) {
        HandOutcome.ZMAGAL -> "W"
        HandOutcome.ZMAGAL_SLEPAL -> "W*"
        HandOutcome.IZGUBIL -> "L"
        HandOutcome.IZGUBIL_SLEPAL -> "L*"
    }

    fun signed(v: Int) = if (v > 0) "+$v" else "$v"

    val lines = mutableListOf<String>()

    lines.add("LEGENDA")
    lines.add("V=Valat,BV=Barvni valat,OB=Odprti berač,B=Berač,1=Ena,2=Dve,3=Tri,S1=Solo ena,S2=Solo dve,S3=Solo tri,SB=Solo brez,MO=Izgubljen mond,PG=Pagat ultimo,K=Klop,RE=Renons")
    lines.add("Izid: W=zmaga  W*=zmaga(šlepal)  L=poraz  L*=poraz(šlepal)")
    lines.add("Bonusi: TR=Trula  KR=Kralji  KU=Kralj ultimo  PU=Pagat ultimo  MO=Mond kaz.")
    lines.add("")

    val dateFormatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    lines.add("Seja,${dateFormatter.format(Date(startTime))}")
    lines.add("Igralci,${players.joinToString(", ") { it.name }}")
    lines.add("Število rund,${sorted.count { !it.contract.isSideEntry }}")
    lines.add("")

    val playerNames = players.joinToString(",") { q(it.name) }
    lines.add("#,Čas,Kontrakt,Igralec_ka,Soigralec_ka,Izid,Točke,$playerNames,Bonusi")

    sorted.forEachIndexed { i, game ->
        val time = timeFormatter.format(Date(game.timestamp))
        val contract = contractAbbr(game.contract)
        val outcome = if (game.contract == TarokContract.KLOP) "" else outcomeAbbr(game.outcome)
        val partner = game.partnerName
            ?.takeUnless { it.isEmpty() || it == TarokStrings.ZARUFAL } ?: ""
        val bonusStr = game.bonuses.joinToString(" ") { bonusAbbr(it.type) }

        val playerDeltas = players.map { player ->
            when {
                game.contract == TarokContract.KLOP -> {
                    val v = game.klopScores?.get(player.name) ?: 0
                    if (v == 0) "0" else signed(v)
                }
                player.name == game.declarerName || player.name == partner ->
                    if (game.totalPoints == 0) "0" else signed(game.totalPoints)
                else -> ""
            }
        }

        val row = listOf(
            "${i + 1}",
            q(time),
            q(contract),
            q(if (game.declarerName == "-") "" else game.declarerName),
            q(partner),
            q(outcome),
            if (game.totalPoints == 0 && game.contract == TarokContract.KLOP) "" else signed(game.totalPoints),
        ) + playerDeltas + listOf(q(bonusStr))
        lines.add(row.joinToString(","))
    }

    lines.add("")
    val tally = players.joinToString(",") { signed(it.score) }
    lines.add("SKUPAJ,,,,,,,,$tally,")

    return lines.joinToString("\n")
}
