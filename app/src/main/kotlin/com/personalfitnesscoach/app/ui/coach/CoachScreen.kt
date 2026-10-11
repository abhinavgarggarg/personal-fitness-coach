package com.personalfitnesscoach.app.ui.coach

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.CoachModel
import com.personalfitnesscoach.app.flow.Tab
import com.personalfitnesscoach.app.ui.Body
import com.personalfitnesscoach.app.ui.CheckRow
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.SmallAction
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.minutes
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.app.ui.text.ReasonTexts
import com.personalfitnesscoach.app.ui.workout.exerciseName
import com.personalfitnesscoach.data.core.coach.CoachAction
import com.personalfitnesscoach.data.core.coach.CoachAnswer
import com.personalfitnesscoach.data.core.coach.Fact
import com.personalfitnesscoach.data.core.coach.GeminiShare
import com.personalfitnesscoach.data.core.coach.OutOfScope
import com.personalfitnesscoach.data.core.coach.Question
import com.personalfitnesscoach.data.core.coach.ShareContent
import com.personalfitnesscoach.data.core.coach.Why
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.registry.P

/**
 * C1, the Tier 1 coach (Phase 2 section 9; SAF-009, COACH-001): suggested questions, a typed question matched offline, answers built from
 * the decision log and the plan in fixed plain-language templates, "Why?" with each rule's title and confidence, and Ask Gemini (D-045).
 */
@Composable
fun CoachScreen(m: CoachModel) {
    val a = LocalActions.current
    BackHandler { a.run { when { m.share != null -> closeGeminiShare(); m.why != null -> closeWhy(); else -> closeCoach() } } }
    ScreenColumn(stringResource(R.string.coach_title)) {
        m.why?.let { WhyPanel(it); return@ScreenColumn }
        m.share?.let { SharePanel(it); return@ScreenColumn }
        m.warning?.let { WarningPanel(it); return@ScreenColumn }
        Note(stringResource(R.string.coach_intro))
        m.exerciseId?.let { Note(stringResource(R.string.coach_about, exerciseName(it))) }
        OutlinedTextField(
            value = m.text, onValueChange = { t -> a.edit { editCoachText(t) } }, label = { Text(stringResource(R.string.coach_field)) },
            singleLine = false, maxLines = 4, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { a.run { askCoach() } }),
        )
        PrimaryButton(stringResource(R.string.coach_ask), { a.run { askCoach() } }, enabled = m.text.isNotBlank())
        m.answer?.let { AnswerCard(it) }
        SectionTitle(stringResource(R.string.coach_suggested))
        Question.SUGGESTED.forEach { q -> SmallAction(stringResource(Labels.question(q)), { a.run { askSuggested(q) } }) }
        SecondaryButton(stringResource(R.string.gemini_ask), { a.run { prepareGeminiShare() } })
        if (m.back != null) SecondaryButton(stringResource(R.string.action_back), { a.run { closeCoach() } })
    }
}

@Composable
private fun AnswerCard(ans: CoachAnswer) {
    val a = LocalActions.current
    val tone = when (ans.outOfScope) {
        OutOfScope.URGENT_SYMPTOMS -> Tone.ALERT
        null -> Tone.NEUTRAL
        else -> Tone.WARN
    }
    val title = ans.question?.let { stringResource(Labels.question(it)) }
        ?: ans.outOfScope?.let { o -> if (o == OutOfScope.URGENT_SYMPTOMS) stringResource(R.string.oos_urgent, P.SAF_002.emergency_number_default)
            else stringResource(Labels.outOfScope(o)) }
        ?: stringResource(R.string.coach_unknown)
    InfoCard(title, tone) {
        ans.question?.let { q -> ans.facts.forEach { FactText(q, it) } }
        ans.suggestions.forEach { q -> SmallAction(stringResource(Labels.question(q)), { a.run { askSuggested(q) } }) }
        ans.actions.forEach { act -> ActionButton(act) }
        if (ans.rules.isNotEmpty()) {
            Note(stringResource(R.string.coach_rules, ans.rules.joinToString(", ")))
            SmallAction(stringResource(R.string.action_why), { a.run { openWhy(ans.question?.name ?: ans.outOfScope?.name ?: "", ans.rules) } })
        }
    }
}

