package com.personalfitnesscoach.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.SettingsFlow
import com.personalfitnesscoach.app.flow.SettingsModel
import com.personalfitnesscoach.app.flow.ScreeningQuestion
import com.personalfitnesscoach.app.ui.AnswerRow
import com.personalfitnesscoach.app.ui.Body
import com.personalfitnesscoach.app.ui.SmallAction
import com.personalfitnesscoach.app.ui.CheckRow
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.SwitchRow
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.data.android.StepSensor
import com.personalfitnesscoach.data.core.backup.BackupFormat
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.ScreeningMode
import java.text.DateFormat
import java.util.Date

/**
 * S1 settings for Part 5: step counting (D-062, D-078), backup with an optional password, restore and "erase all my data" (Phase 2
 * section 12). Files are written and read only where the user chooses (the system file picker; no storage permission).
 */
@Composable
fun SettingsScreen(m: SettingsModel) {
    val a = LocalActions.current
    val context = LocalContext.current
    var stepsDenied by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportPassword2 by remember { mutableStateOf("") }
    var safetyPassword by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var unreadable by remember { mutableStateOf(false) }
    BackHandler { a.run { closeSettings() } }

    val askSteps = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) a.run { setStepTracking(true) } else stepsDenied = true
    }
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val pw = exportPassword.toCharArray()
        exportPassword = ""
        exportPassword2 = ""
        a.run {
            val ok = try {
                val bytes = exportBytes(pw)
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
            } catch (e: Exception) { false } finally { pw.fill(' ') }
            if (ok) exported() else { failed = true; settingsFlow(null) }
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        a.run {
            val bytes = try { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
            if (bytes == null) { unreadable = true; settingsFlow(null) } else inspectBackup(bytes)
        }
    }
    var restoreToken by remember { mutableStateOf<Int?>(null) }
    val safetyTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val token = restoreToken
        if (uri == null || token == null) return@rememberLauncherForActivityResult
        val pw = safetyPassword.toCharArray()
        safetyPassword = ""
        a.run {
            try {
                restore(token, pw) { _, bytes -> context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("could not write the copy") }
            } finally { pw.fill(' ') }
        }
    }

    val askAlerts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> a.run { openSettings() } }

    ScreenColumn(stringResource(R.string.settings_title)) {
        // Phase 2 section 11: the rest timer can't buzz while locked without notifications (R5-21).
        if (m.alertsOff) InfoCard(stringResource(R.string.set_alerts_off), Tone.WARN) {
            SmallAction(stringResource(R.string.set_alerts_allow), {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) askAlerts.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }

        // ------------------------------------------------------------------ steps
        SectionTitle(stringResource(R.string.set_steps))
        if (!m.stepCounterAvailable) Note(stringResource(R.string.set_steps_unavailable))
        else {
            SwitchRow(stringResource(R.string.set_steps), m.stepTracking, { on ->
                if (!on) a.run { setStepTracking(false) }
                else if (context.checkSelfPermission(StepSensor.PERMISSION) == PackageManager.PERMISSION_GRANTED) a.run { setStepTracking(true) }
                else askSteps.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            })
            Note(stringResource(R.string.set_steps_body))
            if (stepsDenied) Note(stringResource(R.string.set_steps_denied))
        }

        // ------------------------------------------------------------------ health: conditions, screening, doctor's OK (SAF-001, SAF-010)
        SectionTitle(stringResource(R.string.set_conditions_title))
        Body(stringResource(R.string.set_conditions_body))
        SecondaryButton(stringResource(R.string.set_conditions_edit), { a.run { openConditionsEditor() } })
        if (m.screeningGuidance) InfoCard(stringResource(R.string.set_screening_guidance), Tone.WARN)
        val rescreen = m.flow as? SettingsFlow.Rescreen
        if (rescreen == null) SecondaryButton(stringResource(R.string.set_rescreen), { a.run { openRescreen() } })
        else {
            SectionTitle(stringResource(R.string.set_rescreen))
            ScreeningQuestion.entries.forEach { q ->
                AnswerRow(stringResource(Labels.screening(q)), rescreen.answers[q], { yes -> a.edit { answerRescreen(q, yes) } })
            }
            rescreen.result?.let { r ->
                InfoCard(stringResource(when {
                    r.clinicianGuidance -> R.string.screen_result_clinician
                    r.mode == ScreeningMode.CONSERVATIVE -> R.string.screen_result_conservative
                    r.mode == ScreeningMode.MODERATE_ONLY -> R.string.screen_result_moderate
                    else -> R.string.screen_result_standard
                }), if (r.mode == ScreeningMode.STANDARD) Tone.GOOD else Tone.WARN)
            }
            if (rescreen.result == null) {
                PrimaryButton(stringResource(R.string.set_rescreen_save), { a.run { submitRescreen() } },
                    enabled = rescreen.answers.size == ScreeningQuestion.entries.size)
            }
            SecondaryButton(stringResource(R.string.action_close), { a.run { settingsFlow(null) } })
        }
        if (m.screeningClearanceNeeded || m.screeningClearanceDay != null || m.clearances.isNotEmpty()) {
            SectionTitle(stringResource(R.string.set_clearance_title))
            if (m.screeningClearanceNeeded) {
                Body(stringResource(R.string.set_clearance_screening_body))
                PrimaryButton(stringResource(R.string.set_clearance_screening), { a.run { confirmScreeningClearance() } })
            }
            m.screeningClearanceDay?.let { Note(stringResource(R.string.set_clearance_screening_done, dateText(it))) }
            if (m.clearances.isNotEmpty()) Note(stringResource(R.string.set_clearance_condition_intro))
            m.clearances.forEach { cc ->
                Text(cc.name, style = MaterialTheme.typography.titleMedium)
                Note(stringResource(when (cc.rule) {
                    "suggest" -> R.string.onb_cond_clearance_suggest
                    "before_vigorous" -> R.string.onb_cond_clearance_before_vigorous
                    else -> R.string.onb_cond_clearance_always
                }))
                ClearanceScope.entries.forEach { sc ->
                    CheckRow(stringResource(Labels.clearance(sc)), sc in cc.confirmed, { on ->
                        a.run { setConditionClearance(cc.id, if (on) cc.confirmed + sc else cc.confirmed - sc) }
                    })
                }
            }
        }

        // ------------------------------------------------------------------ backup
        SectionTitle(stringResource(R.string.set_backup_title))
        Body(stringResource(R.string.set_backup_body))
        Note(m.lastExportDay?.let { stringResource(R.string.set_backup_last, dateText(it)) } ?: stringResource(R.string.set_backup_never))
        if (failed) InfoCard(stringResource(R.string.set_backup_failed), Tone.WARN)
        when (m.flow) {
            is SettingsFlow.Export -> {
                Note(stringResource(R.string.set_backup_health_note))
                PasswordField(stringResource(R.string.set_backup_password), exportPassword) { exportPassword = it }
                // A mistyped password would make the backup impossible to open: it is typed twice (R5-21).
                if (exportPassword.isNotEmpty()) PasswordField(stringResource(R.string.set_backup_password_again), exportPassword2) { exportPassword2 = it }
                val mismatch = exportPassword.isNotEmpty() && exportPassword != exportPassword2
                if (mismatch && exportPassword2.isNotEmpty()) Note(stringResource(R.string.set_backup_password_mismatch))
                Note(stringResource(R.string.set_backup_password_note))
                PrimaryButton(stringResource(R.string.set_backup_choose), { failed = false; exportTo.launch(BackupFormat.fileName(Days.of(java.time.LocalDate.now()))) },
                    enabled = !mismatch)
                SecondaryButton(stringResource(R.string.action_cancel), { exportPassword = ""; exportPassword2 = ""; a.run { settingsFlow(null) } })
            }
            SettingsFlow.Exported -> InfoCard(stringResource(R.string.set_backup_done), Tone.GOOD)
            else -> PrimaryButton(stringResource(R.string.set_backup_now), { failed = false; a.run { settingsFlow(SettingsFlow.Export()) } })
        }

        // ------------------------------------------------------------------ restore
        SectionTitle(stringResource(R.string.set_restore_title))
        Body(stringResource(R.string.set_restore_body))
        when (val f = m.flow) {
            is SettingsFlow.RestoreNeedsPassword -> InfoCard(stringResource(R.string.restore_password_title)) {
                if (f.wrong) Text(stringResource(R.string.restore_password_wrong))
                PasswordField(stringResource(R.string.set_backup_password), restorePassword) { restorePassword = it }
                PrimaryButton(stringResource(R.string.restore_open), {
                    val pw = restorePassword.toCharArray()
                    restorePassword = ""
                    a.run { try { inspectBackup(f.bytes, pw) } finally { pw.fill(' ') } }
                })
            }
            is SettingsFlow.RestorePreview -> InfoCard(stringResource(R.string.restore_preview_title), Tone.WARN) {
                val sum = f.summary
                Text(stringResource(R.string.restore_preview_made, DateFormat.getDateInstance().format(Date(sum.createdAtMs)), sum.appVersion))
                val first = sum.firstDay
                val last = sum.lastDay
                Text(if (first != null && last != null) stringResource(R.string.restore_preview_range, dateText(first), dateText(last), sum.sessions)
                    else stringResource(R.string.restore_preview_empty))
                Text(stringResource(R.string.restore_safety))
                PasswordField(stringResource(R.string.restore_safety_password), safetyPassword) { safetyPassword = it }
                PrimaryButton(stringResource(R.string.restore_go), {
                    restoreToken = f.token
                    safetyTo.launch("before-restore-" + BackupFormat.fileName(Days.of(java.time.LocalDate.now())))
                })
                SecondaryButton(stringResource(R.string.action_cancel), { a.run { settingsFlow(null) } })
            }
            is SettingsFlow.RestoreRefused -> InfoCard(stringResource(Labels.backupProblem(f.problem)), Tone.WARN)
            SettingsFlow.Restored -> InfoCard(stringResource(R.string.restore_done), Tone.GOOD)
            else -> Unit
        }
        if (unreadable) InfoCard(stringResource(R.string.set_restore_unreadable), Tone.WARN)
        if (m.flow !is SettingsFlow.RestorePreview && m.flow !is SettingsFlow.RestoreNeedsPassword) {
            SecondaryButton(stringResource(R.string.set_restore_pick), { unreadable = false; pickBackup.launch(arrayOf("*/*")) })
        }

        // ------------------------------------------------------------------ erase (two-step confirmation)
        SectionTitle(stringResource(R.string.set_erase_title))
        Body(stringResource(R.string.set_erase_body))
        SecondaryButton(stringResource(R.string.set_erase_title), { a.run { settingsFlow(SettingsFlow.Erase(1)) } })
        (m.flow as? SettingsFlow.Erase)?.let { e ->
            val first = e.confirmations <= 1
            AlertDialog(
                onDismissRequest = { a.run { settingsFlow(null) } },
                title = { Text(stringResource(if (first) R.string.erase_confirm1_title else R.string.erase_confirm2_title)) },
                text = { Text(stringResource(if (first) R.string.erase_confirm1_body else R.string.erase_confirm2_body)) },
                confirmButton = {
                    TextButton(onClick = { if (first) a.run { settingsFlow(SettingsFlow.Erase(2)) } else a.run { eraseAll() } }) {
                        Text(stringResource(if (first) R.string.erase_go else R.string.erase_go_final))
                    }
                },
                dismissButton = { TextButton(onClick = { a.run { settingsFlow(null) } }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }

        // ------------------------------------------------------------------ about
        SectionTitle(stringResource(R.string.set_about))
        Note(stringResource(R.string.set_privacy))
        Note(stringResource(R.string.set_not_medical))
        Note(stringResource(R.string.set_versions, m.appVersion, m.registryVersion, m.libraryVersion, m.conditionsVersion))
        PrimaryButton(stringResource(R.string.action_done), { a.run { closeSettings() } })
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth())
}

/** A training day (days since 1970-01-01) as the phone's local date format. */
private fun dateText(day: Int): String = DateFormat.getDateInstance().format(Date(Days.startMs(day, java.time.ZoneId.systemDefault())))
