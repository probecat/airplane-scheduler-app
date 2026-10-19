package io.github.probecat.airplanescheduler.ui.schedules

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.mock.activeMock
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.system.batteryOptimizationOff
import io.github.probecat.airplanescheduler.system.exactAlarmSettings
import io.github.probecat.airplanescheduler.system.exactAlarmsAllowed
import io.github.probecat.airplanescheduler.system.stopBatteryOptimization
import io.github.probecat.airplanescheduler.ui.AppViewModel
import io.github.probecat.airplanescheduler.ui.ContentWidth
import io.github.probecat.airplanescheduler.ui.DismissibleSnackbarHost
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.ShizukuDialog
import io.github.probecat.airplanescheduler.ui.rememberFormat
import io.github.probecat.airplanescheduler.ui.rememberNotificationRequest
import io.github.probecat.airplanescheduler.ui.theme.AddButton
import io.github.probecat.airplanescheduler.ui.theme.LocalWarningColors
import io.github.probecat.airplanescheduler.ui.theme.OnAddButton
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    viewModel: AppViewModel,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    // Debug builds only.
    onMock: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizuku.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val missed by viewModel.missed.collectAsStateWithLifecycle()
    val format = rememberFormat()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var editing by rememberSaveable(stateSaver = ScheduleSaver) { mutableStateOf<Schedule?>(null) }
    var explaining by rememberSaveable { mutableStateOf<ShizukuState?>(null) }
    // The schedule whose Skip or End asks whether to postpone instead.
    var choosing by rememberSaveable { mutableStateOf<Long?>(null) }
    val active = ScheduleResolver.activeSchedule(schedules, now)
    var exactAlarms by remember { mutableStateOf(exactAlarmsAllowed(context)) }
    val mock = activeMock()
    var batteryOff by remember(mock) { mutableStateOf(batteryOptimizationOff(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exactAlarms = exactAlarmsAllowed(context)
        batteryOff = batteryOptimizationOff(context)
    }
    val requestNotifications = rememberNotificationRequest(fallback = false) {}

    // Like an alarm clock, says when airplane mode comes on, or how long it stays on if it already has.
    fun showStart(id: Long) {
        val schedule = viewModel.schedules.value.find { it.id == id }?.takeIf { it.enabled } ?: return
        val message = if (ScheduleResolver.isActive(schedule, now)) {
            minutesLeft(schedule, now)?.let { resources.getString(R.string.schedules_on_for, format.duration(it)) }
        } else {
            minutesUntil(now) { ScheduleResolver.nextStart(schedule, it) }
                ?.let { resources.getString(R.string.schedules_set_for, format.duration(it)) }
        } ?: return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun showConflict(conflict: Schedule) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                resources.getString(R.string.schedule_overlaps, format.label(conflict, now.toLocalDate())),
            )
        }
    }

    fun unskip(id: Long) {
        val conflict = viewModel.unskip(id)
        if (conflict != null) showConflict(conflict) else showStart(id)
    }

    fun skip(schedule: Schedule, ending: Boolean) {
        viewModel.skip(schedule)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                resources.getString(if (ending) R.string.schedules_ended_early else R.string.schedules_skipped),
            )
        }
    }

    fun postpone(schedule: Schedule, pausing: Boolean) {
        viewModel.postpone(schedule)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                resources.getString(if (pausing) R.string.schedules_paused else R.string.schedules_postponed),
            )
        }
    }

    fun catchUp(id: Long) {
        viewModel.catchUp { on ->
            if (on) {
                showStart(id)
            } else {
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(resources.getString(R.string.schedules_turn_on_failed))
                }
            }
        }
    }

    fun delete(schedule: Schedule) {
        viewModel.delete(schedule)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                resources.getString(R.string.schedules_deleted),
                resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restore(schedule)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { OverflowMenu(onSettings, onHelp, onMock) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = viewModel.newSchedule() },
                shape = RoundedCornerShape(20.dp),
                containerColor = AddButton,
                contentColor = OnAddButton,
                modifier = Modifier.size(80.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.schedules_add),
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        snackbarHost = { DismissibleSnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = ContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!exactAlarms && schedules.any { it.enabled }) {
                    item(key = "alarms") {
                        Strip(
                            label = stringResource(R.string.strip_alarms),
                            icon = R.drawable.ic_error,
                            error = true,
                            onClick = { context.startActivity(exactAlarmSettings(context)) },
                            modifier = Modifier.animateItem().padding(bottom = 8.dp),
                        )
                    }
                }
                if (!batteryOff && schedules.any { it.enabled }) {
                    item(key = "battery") {
                        Strip(
                            label = stringResource(R.string.strip_battery),
                            icon = R.drawable.ic_warning,
                            warning = true,
                            onClick = { stopBatteryOptimization(context) },
                            modifier = Modifier.animateItem().padding(bottom = 8.dp),
                        )
                    }
                }
                if (shizuku != ShizukuState.READY || !settings.hideShizukuReady) {
                    item(key = "status") {
                        val (label, icon) = when (shizuku) {
                            ShizukuState.READY -> R.string.strip_shizuku_ready to R.drawable.ic_check_circle
                            ShizukuState.OFFLINE -> R.string.strip_shizuku_offline to R.drawable.ic_error
                            ShizukuState.PERMISSION_NEEDED -> R.string.strip_shizuku_permission to R.drawable.ic_lock
                        }
                        val ready = shizuku == ShizukuState.READY
                        Strip(
                            label = stringResource(label),
                            icon = icon,
                            error = !ready,
                            onClick = if (ready) {
                                null
                            } else {
                                {
                                    if (shizuku != ShizukuState.PERMISSION_NEEDED || !viewModel.requestShizuku()) {
                                        explaining = shizuku
                                    }
                                }
                            },
                            onDismiss = if (ready) {
                                { viewModel.updateSettings { it.copy(hideShizukuReady = true) } }
                            } else {
                                null
                            },
                            modifier = Modifier.animateItem().padding(bottom = 8.dp),
                        )
                    }
                }
                if (schedules.isEmpty()) {
                    item(key = "empty") { EmptyState(Modifier.fillParentMaxHeight(0.6f)) }
                }
                items(schedules, key = { it.id }) { schedule ->
                    val isActive = schedule.id == active?.id
                    val isMissed = isActive && schedule.id == missed
                    val action = cardAction(schedule, isActive, isMissed && shizuku == ShizukuState.READY, now)
                    SwipeToDelete(onDelete = { delete(schedule) }, modifier = Modifier.animateItem()) {
                        ScheduleCard(
                            schedule = schedule,
                            endsIn = if (isActive) minutesLeft(schedule, now) else null,
                            missed = shizuku.takeIf { isMissed },
                            note = if (action == CardAction.UNDO || action == CardAction.RESUME) {
                                note(schedule, now, format)
                            } else {
                                null
                            },
                            action = action,
                            today = now.toLocalDate(),
                            format = format,
                            onClick = { editing = schedule },
                            onAction = {
                                when (action) {
                                    CardAction.SKIP, CardAction.END ->
                                        if (postponedTo(schedule, now, settings.postponeMinutes) != null) {
                                            choosing = schedule.id
                                        } else {
                                            skip(schedule, ending = action == CardAction.END)
                                        }
                                    CardAction.UNDO, CardAction.RESUME -> unskip(schedule.id)
                                    CardAction.TURN_ON -> catchUp(schedule.id)
                                    null -> {}
                                }
                            },
                            onToggle = { enabled ->
                                val conflict = viewModel.setEnabled(schedule, enabled)
                                if (conflict != null) showConflict(conflict) else showStart(schedule.id)
                            },
                        )
                    }
                }
            }
        }
    }

    editing?.let { schedule ->
        ScheduleSheet(
            initial = schedule,
            edits = viewModel.draft(schedule),
            isNew = viewModel.isNew(schedule),
            now = now,
            conflictOf = viewModel::conflict,
            onDismiss = { editing = null },
            onEdit = { viewModel.keepDraft(schedule, it) },
            onSave = { edited ->
                val isNew = viewModel.isNew(edited)
                (viewModel.save(edited) == null).also { saved ->
                    if (saved) showStart(edited.id)
                    if (saved && isNew && viewModel.asksNotifications()) requestNotifications()
                }
            },
            onDelete = ::delete,
        )
    }

    choosing?.let { id ->
        val schedule = schedules.find { it.id == id }
        val to = schedule?.let { postponedTo(it, now, settings.postponeMinutes) }
        // Gone, or nothing left to postpone, since the dialog opened.
        if (schedule == null || to == null) {
            SideEffect { choosing = null }
            return@let
        }
        val ending = schedule.id == active?.id
        PostponeDialog(
            schedule = schedule,
            ending = ending,
            minutes = settings.postponeMinutes,
            to = to,
            breaksAt = if (ending) null else breaksAt(schedule, schedules, now),
            now = now,
            format = format,
            onPostpone = {
                choosing = null
                postpone(schedule, pausing = ending)
            },
            onSkip = {
                choosing = null
                skip(schedule, ending)
            },
            onDismiss = { choosing = null },
        )
    }

    explaining?.let { state ->
        ShizukuDialog(
            state,
            onHelp = {
                explaining = null
                onHelp()
            },
            onDismiss = { explaining = null },
        )
    }
}