@Composable
private fun FactText(q: Question, f: Fact) {
    when (f) {
        is Fact.ExerciseRole -> {
            val name = exerciseName(f.exerciseId)
            Body(stringResource(when {
                f.main || f.role == SlotRole.MAIN -> R.string.coach_role_main
                f.role != null -> R.string.coach_role_accessory
                else -> R.string.coach_role_other
            }, name))
            Body(stringResource(R.string.coach_role_pattern, stringResource(Labels.pattern(f.pattern))))
            if (f.coreLift) Body(stringResource(R.string.coach_role_core))
            if (f.favourite) Body(stringResource(R.string.coach_role_favourite))
        }
        is Fact.Trains -> {
            Body(stringResource(R.string.coach_trains, exerciseName(f.exerciseId), f.primary.map { stringResource(Labels.muscle(it)) }.joinToString(", ")))
            if (f.secondary.isNotEmpty()) Body(stringResource(R.string.coach_trains_helpers, f.secondary.map { stringResource(Labels.muscle(it)) }.joinToString(", ")))
            Body(stringResource(R.string.coach_trains_pattern, stringResource(Labels.pattern(f.pattern))))
        }
        is Fact.Replacements -> {
            Body(stringResource(R.string.coach_replace_yes, exerciseName(f.exerciseId)))
            if (f.options.isEmpty()) Body(stringResource(R.string.coach_replace_none))
            else Body(stringResource(R.string.coach_replace_options, f.options.joinToString(", ") { exerciseName(it) }))
        }
        is Fact.TodayLevel -> {
            val tier = f.tier
            Body(if (f.checkedIn && tier != null) stringResource(R.string.coach_today_level, stringResource(Labels.tier(tier)))
                else stringResource(R.string.coach_today_no_checkin))
        }
        is Fact.Decisions -> {
            val a = LocalActions.current
            if (f.entries.isEmpty()) {
                if (q == Question.WHY_TODAY_DIFFERENT) Body(stringResource(R.string.coach_today_plain))
            } else {
                Body(stringResource(R.string.coach_changes))
                f.entries.forEach { e ->
                    Body("• " + stringResource(ReasonTexts.of(e.reason)))
                    SmallAction(stringResource(R.string.action_why), { a.run { openWhy(e.reason, e.ruleIds) } })
                }
            }
        }
        is Fact.Minutes -> Body(stringResource(R.string.coach_minutes, f.minutes, f.usual))
        is Fact.Improved -> {
            Body(stringResource(R.string.coach_improved_sessions, f.sessionsThisMonth, f.sessionsLastMonth))
            val up = f.liftsUp.map { l ->
                stringResource(R.string.coach_lift_up, exerciseName(l.exerciseId), String.format(java.util.Locale.getDefault(), "%.0f", l.changePct)) }
            if (up.isNotEmpty()) Body(stringResource(R.string.coach_improved_up, up.joinToString(", ")))
            if (f.liftsFlat > 0) Body(stringResource(R.string.coach_improved_flat, f.liftsFlat))
            Body(stringResource(R.string.coach_improved_weeks, f.whoWeeks, f.streakWeeks))
        }
        is Fact.Focus -> {
            val block = f.block
            val left = f.weeksLeftInBlock
            if (block != null && left != null) Body(stringResource(R.string.coach_focus_block, stringResource(Labels.block(block)), left))
            f.next?.let { Body(stringResource(R.string.coach_focus_next, stringResource(Labels.weekday(it.weekday)), stringResource(Labels.template(it.template)))) }
            f.lastWeekMinutes?.let { Body(stringResource(R.string.coach_focus_minutes, minutes(it))) }
            f.stepTarget?.let { Body(stringResource(R.string.coach_focus_steps, java.text.NumberFormat.getIntegerInstance().format(it))) }
        }
        is Fact.Effort -> {
            Body(f.targetRir?.let { stringResource(R.string.coach_effort, minutes(it)) } ?: stringResource(R.string.coach_effort_general))
            if (f.beginnerPrompt) Body(stringResource(R.string.coach_effort_beginner))
        }
        is Fact.LighterWeek -> Body(when {
            f.deloadThisWeek -> stringResource(R.string.coach_lighter_deload)
            f.lighterSessionsLeft > 0 -> stringResource(R.string.coach_lighter_week, f.lighterSessionsLeft)
            else -> stringResource(R.string.coach_lighter_none)
        })
        Fact.NotYet -> Body(stringResource(when (q) {
            Question.WHY_LESS_VOLUME -> R.string.coach_volume_none
            Question.WHY_TARGET_CHANGED -> R.string.coach_target_none
            else -> R.string.coach_not_yet
        }))
    }
    if (q == Question.HARDER && f is Fact.TodayLevel) Body(stringResource(R.string.coach_harder))
    if (q == Question.TIRED_TODAY && f is Fact.TodayLevel) Body(stringResource(R.string.coach_tired))
}

