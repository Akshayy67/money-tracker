package com.aimoneytracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.backup.BackupManager
import com.aimoneytracker.data.parser.ParserRegistry
import com.aimoneytracker.data.preferences.SettingsRepository
import com.aimoneytracker.data.repository.TransactionRepository
import com.aimoneytracker.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val workScheduler: WorkScheduler,
    private val backupManager: BackupManager,
    private val parserRegistry: ParserRegistry,
) : ViewModel() {

    val state: StateFlow<SettingsRepository.Settings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.Settings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val supportedBanks: List<String> = parserRegistry.supportedBanks()

    fun setLocalOnly(v: Boolean) = launchSet { settings.setLocalOnly(v) }
    fun setAppLock(v: Boolean) = launchSet { settings.setAppLock(v) }
    fun setBiometric(v: Boolean) = launchSet { settings.setBiometric(v) }
    fun setBlurRecents(v: Boolean) = launchSet { settings.setBlurInRecents(v) }
    fun setDynamicColor(v: Boolean) = launchSet { settings.setDynamicColor(v) }
    fun setDarkMode(v: String) = launchSet { settings.setDarkMode(v) }
    fun setWeeklyDigest(v: Boolean) = launchSet { settings.setWeeklyDigest(v); workScheduler.scheduleDigests() }
    fun setMonthlyDigest(v: Boolean) = launchSet { settings.setMonthlyDigest(v); workScheduler.scheduleDigests() }
    fun setAutoBackup(v: Boolean) = launchSet { settings.setAutoBackup(v) }

    fun reprocess() {
        viewModelScope.launch {
            val n = transactionRepository.reprocessAllRawMessages()
            _message.value = "Reprocessed messages → $n transactions"
        }
    }

    fun runBackfill() {
        workScheduler.runBackfill()
        _message.value = "SMS backfill started in the background"
    }

    fun backupNow() {
        viewModelScope.launch {
            val file = backupManager.createBackup(encrypt = true)
            _message.value = "Encrypted backup saved: ${file.name}"
        }
    }

    fun clearMessage() { _message.value = null }

    private fun launchSet(block: suspend () -> Unit) { viewModelScope.launch { block() } }
}
