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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.machandler.haptichabits.data.HabitDatabase
import com.machandler.haptichabits.data.SettingsStore
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HapticHabitsTheme {
                val counter = viewModel {
                    CounterViewModel(
                        HabitDatabase.getInstance(applicationContext).tapEventDao(),
                        SettingsStore.from(applicationContext).settings,
                    )
                }
                val vibration = remember { TapVibration(applicationContext) }
                val total by counter.total.collectAsStateWithLifecycle()
                val canUndo by counter.canUndo.collectAsStateWithLifecycle()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CounterScreen(
                        total = total,
                        onTap = {
                            if (counter.vibratesFor(1)) vibration.play()
                            counter.recordTap(1)
                        },
                        canUndo = canUndo,
                        // Deliberately silent: only presses vibrate.
                        onUndo = counter::undo,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
fun CounterScreen(
    total: Long?,
    onTap: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
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
    }
}

@Preview(showBackground = true)
@Composable
fun CounterScreenPreview() {
    HapticHabitsTheme {
        CounterScreen(total = 42, onTap = {}, canUndo = true, onUndo = {})
    }
}