@Composable
private fun OverflowMenu(onSettings: () -> Unit, onHelp: () -> Unit, onMock: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOfNotNull(
                Triple(stringResource(R.string.settings), R.drawable.ic_settings, onSettings),
                Triple(stringResource(R.string.help), R.drawable.ic_help, onHelp),
                onMock?.let { Triple("UI states", R.drawable.ic_code, it) },
            ).forEach { (label, icon, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

// A line above the schedules about what the app needs. An error one stays until it's fixed; others can
// be dismissed.
@Composable
private fun Strip(
    label: String,
    icon: Int,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    warning: Boolean = false,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val warningColors = LocalWarningColors.current
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = when {
            error -> colors.errorContainer
            warning -> warningColors.container
            else -> colors.surfaceContainer
        },
        contentColor = when {
            error -> colors.onErrorContainer
            warning -> warningColors.onContainer
            else -> colors.onSurface
        },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(
                start = 16.dp,
                end = if (onDismiss != null) 8.dp else 16.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (error || warning) LocalContentColor.current else colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            if (onDismiss != null) {
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(
                painterResource(R.drawable.ic_airplanemode_active),
                contentDescription = null,
                modifier = Modifier.padding(20.dp).size(40.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.schedules_empty), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Not saveable: undo and app restarts can reuse an id, and a saved dismissed state would delete the
    // next schedule with it.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        onDismiss = { onDelete() },
        backgroundContent = {
            val direction = state.dismissDirection
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    val width = with(LocalDensity.current) { abs(state.requireOffset()).toDp() - 8.dp }
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxHeight().width(width.coerceAtLeast(0.dp)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                        }
                    }
                }
            }
        },
        content = { content() },
    )
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    endsIn: Int?,
    // Why airplane mode is off while it's active.
    missed: ShizukuState?,
    // Why the next window won't run as planned.
    note: Pair<String, Int>?,
    action: CardAction?,
    today: LocalDate,
    format: Format,
    onClick: () -> Unit,
    onAction: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val on = schedule.enabled
    val container by animateColorAsState(if (on) colors.primaryContainer else colors.surfaceContainer)
    val content by animateColorAsState(if (on) colors.onPrimaryContainer else colors.onSurfaceVariant)
    val time by animateColorAsState(if (on) colors.onPrimaryContainer else colors.onSurface)
    val weight by animateIntAsState(if (on) 450 else 200)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.heightIn(min = 48.dp)) {
                Column(
                    Modifier.weight(1f).then(
                        if (action != null) Modifier.alignByBaseline() else Modifier.align(Alignment.CenterVertically),
                    ),
                ) {
                    // Like Clock, a name that doesn't fit beside the days moves to a line of its own.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(format.repeats(schedule, today), style = MaterialTheme.typography.bodyLarge)
                        if (schedule.name.isNotBlank()) {
                            Text(schedule.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    when {
                        missed != null -> Missed(missed)
                        endsIn != null -> EndsIn(format.duration(endsIn))
                        note != null -> Status(note.first, note.second)
                    }
                }
                if (action != null) {
                    Spacer(Modifier.width(8.dp))
                    ActionButton(stringResource(action.label), onAction, Modifier.alignByBaseline())
                }
            }
            Row(Modifier.heightIn(min = 59.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    format.window(schedule, Format.MARKER),
                    style = MaterialTheme.typography.displayMedium,
                    color = time,
                    fontWeight = FontWeight((weight + 25) / 50 * 50),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = TIME_SIZE),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics {
                        contentDescription = "${format.window(schedule)}, ${format.repeats(schedule, today)}"
                    },
                )
            }
        }
    }
}

