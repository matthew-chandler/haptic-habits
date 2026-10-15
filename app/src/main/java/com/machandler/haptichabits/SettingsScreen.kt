package com.machandler.haptichabits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.machandler.haptichabits.data.AppSettings
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme

/**
 * The Settings screen: a plain scrolling list of rows. Later roadmap steps add their rows
 * (History, Reset, Trends) to the same list.
 *
 * [settings] is null while loading; the rows that show stored values wait for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings?,
    onOpenButtons: () -> Unit,
    onVibrateOnPositiveChange: (Boolean) -> Unit,
    onVibrateOnNegativeChange: (Boolean) -> Unit,
    onInvertColorsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            }
        }
    }
}

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
            onOpenButtons = {},
            onVibrateOnPositiveChange = {},
            onVibrateOnNegativeChange = {},
            onInvertColorsChange = {},
            onBack = {},
        )
    }
}
