package com.personalfitnesscoach.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.PainForm
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.PainKind

/** The pain questions (SAF-003), shared by the check-in (T2) and "Something hurts" (A4). */
@Composable
fun PainFields(f: PainForm, change: ((PainForm) -> PainForm) -> Unit) {
    SectionTitle(stringResource(R.string.pain_where))
    ChipGroup(Joint.entries, { it == f.region }, { stringResource(Labels.joint(it)) }, { j -> change { it.copy(region = j) } })
    CheckRow(stringResource(R.string.pain_whole_body), f.wholeBody, { on -> change { it.copy(wholeBody = on) } })
    SectionTitle(stringResource(R.string.pain_kind_q))
    PainKind.entries.forEach { k -> RadioRow(stringResource(Labels.painKind(k)), f.kind == k, { change { it.copy(kind = k) } }) }
    Stepper(stringResource(R.string.pain_rating, f.rating), { change { it.copy(rating = (it.rating - 1).coerceAtLeast(0)) } },
        { change { it.copy(rating = (it.rating + 1).coerceAtMost(10)) } }, minusEnabled = f.rating > 0, plusEnabled = f.rating < 10)
    CheckRow(stringResource(R.string.pain_worsening), f.worsening, { on -> change { it.copy(worsening = on) } })
    SectionTitle(stringResource(R.string.pain_descriptors))
    P.SAF_003.stop_region_descriptors.forEach { d ->
        val label = Labels.PAIN_DESCRIPTORS[d]?.let { stringResource(it) } ?: Labels.fallback(d)
        CheckRow(label, d in f.descriptors, { on -> change { it.copy(descriptors = if (on) it.descriptors + d else it.descriptors - d) } })
    }
}
