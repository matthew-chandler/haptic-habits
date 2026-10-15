package com.machandler.haptichabits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.machandler.haptichabits.data.HabitButton
import com.machandler.haptichabits.data.HabitDatabase
import com.machandler.haptichabits.data.SettingsStore
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme
import com.machandler.haptichabits.ui.theme.valueColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HapticHabitsTheme {
                HapticHabitsApp()
            }
        }
    }
}

private const val HOME_ROUTE = "home"
private const val SETTINGS_ROUTE = "settings"
private const val BUTTONS_ROUTE = "buttons"
private const val HISTORY_ROUTE = "history"

@Composable
private fun HapticHabitsApp() {
    val appContext = LocalContext.current.applicationContext
    val database = remember { HabitDatabase.getInstance(appContext) }
    // Created outside the NavHost so it is scoped to the activity: the session's undo history
    // survives trips to Settings, and later Settings rows (e.g. Reset) can add undoable changes.
    val counter = viewModel {
        CounterViewModel(
            database.tapEventDao(),
            database.habitButtonDao().observeActive(),
            SettingsStore.from(appContext).settings,
        )
    }
    val vibration = remember { TapVibration(appContext) }
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            val total by counter.total.collectAsStateWithLifecycle()
            val buttons by counter.buttons.collectAsStateWithLifecycle()
            val canUndo by counter.canUndo.collectAsStateWithLifecycle()
            val invertColors by counter.invertColors.collectAsStateWithLifecycle()
            // Every press goes through here: vibrate by the sign of the button's value, then record it.
            fun press(button: HabitButton) {
                if (counter.vibratesFor(button.weight)) vibration.play()
                counter.recordTap(button)
            }
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                CounterScreen(
                    total = total,
                    // The buttons wait for the color setting so they never show the wrong colors first.
                    buttons = buttons.takeIf { invertColors != null },
                    invertColors = invertColors == true,
                    onPress = ::press,
                    canUndo = canUndo,
                    // Deliberately silent: only presses vibrate.
                    onUndo = counter::undo,
                    // Ignores a second tap while the first navigation is still in progress.
                    onOpenSettings = dropUnlessResumed { navController.navigate(SETTINGS_ROUTE) },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
        composable(SETTINGS_ROUTE) {
            val settingsViewModel = viewModel { SettingsViewModel(SettingsStore.from(appContext)) }
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            SettingsScreen(
                settings = settings,
                onOpenButtons = dropUnlessResumed { navController.navigate(BUTTONS_ROUTE) },
                onOpenHistory = dropUnlessResumed { navController.navigate(HISTORY_ROUTE) },
                onVibrateOnPositiveChange = settingsViewModel::setVibrateOnPositive,
                onVibrateOnNegativeChange = settingsViewModel::setVibrateOnNegative,
                onInvertColorsChange = settingsViewModel::setInvertColors,
                // Guarded so a double tap can't pop the home screen too.
                onBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable(BUTTONS_ROUTE) {
            val buttonsViewModel = viewModel { ButtonsViewModel(database.habitButtonDao()) }
            val buttons by buttonsViewModel.buttons.collectAsStateWithLifecycle()
            ButtonsScreen(
                buttons = buttons,
                onAdd = buttonsViewModel::add,
                onUpdate = buttonsViewModel::update,
                onDelete = buttonsViewModel::delete,
                onBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable(HISTORY_ROUTE) {
            val historyViewModel = viewModel { HistoryViewModel(database.tapEventDao()) }
            val rows by historyViewModel.rows.collectAsStateWithLifecycle()
            val invertColors by counter.invertColors.collectAsStateWithLifecycle()
            HistoryScreen(
                // Like the home buttons, the values wait for the color setting.
                rows = rows.takeIf { invertColors != null },
                invertColors = invertColors == true,
                // Through the counter, so a deleted change from this session also leaves the undo
                // list. Deliberately silent: only presses vibrate.
                onDelete = counter::deleteEntry,
                onBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
    }
}

/**
 * The home screen: the total, large, above the active buttons. The total stays put; the buttons
 * scroll beneath it when there are more than fit.
 *
 * [total] and [buttons] are null while loading. Buttons are green for positive values and red for
 * negative ones, and the total flashes green going up and red going down; [invertColors] swaps
 * both.
 */
@Composable
fun CounterScreen(
    total: Long?,
    buttons: List<HabitButton>?,
    invertColors: Boolean,
    onPress: (HabitButton) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(TOTAL_AREA_WEIGHT)
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                FlashingTotal(total = total, invertColors = invertColors)
            }
            val buttonAreaModifier = Modifier
                .fillMaxWidth()
                .weight(BUTTON_AREA_WEIGHT)
            when {
                buttons == null -> Spacer(buttonAreaModifier)
                buttons.isEmpty() -> Text(
                    text = "No buttons. Tap the gear, then Buttons, to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = buttonAreaModifier.padding(horizontal = 32.dp),
                )
                else -> LazyColumn(
                    modifier = buttonAreaModifier,
                    // The bottom padding lets the last button scroll clear of the Undo button.
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(buttons, key = { it.id }) { button ->
                        HabitButtonView(button = button, invertColors = invertColors, onClick = { onPress(button) })
                    }
                }
            }
        }
        // Pinned to the bottom, outside the column, so showing or hiding it never moves the
        // buttons.
        if (canUndo) {
            OutlinedButton(
                onClick = onUndo,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_undo),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = "Undo")
            }
        }
        // Last in the Box so it sits on top of the full-screen column.
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = "Settings")
        }
    }
}

