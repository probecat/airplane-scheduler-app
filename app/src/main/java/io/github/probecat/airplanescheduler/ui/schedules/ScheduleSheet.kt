package io.github.probecat.airplanescheduler.ui.schedules

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.core.ScheduleTime
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.ui.CompactHeight
import io.github.probecat.airplanescheduler.ui.ErrorText
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.SwitchRow
import io.github.probecat.airplanescheduler.ui.rememberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

val ScheduleSaver: Saver<Schedule?, String> = Saver(
    save = { schedule -> schedule?.let { Json.encodeToString(it) } },
    restore = { Json.decodeFromString<Schedule>(it) },
)

private const val NAME_LENGTH = 40
private val NAME_MIN_WIDTH = 72.dp

// The smallest windows that fit each clock layout at font scale 1, plus the extra height per 1.0 of font scale.
private val VERTICAL_HEIGHT = 550.dp
private val VERTICAL_TEXT = 40.dp
private val HORIZONTAL_WIDTH = 600.dp
private val HORIZONTAL_HEIGHT = 340.dp
private val HORIZONTAL_TEXT = 40.dp

// Edits apply only on Save. Closing the sheet with unsaved edits asks to discard them first.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSheet(
    initial: Schedule,
    isNew: Boolean,
    now: LocalDateTime,
    conflictOf: (Schedule) -> Schedule?,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Boolean,
    onDelete: (Schedule) -> Unit,
) {
    val format = rememberFormat()
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(initial.id, stateSaver = ScheduleSaver) { mutableStateOf(initial) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val schedule = draft ?: initial
    val dirty = schedule.copy(name = schedule.name.trim()) != initial
    val currentDirty by rememberUpdatedState(dirty)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) { value ->
        (value != SheetValue.Hidden || closing || !currentDirty).also { if (!it) discarding = true }
    }

    val sameTimes = schedule.start == schedule.end
    val changed = isNew || dirty
    // A name cannot overlap anything, so a change to the name does not run the overlap check again.
    val conflict = remember(schedule.copy(name = ""), now) { conflictOf(schedule) }

    fun close(then: () -> Unit = {}) {
        closing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = {
            WindowInsets.navigationBars.union(WindowInsets.statusBars).union(WindowInsets.displayCutout)
        },
    ) {
        BackHandler(enabled = dirty && !closing) { discarding = true }
        SheetLayout(
            fields = {
                TimesRow(schedule, format, onChange = { draft = it }, Modifier.padding(horizontal = 16.dp))
                TimesNote(
                    schedule,
                    conflict,
                    format,
                    now.toLocalDate(),
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                OptionFields(schedule, format, onChange = { draft = it })
            },
            buttons = {
                SheetButtons(
                    canDelete = !isNew,
                    canSave = !sameTimes && conflict == null && changed,
                    onDelete = { close { onDelete(initial) } },
                    onSave = { if (onSave(schedule.copy(name = schedule.name.trim()))) close() },
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                )
            },
        )
    }

    if (discarding) {
        DiscardDialog(
            onDiscard = {
                discarding = false
                close()
            },
            onKeep = { discarding = false },
        )
    }
}

// The buttons stay below the scrolling fields, so Save is reachable in a short window and above the keyboard.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetLayout(fields: @Composable ColumnScope.() -> Unit, buttons: @Composable () -> Unit) {
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val showButtons = height >= CompactHeight || !WindowInsets.isImeVisible
    Column(Modifier.imePadding()) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = fields)
        if (showButtons) buttons()
    }
}

// A new start moves the end with it. A new end keeps the start, even when both are equal, so the sheet shows why it
// cannot save instead of moving the end silently.
private fun picked(schedule: Schedule, start: Boolean, minute: Int): Schedule = if (start) {
    schedule.copy(start = minute, end = ScheduleTime.movedEnd(schedule.start, schedule.end, minute))
} else {
    schedule.copy(end = minute)
}

