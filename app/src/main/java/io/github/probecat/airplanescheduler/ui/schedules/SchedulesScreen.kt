package io.github.probecat.airplanescheduler.ui.schedules

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.core.ScheduleEdits
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.core.ScheduleStatus
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.data.Settings
import io.github.probecat.airplanescheduler.system.Access
import io.github.probecat.airplanescheduler.system.AccessState
import io.github.probecat.airplanescheduler.system.ShizukuStatus
import io.github.probecat.airplanescheduler.system.batteryOptimizationOff
import io.github.probecat.airplanescheduler.system.exactAlarmSettings
import io.github.probecat.airplanescheduler.system.exactAlarmsAllowed
import io.github.probecat.airplanescheduler.system.stopBatteryOptimization
import io.github.probecat.airplanescheduler.ui.AppViewModel
import io.github.probecat.airplanescheduler.ui.ContentWidth
import io.github.probecat.airplanescheduler.ui.DismissibleSnackbarHost
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.icon
import io.github.probecat.airplanescheduler.ui.rememberAccessRequest
import io.github.probecat.airplanescheduler.ui.rememberFormat
import io.github.probecat.airplanescheduler.ui.rememberNotificationRequest
import io.github.probecat.airplanescheduler.ui.theme.AddButton
import io.github.probecat.airplanescheduler.ui.theme.LocalSuccessColors
import io.github.probecat.airplanescheduler.ui.theme.LocalWarningColors
import io.github.probecat.airplanescheduler.ui.theme.OnAddButton
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    viewModel: AppViewModel,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    opening: Long? = null,
    onOpen: () -> Unit = {},
) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val missed by viewModel.missed.collectAsStateWithLifecycle()
    val format = rememberFormat()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var editing by rememberEditing(opening, schedules, onOpen)
    val requestAccess = rememberAccessRequest(viewModel, onHelp)
    // The schedule whose Skip or End asks whether to postpone instead.
    var choosing by rememberSaveable { mutableStateOf<Long?>(null) }
    val phone = rememberPhoneState()
    val requestNotifications = rememberNotificationAsk(viewModel)
    val actions = rememberScheduleActions(viewModel, format, snackbar)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { SchedulesTopBar(scrollBehavior, onHelp = onHelp, onSettings = onSettings) },
        floatingActionButton = { AddScheduleButton(onClick = { editing = viewModel.newSchedule() }) },
        snackbarHost = { DismissibleSnackbarHost(snackbar) },
    ) { padding ->
        SchedulesList(
            padding,
            schedules,
            now,
            missed,
            access,
            settings,
            phone,
            format,
            actions,
            onAccess = { requestAccess(access) },
            onHideReady = { viewModel.updateSettings { it.copy(hideShizukuReady = true) } },
            onEdit = { editing = it },
            onChoose = { choosing = it },
        )
    }

    editing?.let { schedule ->
        ScheduleSheet(
            initial = schedule,
            isNew = viewModel.isNew(schedule),
            now = now,
            conflictOf = viewModel::conflict,
            onDismiss = { editing = null },
            onSave = { edited -> actions.save(edited, onAsk = requestNotifications) },
            onDelete = actions::delete,
        )
    }

    choosing?.let { id ->
        PostponeChoice(
            schedules.find { it.id == id },
            now,
            missed,
            settings.postponeMinutes,
            format,
            actions,
            onClose = { choosing = null },
        )
    }
}

// The schedule open in the sheet. A tapped notification opens its schedule, then [onOpen] clears the request. A
// deleted schedule opens nothing.
@Composable
private fun rememberEditing(opening: Long?, schedules: List<Schedule>, onOpen: () -> Unit): MutableState<Schedule?> {
    val editing = rememberSaveable(stateSaver = ScheduleSaver) { mutableStateOf<Schedule?>(null) }
    if (opening != null) {
        SideEffect {
            schedules.find { it.id == opening }?.let { editing.value = it }
            onOpen()
        }
    }
    return editing
}

