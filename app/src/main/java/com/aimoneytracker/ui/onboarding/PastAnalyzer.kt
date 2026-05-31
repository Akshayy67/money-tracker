package com.aimoneytracker.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.aimoneytracker.data.preferences.SettingsRepository
import com.aimoneytracker.work.SmsBackfillWorker
import com.aimoneytracker.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyzerUiState(
    val status: Status = Status.IDLE,
    val pct: Int = 0,
    val scanned: Int = 0,
    val found: Int = 0,
    val total: Int = 0,
) {
    enum class Status { IDLE, RUNNING, DONE }
}

@HiltViewModel
class PastAnalyzerViewModel @Inject constructor(
    private val workScheduler: WorkScheduler,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<AnalyzerUiState> = workScheduler.observeBackfill().map { infos ->
        when (val info = infos.firstOrNull()) {
            null -> AnalyzerUiState()
            else -> when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    val d = info.progress
                    AnalyzerUiState(
                        status = AnalyzerUiState.Status.RUNNING,
                        pct = d.getInt(SmsBackfillWorker.KEY_PROGRESS, 0),
                        scanned = d.getInt(SmsBackfillWorker.KEY_SCANNED, 0),
                        found = d.getInt(SmsBackfillWorker.KEY_FOUND, 0),
                        total = d.getInt(SmsBackfillWorker.KEY_TOTAL, 0),
                    )
                }
                else -> {
                    val d = info.outputData
                    AnalyzerUiState(
                        status = AnalyzerUiState.Status.DONE,
                        pct = 100,
                        scanned = d.getInt(SmsBackfillWorker.KEY_SCANNED, 0),
                        found = d.getInt(SmsBackfillWorker.KEY_FOUND, 0),
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyzerUiState())

    fun start(days: Int) = workScheduler.runBackfill(days)

    fun markOnboarded() {
        viewModelScope.launch { settings.setOnboarded(true) }
    }
}

/**
 * Past-SMS analyser screen (§2). The user picks how far back to scan; the app reads those SMS,
 * parses them, and fills the database with live progress. Shown on first run and re-runnable later.
 */
@Composable
fun PastAnalyzerScreen(onComplete: () -> Unit, viewModel: PastAnalyzerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var days by remember { mutableIntStateOf(365) }
    var customDays by remember { mutableStateOf("") }
    var permissionDenied by remember { mutableStateOf(false) }

    // Effective range: a valid custom value wins over the chip selection; clamp to 1..3650 days.
    val effectiveDays = customDays.toIntOrNull()?.coerceIn(1, 3650) ?: days
    fun rangeLabel(d: Int) = when {
        d >= 365 && d % 365 == 0 -> "${d / 365} year${if (d / 365 > 1) "s" else ""}"
        else -> "$d days"
    }

    val permissions = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_SMS] == true) {
            permissionDenied = false
            viewModel.start(effectiveDays)
        } else {
            permissionDenied = true
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Analyze your past transactions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "We'll scan your past bank & UPI SMS, parse them, and fill in your history so the app is " +
                "useful right away. Nothing leaves your device.",
            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(20.dp))
        Text("How far back?", fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 90, 180, 365, 730).forEach { d ->
                FilterChip(
                    // A chip is "selected" only when no custom value overrides it.
                    selected = customDays.isBlank() && days == d,
                    onClick = { days = d; customDays = "" },
                    label = { Text(rangeLabel(d)) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = customDays,
            onValueChange = { input -> customDays = input.filter { it.isDigit() }.take(4) },
            label = { Text("Or enter exact number of days") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                customDays.toIntOrNull()?.let {
                    if (it !in 1..3650) Text("Enter 1–3650 days", color = MaterialTheme.colorScheme.error)
                    else Text("Will scan the last $it days")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        when (state.status) {
            AnalyzerUiState.Status.RUNNING -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Analyzing…", fontWeight = FontWeight.Medium)
                        LinearProgressIndicator(
                            progress = { state.pct / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                        Text("Scanned ${state.scanned} of ${state.total} messages • found ${state.found} transactions",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            AnalyzerUiState.Status.DONE -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("All done!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Scanned ${state.scanned} messages and imported ${state.found} transactions.",
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.markOnboarded(); onComplete() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continue") }
            }
            AnalyzerUiState.Status.IDLE -> {
                Button(
                    onClick = { launcher.launch(permissions) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Analyze last ${rangeLabel(effectiveDays)}") }

                if (permissionDenied) {
                    Text("SMS permission is needed to read your past transactions. You can also grant it later in Settings.",
                        Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.markOnboarded(); onComplete() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Skip for now") }
            }
        }
    }
}
