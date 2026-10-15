package com.machandler.haptichabits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.machandler.haptichabits.data.HabitDatabase
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HapticHabitsTheme {
                val counter = viewModel {
                    CounterViewModel(HabitDatabase.getInstance(applicationContext).tapEventDao())
                }
                val total by counter.total.collectAsStateWithLifecycle()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CounterScreen(
                        total = total,
                        onTap = counter::recordTap,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
fun CounterScreen(total: Long?, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
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
}

@Preview(showBackground = true)
@Composable
fun CounterScreenPreview() {
    HapticHabitsTheme {
        CounterScreen(total = 42, onTap = {})
    }
}
