package com.machandler.haptichabits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.machandler.haptichabits.data.HabitDatabase
import com.machandler.haptichabits.data.SettingsStore
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme

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

@Composable
private fun HapticHabitsApp() {
    val appContext = LocalContext.current.applicationContext
    // Created outside the NavHost so it is scoped to the activity: the session's undo history
    // survives trips to Settings, and later Settings rows (e.g. Reset) can add undoable changes.
    val counter = viewModel {
        CounterViewModel(
            HabitDatabase.getInstance(appContext).tapEventDao(),
            SettingsStore.from(appContext).settings,
        )
    }
    val vibration = remember { TapVibration(appContext) }
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            val total by counter.total.collectAsStateWithLifecycle()
            val canUndo by counter.canUndo.collectAsStateWithLifecycle()
            // Every press goes through here; buttons with other values just pass theirs in.
            fun press(weight: Int) {
                if (counter.vibratesFor(weight)) vibration.play()
                counter.recordTap(weight)
            }
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                CounterScreen(
                    total = total,
                    onTap = { press(1) },
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
                onVibrateOnPositiveChange = settingsViewModel::setVibrateOnPositive,
                onVibrateOnNegativeChange = settingsViewModel::setVibrateOnNegative,
                // Guarded so a double tap can't pop the home screen too.
                onBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
    }
}

@Composable
fun CounterScreen(
    total: Long?,
    onTap: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                // Blank while loading so a stale 0 never flashes on launch.
                text = total?.toString() ?: "",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, lineHeight = 104.sp),
            )
            Button(onClick = onTap, modifier = Modifier.size(width = 200.dp, height = 96.dp)) {
                Text(text = "+1", style = MaterialTheme.typography.displaySmall)
            }
        }
        // Pinned to the bottom, outside the centered column, so showing or hiding it never
        // moves the +1 button and it stays well clear of it.
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

@Preview(showBackground = true)
@Composable
fun CounterScreenPreview() {
    HapticHabitsTheme {
        CounterScreen(total = 42, onTap = {}, canUndo = true, onUndo = {}, onOpenSettings = {})
    }
}