// How the screen height is split between the total (above) and the buttons (below).
private const val TOTAL_AREA_WEIGHT = 2f
private const val BUTTON_AREA_WEIGHT = 3f

/**
 * One habit button, colored by the sign of its value: its name, and below it its value (unless
 * the name already says just that, like "+1").
 */
@Composable
private fun HabitButtonView(button: HabitButton, invertColors: Boolean, onClick: () -> Unit) {
    val colors = valueColors(button.weight.toLong(), invertColors)
    Button(
        onClick = onClick,
        // A value is never 0, but fall back to the theme's colors rather than guess.
        colors = if (colors != null) {
            ButtonDefaults.buttonColors(containerColor = colors.color, contentColor = colors.onColor)
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .heightIn(min = 80.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = button.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val value = ButtonRules.formatValue(button.weight)
            if (value != button.name) {
                Text(text = value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CounterScreenPreview() {
    HapticHabitsTheme {
        CounterScreen(
            total = 42,
            invertColors = false,
            buttons = listOf(
                HabitButton(id = 1, name = "+1", weight = 1, position = 0),
                HabitButton(id = 2, name = "Doomscrolling on the phone in bed at night", weight = 5, position = 1),
                HabitButton(id = 3, name = "Went for a walk", weight = -2, position = 2),
            ),
            onPress = {},
            canUndo = true,
            onUndo = {},
            onOpenSettings = {},
        )
    }
}

@Preview
@Composable
fun CounterScreenDarkPreview() {
    HapticHabitsTheme(darkTheme = true, dynamicColor = false) {
        // A Surface gives the dark background and the matching text color, as the Scaffold does.
        Surface {
            CounterScreen(
                total = 42,
                invertColors = false,
                buttons = listOf(
                    HabitButton(id = 1, name = "+1", weight = 1, position = 0),
                    HabitButton(id = 3, name = "Went for a walk", weight = -2, position = 2),
                ),
                onPress = {},
                canUndo = true,
                onUndo = {},
                onOpenSettings = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CounterScreenNoButtonsPreview() {
    HapticHabitsTheme {
        CounterScreen(total = 7, buttons = emptyList(), invertColors = false, onPress = {}, canUndo = false, onUndo = {}, onOpenSettings = {})
    }
}
