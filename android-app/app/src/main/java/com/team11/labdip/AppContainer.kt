package com.team11.labdip

import android.content.Context
import androidx.room.Room
import com.team11.labdip.data.CameraRepository
import com.team11.labdip.data.ReadingRepository
import com.team11.labdip.data.db.AppDatabase
import com.team11.labdip.data.mqtt.MqttRepository
import com.team11.labdip.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Manual DI: one instance of each repository for the whole process. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "labdip.db").build()
    val readings = ReadingRepository(db.readings())
    val settings = SettingsRepository(context)
    val mqtt = MqttRepository()
    val camera = CameraRepository()

    init {
        scope.launch { mqtt.messages.collect { readings.ingest(it) } }
        scope.launch { mqtt.camera.collect { camera.ingest(it) } }
    }

    /** App came to foreground: connect if the user has set up a broker. */
    fun start() {
        scope.launch {
            val s = settings.settings.first()
            if (s.onboarded) mqtt.connect(s.broker)
        }
    }

    fun stop() = mqtt.disconnect()
}