@Composable
private fun ActionButton(act: CoachAction) {
    val a = LocalActions.current
    when (act) {
        is CoachAction.ShorterSession -> PrimaryButton(stringResource(R.string.coach_minutes_go, act.minutes), { a.run { coachAction(act) } })
        CoachAction.EasierLevel -> PrimaryButton(stringResource(R.string.coach_easier_go), { a.run { coachAction(act) } })
        CoachAction.OpenCheckIn -> PrimaryButton(stringResource(R.string.coach_checkin_go), { a.run { coachAction(act) } })
        CoachAction.OpenPreview -> SecondaryButton(stringResource(R.string.coach_preview_go), { a.run { coachAction(act) } })
        CoachAction.OpenProgress -> SecondaryButton(stringResource(R.string.coach_progress_go), { a.run { coachAction(act) } })
        CoachAction.OpenWarningSigns -> PrimaryButton(stringResource(R.string.oos_urgent_go), { a.run { coachAction(act) } })
        is CoachAction.AskAbout -> SmallAction(stringResource(R.string.coach_ask_about, exerciseName(act.exerciseId)), { a.run { coachAction(act) } })
    }
}

/** "Why?": the decision's plain line, then each rule with its title and confidence (Phase 2 section 9). */
@Composable
private fun WhyPanel(w: Why) {
    val a = LocalActions.current
    SectionTitle(stringResource(R.string.why_title))
    val known = com.personalfitnesscoach.engine.core.ReasonKey.entries.any { it.name == w.reason }
    if (known) Body(stringResource(ReasonTexts.of(w.reason)))
    w.rules.forEach { r ->
        Text(stringResource(R.string.why_rule, r.id, r.title ?: r.id), style = MaterialTheme.typography.titleMedium)
        r.confidence?.let { Note(stringResource(R.string.why_confidence, it)) }
    }
    SecondaryButton(stringResource(R.string.action_back), { a.run { closeWhy() } })
}

/** SAF-002 from the coach: tick what applies now; any sign stops training (and ends a workout in progress). */
@Composable
private fun WarningPanel(picked: Set<String>) {
    val a = LocalActions.current
    SectionTitle(stringResource(R.string.warn_title))
    Body(stringResource(R.string.warn_intro))
    P.SAF_002.symptoms.forEach { id ->
        val label = Labels.RED_FLAGS[id]?.let { stringResource(it) } ?: Labels.fallback(id)
        CheckRow(label, id in picked, { on -> a.edit { pickWarningSign(id, on) } })
    }
    PrimaryButton(stringResource(R.string.warn_go), { a.run { reportWarningSigns() } }, enabled = picked.isNotEmpty())
    SecondaryButton(stringResource(R.string.action_cancel), { a.run { openTab(Tab.COACH) } })
}

/** D-045: the exact text first, then the share sheet (straight to Gemini when it is installed). */
@Composable
private fun SharePanel(c: ShareContent) {
    val a = LocalActions.current
    val context = LocalContext.current
    val text = shareText(c)
    SectionTitle(stringResource(R.string.gemini_title))
    Note(stringResource(R.string.gemini_intro))
    InfoCard(text)
    val gemini = geminiInstalled(context)
    PrimaryButton(stringResource(if (gemini) R.string.gemini_share else R.string.gemini_share_any), { share(context, text, gemini) })
    SecondaryButton(stringResource(R.string.action_cancel), { a.run { closeGeminiShare() } })
}

@Composable
private fun shareText(c: ShareContent): String {
    val lines = ArrayList<String>()
    lines += stringResource(R.string.gemini_text_intro)
    val template = c.template
    val tier = c.tier
    if (template != null) {
        val name = stringResource(Labels.template(template))
        lines += if (tier != null) stringResource(R.string.gemini_text_today, name, c.minutes ?: 60, stringResource(Labels.tier(tier)))
            else stringResource(R.string.gemini_text_today_no_level, name, c.minutes ?: 60)
        if (c.exercises.isNotEmpty()) lines += stringResource(R.string.gemini_text_exercises, c.exercises.joinToString(", ") { exerciseName(it) })
    } else lines += stringResource(R.string.gemini_text_rest)
    val reasons = c.reasons.map { stringResource(ReasonTexts.of(it)) }
    if (reasons.isNotEmpty()) lines += stringResource(R.string.gemini_text_reasons, reasons.joinToString(" "))
    if (c.gentleMode) lines += stringResource(R.string.gemini_text_gentle)
    lines += if (c.question.isNotBlank()) stringResource(R.string.gemini_text_question, c.question) else stringResource(R.string.gemini_text_no_question)
    return lines.joinToString("\n\n")
}

private fun geminiInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(GeminiShare.GEMINI_PACKAGE, 0); true
} catch (e: Exception) { false }

/** ACTION_SEND with plain text: Gemini when installed, otherwise the share sheet. Nothing comes back into the app (D-045). */
private fun share(context: Context, text: String, gemini: Boolean) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        if (gemini) context.startActivity(Intent(send).setPackage(GeminiShare.GEMINI_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
