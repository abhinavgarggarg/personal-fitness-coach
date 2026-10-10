package com.personalfitnesscoach.app.ui.workout

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.ui.Body
import com.personalfitnesscoach.app.ui.Heading
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.kg
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.app.ui.text.ReasonTexts
import com.personalfitnesscoach.app.ui.text.Reasons
import com.personalfitnesscoach.app.ui.today.RpePicker
import com.personalfitnesscoach.data.core.player.PlayerView
import com.personalfitnesscoach.engine.program.SrpePrompt
import com.personalfitnesscoach.engine.safety.SafetyStop

/** R1: an unfinished workout was found when the app opened. Nothing logged was lost. */
@Composable
fun ResumeScreen(v: PlayerView) {
    val a = LocalActions.current
    ScreenColumn(stringResource(R.string.resume_title)) {
        Body(stringResource(R.string.resume_body, stringResource(Labels.template(v.workout.template))))
        PrimaryButton(stringResource(R.string.resume_continue), { a.run { resumeWorkout() } })
        SecondaryButton(stringResource(R.string.resume_end), { a.run { endInterrupted() } })
    }
}

/** A8: minutes and sets, new bests, what changed for next time, and the one question (session effort, LOAD-001/002). */
@Composable
fun DoneScreen(s: Screen.Done) {
    val a = LocalActions.current
    val sum = s.summary
    BackHandler { a.run { closeToToday() } }
    ScreenColumn(stringResource(R.string.done_title)) {
        Body(stringResource(R.string.done_minutes, Math.round(sum.minutes).toInt(), sum.hardSets))
        if (sum.records.isNotEmpty()) {
            SectionTitle(stringResource(R.string.done_records))
            sum.records.forEach { r -> Body(stringResource(R.string.done_record_line, exerciseName(r.exerciseId), kg(r.after))) }
        }
        val changes = Reasons.nextTime(sum.decisions.map { it.reason })
        if (changes.isNotEmpty()) {
            SectionTitle(stringResource(R.string.done_changes))
            changes.forEach { Note(stringResource(ReasonTexts.of(it))) }
        }
        when {
            s.rated -> InfoCard(stringResource(R.string.done_rated), Tone.GOOD)
            sum.ask == SrpePrompt.Ask.NOW -> {
                SectionTitle(stringResource(R.string.done_rate_q))
                RpePicker { rpe -> a.run { rateSummary(rpe) } }
            }
            sum.ask == SrpePrompt.Ask.NEVER -> Unit
            else -> Note(stringResource(R.string.done_rate_wait))
        }
        s.next?.let { n -> Body(stringResource(R.string.done_next, stringResource(Labels.weekday(n.weekday)), stringResource(Labels.template(n.template)))) }
        PrimaryButton(stringResource(R.string.done_back), { a.run { closeToToday() } })
    }
}

/** SAF-003 at the check-in: the pain ends training for today; what to do instead. */
@Composable
fun PainDayScreen(o: com.personalfitnesscoach.engine.safety.PainOutcome) {
    val a = LocalActions.current
    BackHandler { a.run { closeToToday() } }
    ScreenColumn(stringResource(R.string.pain_day_title)) {
        InfoCard(stringResource(Labels.painAction(o.action)), Tone.WARN) {
            androidx.compose.material3.Text(stringResource(R.string.pain_result_end))
            if (o.suggestProfessional) androidx.compose.material3.Text(stringResource(R.string.pain_result_professional))
        }
        PrimaryButton(stringResource(R.string.done_back), { a.run { closeToToday() } })
    }
}

/** A5 SAF-002: stop now; calm guidance and the emergency number. Training waits until the user confirms on Today. */
@Composable
fun StopScreen(stop: SafetyStop) {
    val a = LocalActions.current
    val context = LocalContext.current
    BackHandler { a.run { closeToToday() } }
    ScreenColumn(null) {
        Heading(stringResource(R.string.stop_title))
        val what = stop.symptoms.map { id -> Labels.RED_FLAGS[id]?.let { stringResource(it) } ?: Labels.fallback(id) }.joinToString(", ")
        InfoCard(stringResource(R.string.stop_body, what), Tone.ALERT) {
            androidx.compose.material3.Text(stringResource(R.string.stop_emergency, stop.emergencyNumber))
        }
        PrimaryButton(stringResource(R.string.stop_call, stop.emergencyNumber), {
            // Opens the phone's dialler with the number filled in; the user presses call (no permission needed).
            try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + stop.emergencyNumber)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (e: ActivityNotFoundException) { /* no dialler on this device: the number is shown above */ }
        })
        Body(stringResource(R.string.stop_after))
        SecondaryButton(stringResource(R.string.stop_ok), { a.run { closeToToday() } })
    }
}