@Composable
private fun SchedulesList(
    padding: PaddingValues,
    schedules: List<Schedule>,
    now: LocalDateTime,
    missed: Long?,
    access: Access,
    settings: Settings,
    phone: PhoneState,
    format: Format,
    actions: ScheduleActions,
    onAccess: () -> Unit,
    onHideReady: () -> Unit,
    onEdit: (Schedule) -> Unit,
    onChoose: (Long) -> Unit,
) {
    val active = ScheduleResolver.activeSchedule(schedules, now)
    val strips = Strips.of(phone, access, settings, schedules)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(
                start = LIST_PADDING,
                end = LIST_PADDING,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (strips.any) {
                item(key = "notices") {
                    NoticeStrips(
                        strips,
                        access,
                        phone.shizukuInstalled,
                        onAccess = onAccess,
                        onHideReady = onHideReady,
                        Modifier.animateItem().bleed(LIST_PADDING).animateContentSize().padding(bottom = 18.dp),
                    )
                }
            }
            if (schedules.isEmpty()) {
                item(key = "empty") { EmptyState(Modifier.fillParentMaxHeight(0.6f)) }
            }
            items(schedules, key = { it.id }) { schedule ->
                val isActive = schedule.id == active?.id
                ScheduleItem(
                    schedule,
                    isActive = isActive,
                    isMissed = isActive && schedule.id == missed,
                    now = now,
                    access = access,
                    shizukuInstalled = phone.shizukuInstalled,
                    postponeMinutes = settings.postponeMinutes,
                    format = format,
                    actions = actions,
                    onEdit = { onEdit(schedule) },
                    onChoose = { onChoose(schedule.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

// ScheduleActions is a class, not a composable, so the ViewModel goes no further down the UI.
@Suppress("ViewModelForwarding")
@Composable
private fun rememberScheduleActions(
    viewModel: AppViewModel,
    format: Format,
    snackbar: SnackbarHostState,
): ScheduleActions {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    return remember(viewModel, resources, format, snackbar, scope) {
        ScheduleActions(viewModel, resources, format, snackbar, scope)
    }
}

// What the user does to schedules on this screen, and the messages that confirm it.
private class ScheduleActions(
    private val viewModel: AppViewModel,
    private val resources: Resources,
    private val format: Format,
    private val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    private val now get() = viewModel.now.value

    private fun show(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    // Like an alarm clock, says when airplane mode comes on, or how long it stays on if it is already on.
    fun showStart(id: Long) {
        val schedule = viewModel.schedules.value.find { it.id == id }?.takeIf { it.enabled } ?: return
        val message = if (ScheduleResolver.isActive(schedule, now)) {
            minutesLeft(schedule, now)?.let { resources.getString(R.string.schedules_on_for, format.duration(it)) }
        } else {
            minutesUntil(now) { ScheduleResolver.nextStart(schedule, it) }
                ?.let { resources.getString(R.string.schedules_set_for, format.duration(it)) }
        } ?: return
        show(message)
    }

    private fun showConflict(@StringRes message: Int = R.string.schedules_overlap) {
        show(resources.getString(message))
    }

    // Skip and End ask whether to postpone instead, when the window can be postponed.
    fun act(
        schedule: Schedule,
        status: ScheduleStatus,
        action: CardAction?,
        postponeMinutes: Int,
        onChoose: () -> Unit,
    ) {
        val postponable = ScheduleStatus.Action.POSTPONE in status.actions &&
            ScheduleResolver.postponedTo(schedule, now, postponeMinutes) != null
        when (action) {
            CardAction.SKIP, CardAction.END -> if (postponable) onChoose() else skip(schedule.id, status)
            CardAction.UNSKIP, CardAction.RESTORE, CardAction.RESUME, CardAction.START -> unskip(schedule.id, status)
            CardAction.TURN_ON -> catchUp(schedule.id, status)
            null -> {}
        }
    }

    private fun unskip(id: Long, offered: ScheduleStatus) {
        when (viewModel.unskip(id, offered)) {
            is ScheduleEdits.Result.Overlaps -> showConflict(R.string.schedules_overlaps_other)
            is ScheduleEdits.Result.Saved -> showStart(id)
            null -> {}
        }
    }

    fun skip(id: Long, offered: ScheduleStatus) {
        if (!viewModel.skip(id, offered)) return
        val ending = offered is ScheduleStatus.Running && !offered.missed
        show(resources.getString(if (ending) R.string.schedules_ended_early else R.string.schedules_skipped))
    }

    fun postpone(id: Long, offered: ScheduleStatus) {
        if (!viewModel.postpone(id, offered)) return
        val pausing = offered is ScheduleStatus.Running
        show(resources.getString(if (pausing) R.string.schedules_paused else R.string.schedules_postponed))
    }

    private fun catchUp(id: Long, offered: ScheduleStatus) {
        viewModel.catchUp(id, offered) { on ->
            if (on) showStart(id) else show(resources.getString(R.string.schedules_turn_on_failed))
        }
    }

    fun setEnabled(schedule: Schedule, enabled: Boolean) {
        val conflict = viewModel.setEnabled(schedule, enabled)
        if (conflict != null) showConflict() else showStart(schedule.id)
    }

    // Returns true when saved.
    fun save(edited: Schedule, onAsk: () -> Unit): Boolean {
        val saved = viewModel.save(edited) == null
        if (saved) showStart(edited.id)
        if (saved && viewModel.asksNotifications()) onAsk()
        return saved
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
}

// What the phone allows, read again whenever the app comes back.
private data class PhoneState(val exactAlarms: Boolean, val batteryOff: Boolean, val shizukuInstalled: Boolean) {
    companion object {
        fun of(context: Context) = PhoneState(
            exactAlarms = exactAlarmsAllowed(context),
            batteryOff = batteryOptimizationOff(context),
            shizukuInstalled = ShizukuStatus.launchIntent(context) != null,
        )
    }
}

@Composable
private fun rememberPhoneState(): PhoneState {
    val context = LocalContext.current
    var phone by remember { mutableStateOf(PhoneState.of(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { phone = PhoneState.of(context) }
    return phone
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulesTopBar(scrollBehavior: TopAppBarScrollBehavior, onHelp: () -> Unit, onSettings: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = onHelp) {
                Icon(painterResource(R.drawable.ic_help), contentDescription = stringResource(R.string.help))
            }
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun AddScheduleButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
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
}

private class Strips(val alarms: Boolean, val battery: Boolean, val status: Boolean) {
    val any get() = alarms || battery || status

    companion object {
        fun of(phone: PhoneState, access: Access, settings: Settings, schedules: List<Schedule>): Strips {
            val anyEnabled = schedules.any { it.enabled }
            return Strips(
                alarms = !phone.exactAlarms && anyEnabled,
                battery = !phone.batteryOff && anyEnabled,
                status = !access.ready || !settings.hideShizukuReady,
            )
        }
    }
}

@Composable
private fun NoticeStrips(
    strips: Strips,
    access: Access,
    shizukuInstalled: Boolean,
    onAccess: () -> Unit,
    onHideReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        if (strips.alarms) {
            Strip(
                label = stringResource(R.string.strip_alarms),
                icon = R.drawable.ic_error,
                tone = Tone.ERROR,
                onClick = { context.startActivity(exactAlarmSettings(context)) },
            )
        }
        if (strips.status && !access.ready) {
            Strip(
                label = stringResource(statusStrip(access, shizukuInstalled)),
                icon = access.state.icon,
                tone = Tone.ERROR,
                onClick = onAccess,
            )
        }
        if (strips.battery) {
            Strip(
                label = stringResource(R.string.strip_battery),
                icon = R.drawable.ic_warning,
                tone = Tone.WARNING,
                onClick = { stopBatteryOptimization(context) },
            )
        }
        if (strips.status && access.ready) {
            Strip(
                label = stringResource(statusStrip(access, shizukuInstalled)),
                icon = access.state.icon,
                tone = Tone.SUCCESS,
                onDismiss = onHideReady,
            )
        }
    }
}

@Composable
private fun ScheduleItem(
    schedule: Schedule,
    isActive: Boolean,
    isMissed: Boolean,
    now: LocalDateTime,
    access: Access,
    shizukuInstalled: Boolean,
    postponeMinutes: Int,
    format: Format,
    actions: ScheduleActions,
    onEdit: () -> Unit,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = ScheduleStatus.of(schedule, now, isMissed)
    val action = cardAction(status, canCatchUp = isMissed && access.ready)
    SwipeToDelete(onDelete = { actions.delete(schedule) }, modifier = modifier) {
        ScheduleCard(
            schedule = schedule,
            endsIn = if (isActive) minutesLeft(schedule, now) else null,
            missed = access.takeIf { isMissed },
            shizukuInstalled = shizukuInstalled,
            note = status?.takeIf { action?.undoes == true }?.let { note(it, schedule, now, format) },
            action = action,
            today = now.toLocalDate(),
            format = format,
            onClick = onEdit,
            onAction = { status?.let { actions.act(schedule, it, action, postponeMinutes, onChoose) } },
            onToggle = { enabled -> actions.setEnabled(schedule, enabled) },
        )
    }
}

// Asks whether to postpone or skip. It closes itself when the schedule was deleted or changed since it opened, for
// example by its notification, or when nothing is left to postpone.
@Composable
private fun PostponeChoice(
    schedule: Schedule?,
    now: LocalDateTime,
    missed: Long?,
    minutes: Int,
    format: Format,
    actions: ScheduleActions,
    onClose: () -> Unit,
) {
    val status = schedule?.let { ScheduleStatus.of(it, now, missed = it.id == missed) }
    val offered = remember(schedule?.id) { status }
    val to = schedule?.takeIf { status == offered }?.let { ScheduleResolver.postponedTo(it, now, minutes) }
    if (schedule == null || offered == null || to == null) {
        SideEffect { onClose() }
        return
    }
    PostponeDialog(
        schedule = schedule,
        ending = offered is ScheduleStatus.Running,
        minutes = minutes,
        to = to,
        now = now,
        format = format,
        onPostpone = {
            onClose()
            actions.postpone(schedule.id, offered)
        },
        onSkip = {
            onClose()
            actions.skip(schedule.id, offered)
        },
        onDismiss = onClose,
    )
}

// Sui users have no Shizuku app, so its absence says nothing about them.
private fun shizukuMissing(access: Access, shizukuInstalled: Boolean) =
    access.kind == Access.Kind.SHIZUKU && !shizukuInstalled

private fun statusStrip(access: Access, shizukuInstalled: Boolean): Int = when (access.state) {
    AccessState.READY -> when (access.kind) {
        Access.Kind.SHIZUKU -> R.string.strip_shizuku_ready
        Access.Kind.SUI -> R.string.strip_sui_ready
        Access.Kind.ROOT -> R.string.strip_root_ready
    }
    AccessState.OFFLINE -> if (shizukuMissing(access, shizukuInstalled)) {
        R.string.strip_shizuku_missing
    } else {
        R.string.strip_shizuku_offline
    }
    AccessState.PERMISSION_NEEDED -> when (access.kind) {
        Access.Kind.SHIZUKU -> R.string.strip_shizuku_permission
        Access.Kind.SUI -> R.string.strip_sui_permission
        Access.Kind.ROOT -> R.string.strip_root_permission
    }
}

private enum class Tone { SUCCESS, WARNING, ERROR }

// A line above the schedules about what the app needs. A problem stays until it is fixed, and a tap shows how. The user
// can dismiss the rest.
@Composable
private fun Strip(
    label: String,
    icon: Int,
    tone: Tone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        Tone.ERROR -> colors.errorContainer to colors.onErrorContainer
        Tone.WARNING -> LocalWarningColors.current.let { it.container to it.onContainer }
        Tone.SUCCESS -> LocalSuccessColors.current.let { it.container to it.onContainer }
    }
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = container,
        contentColor = content,
    ) {
        // The close button's touch target pads its icon by 15 dp, so both trailing icons end 16 dp in.
        val end = if (onDismiss != null) 1.dp else 16.dp
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(start = 16.dp + LIST_PADDING, end = end + LIST_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            if (onClick != null) {
                Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, Modifier.size(18.dp))
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_dismiss),
                        modifier = Modifier.size(18.dp),
                    )
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
    // Not saveable. Undo and app restarts can reuse an id, and a saved dismissed state then deletes the next
    // schedule with that id.
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
                        shape = RoundedCornerShape(CARD_CORNER),
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
    // Why airplane mode is off while it is active.
    missed: Access?,
    shizukuInstalled: Boolean,
    // Why the next window will not run as planned.
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
    val weight by animateIntAsState(if (on) 500 else 200)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.heightIn(min = 48.dp)) {
                Column(
                    Modifier.weight(1f).then(
                        if (action != null) Modifier.alignByBaseline() else Modifier.align(Alignment.CenterVertically),
                    ),
                ) {
                    DaysAndName(format.repeats(schedule, today), schedule.name)
                    when {
                        missed != null -> Missed(missed, shizukuInstalled)
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

// A name that does not fit beside the days moves to a line of its own.
@Composable
private fun DaysAndName(days: String, name: String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(days, style = MaterialTheme.typography.bodyLarge)
        if (name.isNotBlank()) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current.copy(alpha = NAME_ALPHA),
            )
        }
    }
}

private val TIME_SIZE = 46.sp

private val CARD_CORNER = 24.dp

private const val NAME_ALPHA = 0.84f

private val LIST_PADDING = 16.dp

// Widens the content past the list's side padding, so it reaches the screen edges.
private fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = horizontal.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = 2 * extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

@Composable
private fun EndsIn(duration: String) = Status(
    stringResource(R.string.card_ends_in, duration),
    R.drawable.ic_airplanemode_active,
    stringResource(R.string.card_ends_in_description, duration),
)

@Composable
private fun Missed(access: Access, shizukuInstalled: Boolean) {
    val text = when (access.state) {
        AccessState.READY -> R.string.card_missed
        AccessState.OFFLINE -> if (shizukuMissing(access, shizukuInstalled)) {
            R.string.card_missed_missing
        } else {
            R.string.card_missed_offline
        }
        AccessState.PERMISSION_NEEDED -> when (access.kind) {
            Access.Kind.SHIZUKU -> R.string.card_missed_permission
            Access.Kind.SUI -> R.string.card_missed_permission_sui
            Access.Kind.ROOT -> R.string.card_missed_permission_root
        }
    }
    val icon = if (access.ready) R.drawable.ic_error else access.state.icon
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

// Shifted by its padding so the label lines up with the switch.
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

// [undoes] a skip or postpone, which the card notes.
private enum class CardAction(@StringRes val label: Int, val undoes: Boolean = false) {
    SKIP(R.string.action_skip),
    END(R.string.action_end),
    UNSKIP(R.string.action_unskip, undoes = true),
    RESTORE(R.string.action_restore, undoes = true),
    RESUME(R.string.action_resume, undoes = true),
    START(R.string.action_turn_on, undoes = true),
    TURN_ON(R.string.action_turn_on),
}

// The app never ran a missed start, so it skips the window and does not end it. An undo of a skip while the window is
// under way turns airplane mode on now.
private fun cardAction(status: ScheduleStatus?, canCatchUp: Boolean): CardAction? = when (status) {
    is ScheduleStatus.Running -> when {
        canCatchUp -> CardAction.TURN_ON
        status.missed -> CardAction.SKIP
        else -> CardAction.END
    }
    is ScheduleStatus.Paused -> CardAction.RESUME
    is ScheduleStatus.Upcoming -> upcomingAction(status)
    is ScheduleStatus.Skipped -> skippedAction(status)
    null -> null
}

private fun upcomingAction(status: ScheduleStatus.Upcoming): CardAction? = when {
    status.postponed -> CardAction.RESTORE
    status.soon -> CardAction.SKIP
    else -> null
}

private fun skippedAction(status: ScheduleStatus.Skipped): CardAction = when {
    status.endedEarly -> CardAction.RESUME
    status.underway -> CardAction.START
    else -> CardAction.UNSKIP
}

private fun note(status: ScheduleStatus, schedule: Schedule, now: LocalDateTime, format: Format): Pair<String, Int>? =
    when (status) {
        is ScheduleStatus.Paused -> format.resumes(status.until) to R.drawable.ic_snooze
        is ScheduleStatus.Upcoming -> format.delayed(status.start).takeIf { status.postponed }?.let {
            it to
                R.drawable.ic_snooze
        }
        is ScheduleStatus.Skipped -> format.skipped(schedule, status.date, now) to R.drawable.ic_skip_next
        is ScheduleStatus.Running -> null
    }

@Composable
private fun PostponeDialog(
    schedule: Schedule,
    ending: Boolean,
    minutes: Int,
    to: LocalDateTime,
    now: LocalDateTime,
    format: Format,
    onPostpone: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val next = if (schedule.once) null else ScheduleResolver.nextWindow(ScheduleResolver.skip(schedule, now), now)
    val resumes = next?.let {
        stringResource(
            if (ending) R.string.postpone_starts else R.string.postpone_skip_starts,
            format.upcomingDay(it.toLocalDate(), now.toLocalDate()),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(format.title(schedule)) },
        text = {
            Column {
                DialogChoice(
                    icon = R.drawable.ic_snooze,
                    title = stringResource(
                        if (ending) R.string.action_pause else R.string.action_postpone,
                        format.duration(minutes),
                    ),
                    text = stringResource(
                        if (ending) R.string.postpone_resumes_at else R.string.starts_at,
                        format.time(to),
                    ),
                    onClick = onPostpone,
                )
                DialogChoice(
                    icon = R.drawable.ic_skip_next,
                    title = stringResource(if (ending) R.string.action_end else R.string.action_skip),
                    text = resumes,
                    onClick = onSkip,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DialogChoice(icon: Int, title: String, text: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = text?.let { { Text(it) } },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick),
    )
}

private fun minutesLeft(schedule: Schedule, now: LocalDateTime): Int? =
    minutesUntil(now) { ScheduleResolver.nextEnd(schedule, it) }

// Whole minutes from the start of the current minute, so a window that starts or ends on the minute shows
// its full length.
private fun minutesUntil(now: LocalDateTime, next: (ZonedDateTime) -> ZonedDateTime?): Int? {
    val minute = now.truncatedTo(ChronoUnit.MINUTES).atZone(ZoneId.systemDefault())
    return next(minute)?.let { Duration.between(minute, it).toMinutes().toInt() }
}

// Asks on opening when restored schedules exist. The returned request asks after a save.
@Composable
private fun rememberNotificationAsk(viewModel: AppViewModel): () -> Unit {
    val request = rememberNotificationRequest(fallback = false) {}
    SideEffect { if (viewModel.asksNotifications()) request() }
    return request
}
