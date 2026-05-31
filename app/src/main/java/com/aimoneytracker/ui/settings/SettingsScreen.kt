package com.aimoneytracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimoneytracker.ui.components.SectionHeader

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SectionHeader("Privacy & security")
        ToggleRow("Local-only mode (disable all AI/network)", s.localOnly, viewModel::setLocalOnly)
        ToggleRow("App lock", s.appLock, viewModel::setAppLock)
        ToggleRow("Biometric unlock", s.biometric, viewModel::setBiometric)
        ToggleRow("Blur amounts in recents", s.blurInRecents, viewModel::setBlurRecents)

        HorizontalDivider()
        SectionHeader("Appearance")
        ToggleRow("Dynamic color (Material You)", s.dynamicColor, viewModel::setDynamicColor)
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                Text(
                    mode.lowercase().replaceFirstChar { it.uppercase() },
                    Modifier.padding(end = 16.dp).clickable { viewModel.setDarkMode(mode) },
                    fontWeight = if (s.darkMode == mode) FontWeight.Bold else FontWeight.Normal,
                    color = if (s.darkMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider()
        SectionHeader("Digests")
        ToggleRow("Weekly digest", s.weeklyDigest, viewModel::setWeeklyDigest)
        ToggleRow("Monthly digest", s.monthlyDigest, viewModel::setMonthlyDigest)

        HorizontalDivider()
        SectionHeader("Data")
        ToggleRow("Auto encrypted backups", s.autoBackup, viewModel::setAutoBackup)
        ActionRow("Backup now") { viewModel.backupNow() }
        ActionRow("Import last 12 months of SMS") { viewModel.runBackfill() }
        ActionRow("Reprocess stored messages") { viewModel.reprocess() }

        HorizontalDivider()
        SectionHeader("Supported formats")
        Text(viewModel.supportedBanks.joinToString(", "),
            Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)

        message?.let {
            Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.padding(24.dp)) {}
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Text(label, Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        color = MaterialTheme.colorScheme.primary)
}
