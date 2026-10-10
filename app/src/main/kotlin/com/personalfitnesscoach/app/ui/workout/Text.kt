package com.personalfitnesscoach.app.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.ui.kg
import com.personalfitnesscoach.app.ui.minutes
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.data.core.player.Change
import com.personalfitnesscoach.data.core.session.ConditioningItem
import com.personalfitnesscoach.data.core.store.SetRow
import com.personalfitnesscoach.engine.conditioning.CircuitPlan
import com.personalfitnesscoach.engine.conditioning.Interval
import com.personalfitnesscoach.engine.library.GeneratedLibrary
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Zone

/** "8 to 10 reps", "30 s", with "each side" when it applies. */
@Composable
fun doseText(reps: IntRange, unit: DoseUnit, perSide: Boolean): String {
    val base = when {
        unit == DoseUnit.SECONDS && reps.first == reps.last -> stringResource(R.string.secs_exact, reps.first)
        unit == DoseUnit.SECONDS -> stringResource(R.string.secs_range, reps.first, reps.last)
        reps.first == reps.last -> stringResource(R.string.reps_exact, reps.first)
        else -> stringResource(R.string.reps_range, reps.first, reps.last)
    }
    return if (perSide) base + ", " + stringResource(R.string.each_side) else base
}

fun modalityName(m: Modality): String = GeneratedLibrary.modalities.firstOrNull { it.modality == m }?.name ?: Labels.fallback(m.name.lowercase())

fun exerciseName(id: String): String = Library[id]?.name ?: Library.drill(id)?.name ?: Labels.fallback(id)

/** One conditioning block as planned (preview). */
@Composable
fun conditioningLine(modality: Modality, zone: Zone, workMinutes: Double, interval: Interval?, circuit: CircuitPlan?): String = when {
    circuit != null -> stringResource(R.string.cond_circuit, modalityName(modality), circuit.rounds, circuit.moves.size, circuit.workSec, circuit.restSec)
    interval != null -> stringResource(R.string.cond_interval, modalityName(modality), interval.reps, interval.workSec, interval.restSec, interval.cr10.first,
        interval.cr10.last)
    else -> stringResource(R.string.cond_steady, modalityName(modality), minutes(workMinutes), stringResource(Labels.zone(zone)))
}

/** One conditioning block as stored in the workout. */
@Composable
fun conditioningLine(c: ConditioningItem): String {
    val i = c.interval
    val k = c.circuit
    return when {
        k != null -> stringResource(R.string.cond_circuit, modalityName(c.modality), k.rounds, k.moves.size, k.workSec, k.restSec)
        i != null -> stringResource(R.string.cond_interval, modalityName(c.modality), i.reps, i.workSec, i.restSec, i.cr10.first, i.cr10.last)
        else -> stringResource(R.string.cond_steady, modalityName(c.modality), minutes(c.workMinutes), stringResource(Labels.zone(c.zone)))
    }
}

/** "60 kg × 8" (or "× 30 s"), for "last time" and set lists. */
@Composable
fun setText(s: SetRow): String {
    val load = s.loadKg?.let { stringResource(R.string.unit_kg, kg(it)) } ?: stringResource(R.string.bodyweight)
    val amount = s.reps?.toString() ?: s.seconds?.let { stringResource(R.string.unit_sec, it) } ?: "-"
    return stringResource(R.string.wk_set_summary, load, amount)
}

/** A "what moved where" line (FS-6, FS-7). */
@Composable
fun changeText(c: Change, conditioning: List<ConditioningItem>): String = when (c) {
    is Change.Skipped -> stringResource(R.string.change_skipped, exerciseName(c.exerciseId))
    is Change.SetsReduced -> stringResource(R.string.change_sets, exerciseName(c.exerciseId), c.from, c.to)
    is Change.Swapped -> stringResource(R.string.change_swapped, exerciseName(c.from), exerciseName(c.to))
    is Change.LoadLowered -> stringResource(R.string.change_load, exerciseName(c.exerciseId), kg(c.from), kg(c.to))
    is Change.ConditioningShortened -> stringResource(R.string.change_cond_short, conditioning.getOrNull(c.index)?.let { modalityName(it.modality) } ?: "-",
        minutes(c.toMinutes))
    is Change.ConditioningDropped -> stringResource(R.string.change_cond_dropped, conditioning.getOrNull(c.index)?.let { modalityName(it.modality) } ?: "-")
    is Change.Added -> stringResource(R.string.change_added, exerciseName(c.exerciseId))
}