@Composable
private fun TimesRow(schedule: Schedule, format: Format, onChange: (Schedule) -> Unit, modifier: Modifier = Modifier) {
    // True while picking the start, and false while picking the end.
    var picking by rememberSaveable { mutableStateOf<Boolean?>(null) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TimeButton(
            stringResource(R.string.sheet_starts),
            schedule.start,
            format,
            onClick = { picking = true },
            Modifier.weight(1f),
        )
        Icon(
            painterResource(R.drawable.ic_arrow_forward),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp).size(20.dp),
        )
        TimeButton(
            stringResource(R.string.sheet_ends),
            schedule.end,
            format,
            onClick = { picking = false },
            Modifier.weight(1f),
        )
    }
    picking?.let { start ->
        TimeDialog(
            title = stringResource(if (start) R.string.sheet_start_time else R.string.sheet_end_time),
            minute = if (start) schedule.start else schedule.end,
            is24Hour = format.is24Hour,
            onDismiss = { picking = null },
            onConfirm = { minute ->
                onChange(picked(schedule, start, minute))
                picking = null
            },
        )
    }
}

@Composable
private fun OptionFields(schedule: Schedule, format: Format, onChange: (Schedule) -> Unit) = Column {
    SheetHeading(stringResource(R.string.sheet_repeats))
    DayChips(
        format,
        schedule.days,
        onToggle = { day ->
            onChange(schedule.copy(days = if (day in schedule.days) schedule.days - day else schedule.days + day))
        },
        Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        icon = R.drawable.ic_wifi_off,
        title = stringResource(R.string.sheet_wifi_off),
        checked = schedule.disableWifi,
        onChange = { onChange(schedule.copy(disableWifi = it)) },
    )
    // Only Wi-Fi that the start turned off comes back, so the end option needs the start one.
    // It grays out instead of hiding. The sheet grows from the bottom, so hiding the row moves
    // the switch above it out from under the finger.
    SwitchRow(
        icon = R.drawable.ic_wifi,
        title = stringResource(R.string.sheet_wifi_on),
        checked = schedule.enableWifi,
        enabled = schedule.disableWifi,
        onChange = { onChange(schedule.copy(enableWifi = it)) },
    )
    NameRow(schedule.name) { onChange(schedule.copy(name = it)) }
}

// Why the times cannot be saved, or else how long the window lasts.
@Composable
private fun TimesNote(
    schedule: Schedule,
    conflict: Schedule?,
    format: Format,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        when {
            schedule.start == schedule.end -> ErrorText(stringResource(R.string.sheet_same_times))
            conflict != null -> ErrorText(stringResource(R.string.sheet_overlaps, format.label(conflict, today)))
            else -> Text(
                stringResource(
                    R.string.sheet_lasts,
                    format.duration(ScheduleTime.duration(schedule.start, schedule.end)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SheetButtons(
    canDelete: Boolean,
    canSave: Boolean,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // Delete sits at the start, away from Save, as it acts without a confirmation.
        if (canDelete) {
            FilledTonalIconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.size(ButtonDefaults.MinHeight),
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
            }
        }
        Button(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun DiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.sheet_discard_title)) },
        text = { Text(stringResource(R.string.sheet_discard_text)) },
        confirmButton = {
            TextButton(
                onClick = onDiscard,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.action_discard)) }
        },
        dismissButton = {
            TextButton(onClick = onKeep) { Text(stringResource(R.string.action_keep_editing)) }
        },
    )
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun TimeButton(
    label: String,
    minute: Int,
    format: Format,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = TIME_SIZE),
            )
        }
    }
}

private val TIME_SIZE = 42.sp

