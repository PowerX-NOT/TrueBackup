package dev.truebackup.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.truebackup.app.root.RootPreflight
import dev.truebackup.app.root.RootPreflightResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    val preflight = remember { RootPreflight() }
    var isChecking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RootPreflightResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("TrueBackup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Root-privileged standalone backup suite.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Root status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                if (isChecking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isChecking) return@Button
                        isChecking = true
                        scope.launch {
                            result = withContext(Dispatchers.IO) { preflight.verify() }
                            isChecking = false
                        }
                    }
                ) {
                    Text(if (isChecking) "Checking..." else "Run root preflight")
                }
                result?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(it.message, style = MaterialTheme.typography.bodyMedium)
                    if (it.output.isNotBlank()) {
                        Text("Output: ${it.output}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
