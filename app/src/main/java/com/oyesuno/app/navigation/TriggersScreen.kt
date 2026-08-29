package com.oyesuno.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oyesuno.feature.scheduler.SchedulerScreen
import com.oyesuno.feature.wakeword.WakeWordScreen

@Composable
fun TriggersScreen(
    onStartRecording: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Triggers", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        // Scheduled recordings section
        SchedulerScreen()
        Spacer(modifier = Modifier.height(24.dp))
        // Wake word section
        WakeWordScreen(onStartRecording = onStartRecording)
    }
}
