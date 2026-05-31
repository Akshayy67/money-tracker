package com.aimoneytracker.ui.digest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.entity.DigestRecordEntity
import com.aimoneytracker.data.repository.DigestRepository
import com.aimoneytracker.domain.digest.DigestContent
import com.aimoneytracker.domain.model.DigestType
import com.aimoneytracker.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class DigestListViewModel @Inject constructor(
    private val repository: DigestRepository,
    private val workScheduler: WorkScheduler,
) : ViewModel() {
    val digests: StateFlow<List<DigestRecordEntity>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun generateNow(type: DigestType) { workScheduler.runDigestNow(type.name) }
}

@HiltViewModel
class DigestDetailViewModel @Inject constructor(
    private val repository: DigestRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val id: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1

    private val _content = MutableStateFlow<DigestContent?>(null)
    val content: StateFlow<DigestContent?> = _content.asStateFlow()

    private val _summary = MutableStateFlow<String?>(null)
    val summary: StateFlow<String?> = _summary.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getById(id)?.let { rec ->
                _content.value = runCatching { json.decodeFromString(DigestContent.serializer(), rec.bodyJson) }.getOrNull()
                _summary.value = rec.naturalLanguageSummary
                repository.markOpened(id)
            }
        }
    }
}
