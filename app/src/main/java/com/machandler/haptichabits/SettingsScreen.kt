package com.machandler.haptichabits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.machandler.haptichabits.data.AppSettings
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme

/**
 * The Settings screen: a plain scrolling list of rows (Buttons, History and Trends open their own
 * screens, then the switches). Last, set apart below a divider, is "Reset total to 0", which asks
 * for confirmation and then calls [onReset].
 *
 * [settings] is null while loading; the rows that show stored values wait for it, and so does
 * the Reset row below them, so it never jumps down when they appear. [total] is the current
 * total (null while loading); Reset is disabled while it is 0, since there is nothing to reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings?,
    total: Long?,
    onOpenButtons: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTrends: () -> Unit,
    onVibrateOnPositiveChange: (Boolean) -> Unit,
    onVibrateOnNegativeChange: (Boolean) -> Unit,
    onInvertColorsChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whether the reset confirmation is open; saveable so rotating the phone keeps it open.
    var confirmingReset by rememberSaveable { mutableStateOf(false) }
    val canReset = total != null && total != 0L

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text("Buttons") },
                supportingContent = { Text("Add, rename, change the value of, or delete buttons") },
                modifier = Modifier.clickable(onClick = onOpenButtons),
            )
            ListItem(
                headlineContent = { Text("History") },
                supportingContent = { Text("Every entry with the total after it; delete entries") },
                modifier = Modifier.clickable(onClick = onOpenHistory),
            )
            ListItem(
                headlineContent = { Text("Trends") },
                supportingContent = { Text("The total over time, and presses per button by day, week or month") },
                modifier = Modifier.clickable(onClick = onOpenTrends),
            )
            if (settings != null) {
                SwitchRow(
                    label = "Vibrate on positive presses",
                    checked = settings.vibrateOnPositive,
                    onCheckedChange = onVibrateOnPositiveChange,
                )
                SwitchRow(
                    label = "Vibrate on negative presses",
                    checked = settings.vibrateOnNegative,
                    onCheckedChange = onVibrateOnNegativeChange,
                )
                SwitchRow(
                    label = "Invert colors",
                    supporting = "Positive red, negative green",
                    checked = settings.invertColors,
                    onCheckedChange = onInvertColorsChange,
                )
                // Set apart from the everyday rows: it's the one that changes the total.
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ResetRow(total = total, enabled = canReset, onClick = { confirmingReset = true })
            }
        }
    }

    if (confirmingReset && canReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            icon = { Icon(painter = painterResource(R.drawable.ic_reset), contentDescription = null) },
            title = { Text("Reset total to 0?") },
            text = {
                Text(
                    "The total goes from $total to 0. Nothing is deleted: History keeps every entry " +
                        "and shows the reset as its own row. Until you close the app, Undo on the home " +
                        "screen takes it back, and you can delete it from History at any time to bring " +
                        "the total back.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        confirmingReset = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * "Reset total to 0", in the theme's error color so it reads as a significant action, with what
 * it does underneath. Disabled (and saying why) while the total is already 0.
 */
@Composable
private fun ResetRow(total: Long?, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    }
    ListItem(
        headlineContent = { Text("Reset total to 0", fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Text(if (total == 0L) "The total is already 0" else "Start again from 0; History keeps every entry")
        },
        leadingContent = { Icon(painter = painterResource(R.drawable.ic_reset), contentDescription = null) },
        colors = ListItemDefaults.colors(
            headlineColor = color,
            leadingIconColor = color,
            supportingColor = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
            },
        ),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

// Material's opacity for disabled content.
private const val DISABLED_ALPHA = 0.38f

/** A list row with a switch, and an optional line explaining it; the whole row toggles it. */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    HapticHabitsTheme {
        SettingsScreen(
            settings = AppSettings(vibrateOnPositive = true, vibrateOnNegative = false),
            total = 42,
            onOpenButtons = {},
            onOpenHistory = {},
            onOpenTrends = {},
            onVibrateOnPositiveChange = {},
            onVibrateOnNegativeChange = {},
            onInvertColorsChange = {},
            onReset = {},
            onBack = {},
        )
    }
}
