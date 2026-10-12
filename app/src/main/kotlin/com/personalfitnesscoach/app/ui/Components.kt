@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.personalfitnesscoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Text(text, modifier = modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, color = Glass.text)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(top = 6.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold, color = Glass.text)
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyLarge, color = color)
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = Glass.muted)
}

/**
 * The one main action of a screen (D-099): a warm gradient pill with a soft halo, white bold text. Disabled, it turns to plain glass
 * with muted text, so "can't tap" never depends on colour alone (the button is also not clickable).
 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val g = Glass
    val on = enabled && !LocalBusy.current
    Box(
        modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (on) Modifier.glow(g.warm, radius = 16.dp, alpha = if (g.dark) 0.5f else 0.3f) else Modifier)
            .clip(PillShape)
            .then(if (on) Modifier.background(warmBrush()) else Modifier.glass(PillShape))
            .clickable(enabled = on, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            color = if (on) g.onWarm else g.muted)
    }
}

/** A secondary action: a frosted-glass pill. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val g = Glass
    OutlinedButton(onClick = onClick, enabled = enabled && !LocalBusy.current, modifier = modifier.fillMaxWidth().heightIn(min = SecondaryHeight + 4.dp),
        shape = PillShape, border = androidx.compose.foundation.BorderStroke(1.dp, g.glassBorder),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = g.glass, contentColor = g.text,
            disabledContainerColor = Color.Transparent, disabledContentColor = g.muted)) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
    }
}

/** A small text action ("Why?", "Show older sessions"): the cool link colour. */
@Composable
fun SmallAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled && !LocalBusy.current, modifier = modifier.heightIn(min = SecondaryHeight),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Glass.link)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

enum class Tone { NEUTRAL, GOOD, WARN, ALERT }

/** A message card in frosted glass; the tone tints its border and veil, the words always carry the meaning (NFR-08). */
@Composable
fun InfoCard(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier, content: (@Composable ColumnScope.() -> Unit)? = null) {
    val g = Glass
    val (veil, edge) = when (tone) {
        Tone.NEUTRAL -> g.glass to g.glassBorder
        Tone.GOOD -> g.cool.copy(alpha = 0.08f) to g.cool.copy(alpha = 0.45f)
        Tone.WARN -> g.cautionTint to g.caution.copy(alpha = 0.5f)
        Tone.ALERT -> g.alertTint to g.alert.copy(alpha = 0.6f)
    }
    Column(modifier.fillMaxWidth().background(veil, CardShape).border(1.dp, edge, CardShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = g.text)
        content?.invoke(this)
    }
}

/** A frosted-glass card for any content (tiles, panels). */
@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(CardShape).padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
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
            GlowChip(text, selected(o), { onToggle(o) })
        }
    }
}

/** A choice chip: glass, or warm-tinted with a warm edge when chosen (the chosen state is also read out by TalkBack). */
@Composable
fun GlowChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val g = Glass
    FilterChip(selected = selected, onClick = onClick, enabled = !LocalBusy.current, label = { Text(text, style = MaterialTheme.typography.labelLarge) },
        shape = ChipShape,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(containerColor = g.glass, labelColor = g.text,
            selectedContainerColor = g.warm.copy(alpha = if (g.dark) 0.22f else 0.14f), selectedLabelColor = g.text,
            selectedLeadingIconColor = g.text),
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = g.glassBorder,
            selectedBorderColor = g.warm, borderWidth = 1.dp, selectedBorderWidth = 1.5.dp),
        modifier = modifier.heightIn(min = SecondaryHeight).widthIn(min = SecondaryHeight))
}

