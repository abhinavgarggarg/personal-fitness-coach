@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.personalfitnesscoach.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personalfitnesscoach.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** True while the controller handles a tap: every button is disabled, so a double tap can't log a set twice (NFR-03). */
val LocalBusy = compositionLocalOf { false }

/** Primary actions are at least 56 dp tall (NFR-09), secondary ones at least 48 dp. */
val PrimaryHeight = 56.dp
val SecondaryHeight = 48.dp

/** A scrolling screen with a heading: nothing is cut off on small phones or at 200% text (NFR-08, D-044). */
@Composable
fun ScreenColumn(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (title != null) Heading(title)
        content()
        Spacer(Modifier.heightIn(min = 24.dp))
    }
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(top = 6.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold)
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyLarge, color = color)
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled && !LocalBusy.current, modifier = modifier.fillMaxWidth().heightIn(min = PrimaryHeight)) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled && !LocalBusy.current, modifier = modifier.fillMaxWidth().heightIn(min = SecondaryHeight)) {
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
fun SmallAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled && !LocalBusy.current, modifier = modifier.heightIn(min = SecondaryHeight)) { Text(text) }
}

enum class Tone { NEUTRAL, GOOD, WARN, ALERT }

/** A message card; the tone sets its colour, the words always carry the meaning (NFR-08). */
@Composable
fun InfoCard(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier, content: (@Composable ColumnScope.() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        Tone.NEUTRAL -> cs.surfaceVariant to cs.onSurfaceVariant
        Tone.GOOD -> cs.primaryContainer to cs.onPrimaryContainer
        Tone.WARN -> cs.tertiaryContainer to cs.onTertiaryContainer
        Tone.ALERT -> cs.errorContainer to cs.onErrorContainer
    }
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            content?.invoke(this)
        }
    }
}

/** A tappable row with a checkbox; the whole row is the touch target and reads as one checkbox to TalkBack. */
@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().heightIn(min = SecondaryHeight)
            .toggleable(value = checked, enabled = enabled && !LocalBusy.current, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().heightIn(min = SecondaryHeight)
            .toggleable(value = checked, enabled = enabled && !LocalBusy.current, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun RadioRow(text: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = SecondaryHeight)
            .selectable(selected = selected, enabled = !LocalBusy.current, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** Chips that wrap onto new lines (200% text never overflows). */
@Composable
fun <T> ChipGroup(options: List<T>, selected: (T) -> Boolean, label: @Composable (T) -> String, onToggle: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            val text = label(o)
            FilterChip(selected = selected(o), onClick = { onToggle(o) }, enabled = !LocalBusy.current, label = { Text(text) },
                modifier = Modifier.heightIn(min = SecondaryHeight))
        }
    }
}

/** A yes / no (/ not sure) question. */
@Composable
fun AnswerRow(question: String, answer: Boolean?, onAnswer: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(question, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = answer == true, onClick = { onAnswer(true) }, enabled = !LocalBusy.current,
                label = { Text(stringResource(R.string.action_yes)) }, modifier = Modifier.heightIn(min = SecondaryHeight))
            FilterChip(selected = answer == false, onClick = { onAnswer(false) }, enabled = !LocalBusy.current,
                label = { Text(stringResource(R.string.action_no)) }, modifier = Modifier.heightIn(min = SecondaryHeight))
        }
    }
}

/**
 * RDY-001 rating row: five large dots, 3 = a normal day. Each dot is a radio button with its own spoken label ("Sleep: 4 of 5").
 */
@Composable
fun DotRow(label: String, low: String, high: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..5) {
                val on = i == value
                val desc = stringResource(R.string.checkin_dot_desc, label, i)
                Surface(
                    selected = on,
                    onClick = { onChange(i) },
                    enabled = !LocalBusy.current,
                    shape = CircleShape,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(PrimaryHeight).semantics { contentDescription = desc; role = Role.RadioButton },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(i.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Note(low)
            Note(high)
        }
    }
}

/** − value + with 56 dp buttons; TalkBack reads "Decrease" and "Increase" with the value. */
@Composable
fun Stepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, minusEnabled: Boolean = true, plusEnabled: Boolean = true) {
    val less = stringResource(R.string.action_decrease, text)
    val more = stringResource(R.string.action_increase, text)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onMinus, enabled = minusEnabled && !LocalBusy.current,
            modifier = Modifier.size(PrimaryHeight).semantics { contentDescription = less }) { Text(MINUS) }
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        OutlinedButton(onClick = onPlus, enabled = plusEnabled && !LocalBusy.current,
            modifier = Modifier.size(PrimaryHeight).semantics { contentDescription = more }) { Text(PLUS) }
    }
}

/** Symbols, not words (the spoken label comes from a string resource). */
const val MINUS = "\u2212"
const val PLUS = "+"

/**
 * A number field that keeps what the user is typing (so "62." stays on screen) and reports a parsed value, or null when empty or not a
 * number. Commas are read as decimal points.
 */
@Composable
fun NumberField(label: String, value: Double?, onValue: (Double?) -> Unit, modifier: Modifier = Modifier, decimals: Boolean = true) {
    var text by remember(value == null) { mutableStateOf(value?.let { if (decimals) kg(it) else it.toLong().toString() } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            val clean = s.replace(',', '.').filter { it.isDigit() || (decimals && it == '.') }.take(7)
            text = clean
            onValue(clean.toDoubleOrNull())
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

private val KG = DecimalFormat("0.##", DecimalFormatSymbols(Locale.ROOT))

/** 62.5 → "62.5", 60.0 → "60" (weights as gyms write them). */
fun kg(x: Double): String = KG.format(x)

/** Minutes for display, rounded to the nearest whole minute. */
fun minutes(x: Double): String = Math.round(x).toString()
