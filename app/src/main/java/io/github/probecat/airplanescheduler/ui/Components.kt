package io.github.probecat.airplanescheduler.ui

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.probecat.airplanescheduler.R

val ContentWidth = 600.dp
val CompactHeight = 480.dp

// In short windows the top bar scrolls away with the content, and comes back when scrolling up.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberTopBarScroll(): TopAppBarScrollBehavior {
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    return if (height < CompactHeight) {
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    } else {
        TopAppBarDefaults.pinnedScrollBehavior()
    }
}

const val SOURCE_URL = "https://github.com/probecat/airplane-scheduler-app"
const val ISSUES_URL = "$SOURCE_URL/issues"

@Composable
fun SwitchRow(
    icon: Int?,
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingRow(
        icon = icon,
        title = title,
        enabled = enabled,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onChange,
        ),
    )
}

@Composable
fun LinkRow(icon: Int, title: String, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SettingRow(
        icon = icon,
        title = title,
        trailing = {
            Icon(
                painterResource(R.drawable.ic_open_in_new),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier,
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
    )
}

@Composable
fun SettingRow(
    icon: Int?,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    enabled: Boolean = true,
    tint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyLarge
    SubcomposeLayout(modifier) { constraints ->
        val actionWidth = trailing?.let { action ->
            subcompose("action", action).maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) }
        } ?: 0
        val fixed = (ROW_PADDING * 2 + ITEM_PADDING * 2 + (if (icon != null) ICON_SPACE else 0.dp) + ITEM_PADDING)
            .roundToPx()
        val room = constraints.maxWidth - fixed - actionWidth
        val word = title.split(' ').maxOfOrNull { measurer.measure(it, style, softWrap = false).size.width } ?: 0
        val below = trailing != null && room < word
        val row = subcompose("row") {
            SettingItem(
                icon,
                title,
                text,
                enabled,
                tint,
                if (below) null else trailing,
                below = if (below) trailing else null,
                onClick,
            )
        }.map { it.measure(constraints) }
        layout(constraints.maxWidth, row.sumOf { it.height }) { row.forEach { it.place(0, 0) } }
    }
}

@Composable
private fun SettingItem(
    icon: Int?,
    title: String,
    text: String?,
    enabled: Boolean,
    tint: Color?,
    trailing: (@Composable () -> Unit)?,
    below: (@Composable () -> Unit)?,
    onClick: (() -> Unit)?,
) {
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = when {
            below != null -> {
                {
                    Column {
                        text?.let { Text(it) }
                        Box(Modifier.offset(x = (-12).dp)) { below() }
                    }
                }
            }
            text != null -> {
                { Text(text) }
            }
            else -> null
        },
        leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        trailingContent = trailing,
        colors = when {
            !enabled -> ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = disabled,
                supportingColor = disabled,
                leadingIconColor = disabled,
            )
            tint != null -> ListItemDefaults.colors(
                containerColor = Color.Transparent,
                supportingColor = tint,
                leadingIconColor = tint,
            )
            else -> ListItemDefaults.colors(containerColor = Color.Transparent)
        },
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = ROW_PADDING),
    )
}

private val ROW_PADDING = 8.dp
private val ITEM_PADDING = 16.dp
private val ICON_SPACE = 40.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissibleSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        key(data) {
            SwipeToDismissBox(
                state = rememberSwipeToDismissBoxState(),
                backgroundContent = {},
                onDismiss = { data.dismiss() },
            ) {
                // A raised surface instead of the inverse colors, which are harsh in the light theme.
                Snackbar(
                    data,
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Subpage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = rememberTopBarScroll()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = ContentWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                content = content,
            )
        }
    }
}

class MenuItem(val label: String, @param:DrawableRes val icon: Int, val onClick: () -> Unit)

@Composable
fun OverflowMenu(items: List<MenuItem>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (item in items) {
                DropdownMenuItem(
                    text = { Text(item.label) },
                    leadingIcon = { Icon(painterResource(item.icon), contentDescription = null) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}