/** A yes / no (/ not sure) question. */
@Composable
fun AnswerRow(question: String, answer: Boolean?, onAnswer: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(question, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowChip(stringResource(R.string.action_yes), answer == true, { onAnswer(true) })
            GlowChip(stringResource(R.string.action_no), answer == false, { onAnswer(false) })
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
                val g = Glass
                Box(
                    Modifier.size(PrimaryHeight).then(if (on) Modifier.glow(g.warm, radius = 12.dp, corner = 28.dp, alpha = 0.45f) else Modifier)
                        .clip(CircleShape)
                        .then(if (on) Modifier.background(warmBrush()) else Modifier.glass(CircleShape))
                        .selectable(selected = on, enabled = !LocalBusy.current, role = Role.RadioButton, onClick = { onChange(i) })
                        .semantics { contentDescription = desc },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(i.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (on) g.onWarm else g.text)
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
        RoundButton(MINUS, less, onMinus, minusEnabled)
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        RoundButton(PLUS, more, onPlus, plusEnabled)
    }
}

/** A round glass button (steppers): 56 dp, a symbol, and its spoken label. */
@Composable
fun RoundButton(symbol: String, label: String, onClick: () -> Unit, enabled: Boolean = true, size: androidx.compose.ui.unit.Dp = PrimaryHeight) {
    val g = Glass
    val on = enabled && !LocalBusy.current
    Box(Modifier.size(size).clip(CircleShape).glass(CircleShape, strong = true)
        .clickable(enabled = on, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = if (on) g.text else g.muted)
    }
}

/** A round glass button with one of the app's line icons (settings, back): 48 dp, read by TalkBack as [label]. */
@Composable
fun IconRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                    enabled: Boolean = true) {
    val g = Glass
    val on = enabled && !LocalBusy.current
    Box(modifier.size(SecondaryHeight).clip(CircleShape).glass(CircleShape, strong = true)
        .clickable(enabled = on, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = if (on) g.text else g.muted, modifier = Modifier.size(22.dp))
    }
}

/**
 * A small glass tile with an icon, a big number and a label (Today's steps, minutes, streak). TalkBack reads [description] as one
 * sentence instead of the separate pieces.
 */
@Composable
fun StatTile(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, value: String, label: String, description: String,
             modifier: Modifier = Modifier) {
    Column(modifier.glass(RoundedTile).padding(horizontal = 8.dp, vertical = 12.dp)
        .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(value, style = com.personalfitnesscoach.app.ui.theme.NumeralsSmall.copy(fontSize = 24.sp, lineHeight = 30.sp), color = Glass.text,
            textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Glass.muted, textAlign = TextAlign.Center)
    }
}

private val RoundedTile = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)

/**
 * A tinted pill button with a line icon and its words (the workout's "Something hurts", "Warning signs", "Change time", the rest's
 * ±30 s): at least 56 dp tall, the tone colours its veil and edge, the words carry the meaning.
 */
@Composable
fun TonalPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
              tone: Tone = Tone.NEUTRAL, enabled: Boolean = true) {
    val g = Glass
    val on = enabled && !LocalBusy.current
    val (veil, edge, ink) = when (tone) {
        Tone.NEUTRAL, Tone.GOOD -> Triple(g.glass, g.glassBorder, g.text)
        Tone.WARN -> Triple(g.cautionTint, g.caution.copy(alpha = 0.55f), g.caution)
        Tone.ALERT -> Triple(g.alertTint, g.alert.copy(alpha = 0.6f), g.alert)
    }
    Row(modifier.heightIn(min = PrimaryHeight).clip(ChipShape).background(veil).border(1.dp, edge, ChipShape)
        .clickable(enabled = on, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        if (icon != null) androidx.compose.material3.Icon(icon, contentDescription = null, tint = if (on) ink else g.muted, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (on) ink else g.muted, textAlign = TextAlign.Center)
    }
}

/** One line of "why today looks like this", with a warm dot and its "Why?" link. */
@Composable
fun ReasonRow(text: String, why: String, onWhy: () -> Unit, modifier: Modifier = Modifier) {
    val g = Glass
    Row(modifier.fillMaxWidth().glass(ChipShape).padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).background(g.warm, CircleShape))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = g.text, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        SmallAction(why, onWhy)
    }
}

/** Symbols, not words (the spoken label comes from a string resource). */
const val MINUS = "\u2212"
const val PLUS = "+"
const val TIMES = "\u00D7"

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
        shape = ChipShape,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = Glass.warm, unfocusedBorderColor = Glass.glassBorder,
            focusedContainerColor = Glass.glass, unfocusedContainerColor = Glass.glass, cursorColor = Glass.warm, focusedLabelColor = Glass.warm,
            unfocusedLabelColor = Glass.muted, focusedTextColor = Glass.text, unfocusedTextColor = Glass.text),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

private val KG = DecimalFormat("0.##", DecimalFormatSymbols(Locale.ROOT))

/** 62.5 → "62.5", 60.0 → "60" (weights as gyms write them). */
fun kg(x: Double): String = KG.format(x)

/** Minutes for display, rounded to the nearest whole minute. */
fun minutes(x: Double): String = Math.round(x).toString()
