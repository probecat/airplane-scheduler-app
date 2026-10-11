package io.github.probecat.airplanescheduler.ui.schedules

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.core.ScheduleTime
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.ui.ErrorText
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.SwitchRow
import io.github.probecat.airplanescheduler.ui.rememberFormat
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

val ScheduleSaver: Saver<Schedule?, String> = Saver(
    save = { schedule -> schedule?.let { Json.encodeToString(it) } },
    restore = { Json.decodeFromString<Schedule>(it) },
)

private const val NAME_LENGTH = 40
private const val NAME_PLACEHOLDER = "Unnamed"

// Edits apply only on Save. Closing the sheet any other way keeps them through [onEdit] for next time.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSheet(
    initial: Schedule,
    edits: Schedule,
    isNew: Boolean,
    now: LocalDateTime,
    conflictOf: (Schedule) -> Schedule?,
    onDismiss: () -> Unit,
    onEdit: (Schedule) -> Unit,
    onSave: (Schedule) -> Boolean,
    onDelete: (Schedule) -> Unit,
) {
    val format = rememberFormat()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by rememberSaveable(initial.id, stateSaver = ScheduleSaver) { mutableStateOf(edits) }
    var picking by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val schedule = draft ?: edits
    LaunchedEffect(schedule) { onEdit(schedule) }

    val sameTimes = schedule.start == schedule.end
    val conflict = conflictOf(schedule)

    fun close(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }

    // The keyboard lifts the content with imePadding instead of resizing the sheet's window, which
    // made the sheet jump to the top and settle back.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeButton("Starts", schedule.start, format, Modifier.weight(1f)) { picking = true }
                Icon(
                    painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp).size(20.dp),
                )
                TimeButton("Ends", schedule.end, format, Modifier.weight(1f)) { picking = false }
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                when {
                    sameTimes -> ErrorText("Start and end can't be the same time.")
                    conflict != null -> ErrorText(
                        "This schedule overlaps ${format.label(conflict, now.toLocalDate())}.",
                    )
                    else -> Text(
                        "Airplane mode stays on for " +
                            "${format.duration(ScheduleTime.duration(schedule.start, schedule.end))}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Repeats",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
                    .semantics { heading() },
            )
            DayChips(format, schedule.days, Modifier.padding(horizontal = 16.dp)) { day ->
                draft = schedule.copy(days = if (day in schedule.days) schedule.days - day else schedule.days + day)
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                icon = R.drawable.ic_wifi_off,
                title = "Turn off Wi-Fi at start",
                checked = schedule.disableWifi,
            ) { draft = schedule.copy(disableWifi = it) }
            // Only Wi-Fi that the start turned off comes back, so the end option needs the start one.
            // It grays out instead of hiding: the sheet grows from the bottom, so a hidden row would
            // move the switch above it out from under the finger.
            SwitchRow(
                icon = R.drawable.ic_wifi,
                title = "Turn on Wi-Fi at end",
                checked = schedule.enableWifi,
                enabled = schedule.disableWifi,
            ) { draft = schedule.copy(enableWifi = it) }
            NameRow(schedule.name) { draft = schedule.copy(name = it) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!isNew) {
                    FilledTonalButton(
                        onClick = { close { onDelete(initial) } },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Delete")
                    }
                }
                Button(
                    onClick = { if (onSave(schedule.copy(name = schedule.name.trim()))) close() },
                    enabled = !sameTimes && conflict == null,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }

    picking?.let { start ->
        TimeDialog(
            title = if (start) "Start time" else "End time",
            minute = if (start) schedule.start else schedule.end,
            is24Hour = format.is24Hour,
            onDismiss = { picking = null },
            onConfirm = { minute ->
                draft = if (start) schedule.copy(start = minute) else schedule.copy(end = minute)
                picking = null
            },
        )
    }
}

@Composable
private fun TimeButton(
    label: String,
    minute: Int,
    format: Format,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "$label, ${format.time(minute)}"
        },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).clearAndSetSemantics {}) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                format.time(minute, Format.MARKER),
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 16.sp,
                    maxFontSize = MaterialTheme.typography.displaySmall.fontSize,
                ),
            )
        }
    }
}

@Composable
private fun DayChips(
    format: Format,
    days: Set<DayOfWeek>,
    modifier: Modifier = Modifier,
    onToggle: (DayOfWeek) -> Unit,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (day in format.week) {
            val selected = day in days
            val colors = MaterialTheme.colorScheme
            val corner by animateIntAsState(if (selected) 50 else 25)
            val shape = RoundedCornerShape(percent = corner)
            Surface(
                shape = shape,
                color = animateColorAsState(
                    if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
                ).value,
                contentColor = animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurface).value,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(shape)
                    .semantics {
                        contentDescription = format.dayName(day)
                        stateDescription = if (selected) "Repeats" else "Doesn't repeat"
                    }
                    .toggleable(
                        value = selected,
                        interactionSource = null,
                        indication = null,
                        role = Role.Checkbox,
                    ) { onToggle(day) },
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clearAndSetSemantics {},
                ) {
                    Text(format.dayLetter(day), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, minute: Int, is24Hour: Boolean, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(minute / 60, minute % 60, is24Hour)
    var mode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = {
            Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 20.dp))
        },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    mode = when (mode) {
                        TimePickerDisplayMode.Picker -> TimePickerDisplayMode.Input
                        else -> TimePickerDisplayMode.Picker
                    }
                },
                displayMode = mode,
            )
        },
    ) {
        if (mode == TimePickerDisplayMode.Picker) TimePicker(state) else TimeInput(state)
    }
}

// Edited in place: tapping the row selects the name for typing over, and an empty name shows the
// placeholder, even while typing.
@Composable
private fun NameRow(name: String, onChange: (String) -> Unit) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(name)) }
    var focused by remember { mutableStateOf(false) }
    var textWidth by remember { mutableFloatStateOf(0f) }
    var placeholderWidth by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_label), contentDescription = null, tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text("Schedule name", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(16.dp))
        BasicTextField(
            value = field,
            onValueChange = {
                field = it.copy(text = it.text.take(NAME_LENGTH))
                onChange(field.text)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
            ),
            cursorBrush = SolidColor(colors.primary),
            onTextLayout = { textWidth = it.getLineRight(0) - it.getLineLeft(0) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) field = field.copy(selection = TextRange(0, field.text.length))
                }
                .semantics { contentDescription = "Schedule name" },
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier.drawBehind {
                        // Underlines just the name or placeholder, which sits at the end of the field.
                        if (focused) {
                            val y = size.height + 4.dp.toPx()
                            val start = size.width - if (field.text.isEmpty()) placeholderWidth else textWidth
                            drawLine(colors.primary, Offset(start, y), Offset(size.width, y), 2.dp.toPx())
                        }
                    },
                ) {
                    if (field.text.isEmpty()) {
                        Text(
                            NAME_PLACEHOLDER,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            onTextLayout = { placeholderWidth = it.size.width.toFloat() },
                        )
                    }
                    inner()
                }
            },
        )
    }
}
