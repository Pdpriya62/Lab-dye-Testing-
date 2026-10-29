package com.team11.labdip.ui.samples

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.team11.labdip.LabDipApp
import com.team11.labdip.data.ReadingRepository
import com.team11.labdip.data.db.ReadingEntity
import com.team11.labdip.data.db.SampleSummary
import com.team11.labdip.data.db.toCsv
import com.team11.labdip.data.model.ReadingParser
import com.team11.labdip.data.model.Status
import com.team11.labdip.data.model.statusFor
import com.team11.labdip.data.settings.AppSettings
import com.team11.labdip.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SampleRow(val summary: SampleSummary, val status: Status)

data class SamplesUiState(val rows: List<SampleRow> = emptyList(), val loading: Boolean = true)

class SamplesViewModel(private val readings: ReadingRepository, settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<SamplesUiState> = combine(readings.samples(), settings.settings) { list, s ->
        SamplesUiState(list.map { SampleRow(it, statusFor(it.lastDeltaE, s.passMax, s.marginalMax)) }, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SamplesUiState())

    private var undo: List<ReadingEntity> = emptyList()

    fun delete(sampleId: String) {
        viewModelScope.launch { undo = readings.deleteSample(sampleId) }
    }

    fun undoDelete() {
        val rows = undo
        undo = emptyList()
        if (rows.isNotEmpty()) viewModelScope.launch { readings.restore(rows) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as LabDipApp).container
                SamplesViewModel(c.readings, c.settings)
            }
        }
    }
}

data class SampleDetailUiState(
    val sampleId: String,
    val rows: List<ReadingEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
)

class SampleDetailViewModel(
    private val app: Application,
    private val readings: ReadingRepository,
    settings: SettingsRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val sampleId: String = checkNotNull(handle["id"])

    val state: StateFlow<SampleDetailUiState> = combine(readings.trend(sampleId, -1), settings.settings) { rows, s ->
        SampleDetailUiState(sampleId, rows.asReversed(), s, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SampleDetailUiState(sampleId))

    /** Writes the CSV to the app cache and returns a FileProvider URI for the share sheet. */
    suspend fun exportCsv(): Uri = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        // sampleId is already whitelisted by ReadingParser; clean again since it becomes a file name
        val name = (ReadingParser.cleanText(sampleId) ?: "sample").replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val file = File(dir, "labdip_$name.csv")
        file.writeText(toCsv(readings.allFor(sampleId)))
        FileProvider.getUriForFile(app, "${app.packageName}.files", file)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LabDipApp
                SampleDetailViewModel(app, app.container.readings, app.container.settings, createSavedStateHandle())
            }
        }
    }
}
