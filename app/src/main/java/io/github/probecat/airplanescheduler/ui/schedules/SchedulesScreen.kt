package io.github.probecat.airplanescheduler.ui.schedules

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.ui.AppViewModel
import io.github.probecat.airplanescheduler.ui.ContentWidth
import io.github.probecat.airplanescheduler.ui.DismissibleSnackbarHost
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.ShizukuDialog
import io.github.probecat.airplanescheduler.ui.rememberFormat
import io.github.probecat.airplanescheduler.ui.theme.AddButton
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
    val active = ScheduleResolver.activeSchedule(schedules, now)

    // Like an alarm clock, says when airplane mode comes on, or how long it stays on if it already has.
    fun showStart(id: Long) {
        val schedule = viewModel.schedules.value.find { it.id == id }?.takeIf { it.enabled } ?: return
        val message = if (ScheduleResolver.isActive(schedule, now)) {
            minutesLeft(schedule, now)?.let { "Airplane mode is on for ${format.duration(it)}" }
        } else {
            minutesUntil(now) { ScheduleResolver.nextStart(schedule, it) }
                ?.let { "Airplane mode set for ${format.duration(it)} from now" }
        } ?: return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun showConflict(conflict: Schedule) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar("This schedule overlaps ${format.label(conflict, now.toLocalDate())}.")
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
            snackbar.showSnackbar(if (ending) "Schedule ended early" else "Schedule skipped")
        }
    }

    fun catchUp(id: Long) {
        viewModel.catchUp { on ->
            if (on) {
                showStart(id)
            } else {
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Couldn't turn on airplane mode")
                }
            }
        }
    }

    fun delete(schedule: Schedule) {
        viewModel.delete(schedule)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Schedule deleted", "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restore(schedule)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Airplane Scheduler") },
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
                    contentDescription = "Add schedule",
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
                if (shizuku != ShizukuState.READY || !settings.hideShizukuReady) {
                    item(key = "status") {
                        ShizukuStrip(
                            state = shizuku,
                            onClick = {
                                if (shizuku != ShizukuState.PERMISSION_NEEDED || !viewModel.requestShizuku()) {
                                    explaining = shizuku
                                }
                            },
                            onDismiss = { viewModel.updateSettings { it.copy(hideShizukuReady = true) } },
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
                            skipped = if (action == CardAction.UNDO) skippedNote(schedule, now, format) else null,
                            action = action,
                            today = now.toLocalDate(),
                            format = format,
                            onClick = { editing = schedule },
                            onAction = {
                                when (action) {
                                    CardAction.SKIP, CardAction.END -> skip(schedule, ending = action == CardAction.END)
                                    CardAction.UNDO -> unskip(schedule.id)
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
                (viewModel.save(edited) == null).also { saved -> if (saved) showStart(edited.id) }
            },
            onDelete = ::delete,
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
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOfNotNull(
                Triple("Settings", R.drawable.ic_settings, onSettings),
                Triple("Help", R.drawable.ic_help, onHelp),
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

@Composable
private fun ShizukuStrip(
    state: ShizukuState,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val ready = state == ShizukuState.READY
    val (label, icon) = when (state) {
        ShizukuState.READY -> "Shizuku is ready" to R.drawable.ic_check_circle
        ShizukuState.OFFLINE -> "Shizuku is unavailable, schedules can't work" to R.drawable.ic_error
        ShizukuState.PERMISSION_NEEDED -> "Allow Shizuku access for the app to work" to R.drawable.ic_lock
    }
    Surface(
        onClick = onClick,
        enabled = !ready,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (ready) colors.surfaceContainer else colors.errorContainer,
        contentColor = if (ready) colors.onSurface else colors.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(start = 16.dp, end = if (ready) 8.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (ready) colors.primary else LocalContentColor.current,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (ready) {
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Dismiss") }
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
        Text("No schedules yet", style = MaterialTheme.typography.titleLarge)
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
    // Minutes until it ends, while it's active.
    endsIn: Int?,
    // Why airplane mode is off while it's active.
    missed: ShizukuState?,
    skipped: String?,
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
                        skipped != null -> Status(skipped, R.drawable.ic_skip_next)
                    }
                }
                if (action != null) {
                    Spacer(Modifier.width(8.dp))
                    ActionButton(action.label, onAction, Modifier.alignByBaseline())
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
private fun EndsIn(duration: String) =
    Status("Ends in $duration", R.drawable.ic_airplanemode_active, "Active now, ends in $duration")

@Composable
private fun Missed(shizuku: ShizukuState) {
    val (text, icon) = when (shizuku) {
        ShizukuState.READY -> "Missed the start" to R.drawable.ic_error
        ShizukuState.OFFLINE -> "Missed, Shizuku is unavailable" to R.drawable.ic_error
        ShizukuState.PERMISSION_NEEDED -> "Missed, Shizuku access needed" to R.drawable.ic_lock
    }
    Status(text, icon, "Active now, but airplane mode is off. $text", MaterialTheme.colorScheme.error)
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

private enum class CardAction(val label: String) {
    SKIP("Skip"),
    END("End"),
    UNDO("Undo"),
    TURN_ON("Turn on"),
}

// Skipping is offered only for a start that is close.
private val SKIP_AHEAD = Duration.ofHours(2)

private fun cardAction(schedule: Schedule, isActive: Boolean, canCatchUp: Boolean, now: LocalDateTime): CardAction? =
    when {
        !schedule.enabled -> null
        canCatchUp -> CardAction.TURN_ON
        isActive -> CardAction.END
        ScheduleResolver.pendingSkips(schedule, now).isNotEmpty() -> CardAction.UNDO
        ScheduleResolver.nextWindow(schedule, now)?.let { Duration.between(now, it) <= SKIP_AHEAD } == true ->
            CardAction.SKIP
        else -> null
    }

private fun skippedNote(schedule: Schedule, now: LocalDateTime, format: Format): String? {
    val skipped = ScheduleResolver.pendingSkips(schedule, now).minOrNull() ?: return null
    val started = !skipped.atTime(schedule.start / 60, schedule.start % 60).isAfter(now)
    val what = if (started) "Ended early" else "Skipped"
    val next = ScheduleResolver.nextWindow(schedule, now) ?: return what
    return "$what, resumes ${format.upcomingDay(next.toLocalDate(), now.toLocalDate())}"
}

private fun minutesLeft(schedule: Schedule, now: LocalDateTime): Int? =
    minutesUntil(now) { ScheduleResolver.nextEnd(schedule, it) }

// Whole minutes from the start of the current one, so a window that starts or ends on the minute shows
// its full length.
private fun minutesUntil(now: LocalDateTime, next: (ZonedDateTime) -> ZonedDateTime?): Int? {
    val minute = now.truncatedTo(ChronoUnit.MINUTES).atZone(ZoneId.systemDefault())
    return next(minute)?.let { Duration.between(minute, it).toMinutes().toInt() }
}
