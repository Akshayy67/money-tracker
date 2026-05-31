package com.aimoneytracker.ui.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.domain.forecast.ForecastResult
import com.aimoneytracker.domain.usecase.GenerateForecastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val generateForecast: GenerateForecastUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ForecastResult?>(null)
    val state: StateFlow<ForecastResult?> = _state.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _state.value = runCatching { generateForecast(persist = false) }.getOrNull()
            _loading.value = false
        }
    }
}
