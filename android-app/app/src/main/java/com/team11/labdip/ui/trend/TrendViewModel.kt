package com.team11.labdip.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.team11.labdip.LabDipApp
import com.team11.labdip.data.ReadingRepository
import com.team11.labdip.data.db.ReadingEntity
import com.team11.labdip.data.settings.AppSettings
import com.team11.labdip.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class Range(val n: Int, val label: String) { Last20(20, "Last 20"), Last100(100, "Last 100"), All(-1, "All") }

data class TrendUiState(
    val sampleId: String? = null,
    /** Oldest to newest. */
    val rows: List<ReadingEntity> = emptyList(),
    val range: Range = Range.Last100,
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
)

/** Follows the newest sample (Live switches, Trend resets with it). */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendViewModel(readings: ReadingRepository, settings: SettingsRepository) : ViewModel() {
    private val range = MutableStateFlow(Range.Last100)
    private val sampleId = readings.latest().map { it?.sampleId }.distinctUntilChanged()

    val state: StateFlow<TrendUiState> = combine(sampleId, range) { id, r -> id to r }
        .flatMapLatest { (id, r) ->
            if (id == null) flowOf(TrendUiState(range = r, loading = false))
            else readings.trend(id, r.n).map { TrendUiState(id, it.asReversed(), r, loading = false) }
        }
        .let { rows -> combine(rows, settings.settings) { s, cfg -> s.copy(settings = cfg) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendUiState())

    fun setRange(r: Range) { range.value = r }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as LabDipApp).container
                TrendViewModel(c.readings, c.settings)
            }
        }
    }
}