private val TIME_SIZE = 36.sp

@Composable
private fun EndsIn(duration: String) = Status(
    stringResource(R.string.card_ends_in, duration),
    R.drawable.ic_airplanemode_active,
    stringResource(R.string.card_ends_in_description, duration),
)

@Composable
private fun Missed(shizuku: ShizukuState) {
    val (text, icon) = when (shizuku) {
        ShizukuState.READY -> R.string.card_missed to R.drawable.ic_error
        ShizukuState.OFFLINE -> R.string.card_missed_offline to R.drawable.ic_error
        ShizukuState.PERMISSION_NEEDED -> R.string.card_missed_permission to R.drawable.ic_lock
    }
    val label = stringResource(text)
    Status(
        label,
        icon,
        stringResource(R.string.card_missed_description, label),
        MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun Status(
    text: String,
    icon: Int,
    description: String = text,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

// Like Clock's Dismiss. Shifted by its padding so the label lines up with the switch.
@Composable
private fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick,
        modifier.offset(x = 12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private enum class CardAction(@StringRes val label: Int) {
    SKIP(R.string.action_skip),
    END(R.string.action_end),
    UNDO(R.string.action_undo),
    RESUME(R.string.action_resume),
    TURN_ON(R.string.action_turn_on),
}

private fun cardAction(schedule: Schedule, isActive: Boolean, canCatchUp: Boolean, now: LocalDateTime): CardAction? =
    when {
        !schedule.enabled -> null
        canCatchUp -> CardAction.TURN_ON
        isActive -> CardAction.END
        ScheduleResolver.delay(schedule, now)?.due == true -> CardAction.RESUME
        ScheduleResolver.delay(schedule, now) != null -> CardAction.UNDO
        ScheduleResolver.pendingSkips(schedule, now).isNotEmpty() -> CardAction.UNDO
        // Skipping is offered only for a start that is close.
        ScheduleResolver.nextWindow(schedule, now)
            ?.let { Duration.between(now, it).toMinutes() <= ScheduleResolver.AHEAD_MINUTES } == true -> CardAction.SKIP
        else -> null
    }

private fun note(schedule: Schedule, now: LocalDateTime, format: Format): Pair<String, Int>? {
    ScheduleResolver.delay(schedule, now)?.let { return format.delayed(it.start) to R.drawable.ic_snooze }
    val skipped = ScheduleResolver.pendingSkips(schedule, now).minOrNull() ?: return null
    return format.skipped(schedule, skipped, now) to R.drawable.ic_skip_next
}

// The start of the next window when it continues one that runs until then.
private fun breaksAt(schedule: Schedule, schedules: List<Schedule>, now: LocalDateTime): LocalDateTime? {
    val date = ScheduleResolver.nextDate(schedule, now) ?: return null
    ScheduleResolver.preceding(schedule, date, schedules) ?: return null
    return ScheduleResolver.startOf(schedule, date)
}

// Where Postpone or Pause would start the window, or null when they're off or would leave nothing of it.
private fun postponedTo(schedule: Schedule, now: LocalDateTime, minutes: Int): LocalDateTime? =
    if (minutes > 0) ScheduleResolver.postponedTo(schedule, now, minutes) else null

@Composable
private fun PostponeDialog(
    schedule: Schedule,
    ending: Boolean,
    minutes: Int,
    to: LocalDateTime,
    // When airplane mode turns off instead, as the window continues another one.
    breaksAt: LocalDateTime?,
    now: LocalDateTime,
    format: Format,
    onPostpone: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val next = if (schedule.once) null else ScheduleResolver.nextWindow(ScheduleResolver.skip(schedule, now), now)
    val resumes = next?.let {
        stringResource(R.string.postpone_resumes, format.upcomingDay(it.toLocalDate(), now.toLocalDate()))
    } ?: stringResource(R.string.postpone_switches_off)
    val breaks = breaksAt?.let { stringResource(R.string.postpone_breaks_at, format.time(it)) }
    fun lines(text: String) = listOfNotNull(breaks, text).joinToString("\n")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(format.title(schedule)) },
        text = {
            Column {
                DialogChoice(
                    icon = R.drawable.ic_snooze,
                    title = stringResource(if (ending) R.string.action_pause else R.string.action_postpone),
                    text = lines(
                        stringResource(
                            if (ending) R.string.postpone_turns_back_on else R.string.starts_at,
                            format.time(to),
                        ),
                    ),
                    onClick = onPostpone,
                )
                DialogChoice(
                    icon = R.drawable.ic_skip_next,
                    title = stringResource(if (ending) R.string.action_end else R.string.action_skip),
                    text = lines(resumes),
                    onClick = onSkip,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DialogChoice(icon: Int, title: String, text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(text) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick),
    )
}

private fun minutesLeft(schedule: Schedule, now: LocalDateTime): Int? =
    minutesUntil(now) { ScheduleResolver.nextEnd(schedule, it) }

// Whole minutes from the start of the current one, so a window that starts or ends on the minute shows
// its full length.
private fun minutesUntil(now: LocalDateTime, next: (ZonedDateTime) -> ZonedDateTime?): Int? {
    val minute = now.truncatedTo(ChronoUnit.MINUTES).atZone(ZoneId.systemDefault())
    return next(minute)?.let { Duration.between(minute, it).toMinutes().toInt() }
}