@Composable
private fun DayChips(
    format: Format,
    days: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        format.week.forEachIndexed { index, day ->
            val selected = day in days
            val colors = MaterialTheme.colorScheme
            val corner by animateIntAsState(if (selected) 50 else 25)
            val shape = RoundedCornerShape(percent = corner)
            val state = stringResource(if (selected) R.string.sheet_day_repeats else R.string.sheet_day_doesnt_repeat)
            Box(
                contentAlignment = BiasAlignment(-1f + 2f * index / (format.week.size - 1), 0f),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = format.dayName(day)
                        stateDescription = state
                    }
                    .toggleable(
                        value = selected,
                        interactionSource = null,
                        indication = null,
                        role = Role.Checkbox,
                    ) { onToggle(day) },
            ) {
                Surface(
                    shape = shape,
                    color = animateColorAsState(
                        if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
                    ).value,
                    contentColor = animateColorAsState(
                        if (selected) colors.onPrimaryContainer else colors.onSurface,
                    ).value,
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .widthIn(max = CHIP_SIZE)
                        .fillMaxWidth()
                        .aspectRatio(1f),
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
}

private val CHIP_SIZE = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, minute: Int, is24Hour: Boolean, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(minute / 60, minute % 60, is24Hour)
    val layout = timeLayout()
    var mode by remember {
        mutableStateOf(
            if (layout ==
                null
            ) {
                TimePickerDisplayMode.Input
            } else {
                TimePickerDisplayMode.Picker
            },
        )
    }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(state.hour * 60 + state.minute)
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = {
            Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 20.dp))
        },
        modeToggleButton = {
            // Without a clock that fits there is nothing to switch to.
            if (layout != null) {
                TimePickerDialogDefaults.DisplayModeToggle(
                    onDisplayModeChange = {
                        mode = when (mode) {
                            TimePickerDisplayMode.Picker -> TimePickerDisplayMode.Input
                            else -> TimePickerDisplayMode.Picker
                        }
                    },
                    displayMode = mode,
                )
            }
        },
    ) {
        if (mode == TimePickerDisplayMode.Picker &&
            layout != null
        ) {
            TimePicker(state, layoutType = layout)
        } else {
            TimeInput(state)
        }
    }
}

// The default layout follows the orientation, which pushes the buttons out of short portrait windows and
// leaves a mostly empty dialog on landscape tablets. Null means no clock fits, so the time is typed.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun timeLayout(): TimePickerLayoutType? {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val width = with(density) { window.width.toDp() }
    val height = with(density) { window.height.toDp() }
    // The clock has a fixed size, but the title and the buttons grow with the font scale.
    val text = (density.fontScale - 1).coerceAtLeast(0f)
    return when {
        height >= VERTICAL_HEIGHT + VERTICAL_TEXT * text -> TimePickerLayoutType.Vertical
        width >= HORIZONTAL_WIDTH && height >= HORIZONTAL_HEIGHT + HORIZONTAL_TEXT * text ->
            TimePickerLayoutType.Horizontal
        else -> null
    }
}

// Edited in place: tapping the row selects the name for typing over, and an empty name shows the
// placeholder, even while typing.
@Composable
private fun NameRow(name: String, onChange: (String) -> Unit) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(name)) }
    val focus = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_label),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        val label = stringResource(R.string.sheet_name)
        val style = MaterialTheme.typography.bodyLarge
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(Modifier.weight(1f)) {
            val room = constraints.maxWidth - with(LocalDensity.current) { (16.dp + NAME_MIN_WIDTH).roundToPx() }
            val showLabel = remember(label, style, room) {
                measurer.measure(label, style, maxLines = 1, softWrap = false).size.width <= room
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showLabel) {
                    Text(label, style = style, maxLines = 1)
                    Spacer(Modifier.width(16.dp))
                }
                NameField(
                    field,
                    label,
                    focus,
                    onChange = {
                        field = it.copy(text = it.text.take(NAME_LENGTH))
                        onChange(field.text)
                    },
                    onFocus = { field = field.copy(selection = TextRange(0, field.text.length)) },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NameField(
    field: TextFieldValue,
    label: String,
    focus: FocusRequester,
    onChange: (TextFieldValue) -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var textWidth by remember { mutableFloatStateOf(0f) }
    var placeholderWidth by remember { mutableFloatStateOf(0f) }
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = field,
        onValueChange = onChange,
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
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .semantics { contentDescription = label },
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
                        stringResource(R.string.sheet_name_placeholder),
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
