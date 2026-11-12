package com.team11.labdip.data

import com.team11.labdip.data.model.CamParser
import com.team11.labdip.data.model.CamShot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Latest camera-station result from `.../cam`, validated by [CamParser]. */
// shortcut: camera result kept in memory only, add a Room table if camera history is needed
class CameraRepository(private val clock: () -> Long = System::currentTimeMillis) {
    private val _latest = MutableStateFlow<CamShot?>(null)
    val latest: StateFlow<CamShot?> = _latest

    private val _dropped = MutableStateFlow(0)
    /** Camera messages rejected by the parser since app start. */
    val dropped: StateFlow<Int> = _dropped

    fun ingest(bytes: ByteArray) {
        CamParser.parse(bytes).fold(
            onSuccess = { _latest.value = CamShot(it, clock()) },
            onFailure = { _dropped.update { n -> n + 1 } },
        )
    }
}
