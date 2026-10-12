package com.emilio.deutschdrill

import android.app.Application
import com.emilio.deutschdrill.data.DrillDatabase
import com.emilio.deutschdrill.data.DrillRepository
import com.emilio.deutschdrill.data.SettingsStore
import com.emilio.deutschdrill.domain.CardJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; the app is small enough not to need a DI framework. */
class DrillApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: DrillRepository
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        repository = DrillRepository(DrillDatabase.create(this), settings)
        appScope.launch { seedIfNeeded() }
    }

    /** Loads the bundled seed decks once per seed version; re-seeding keeps learning history. */
    private suspend fun seedIfNeeded() {
        if (settings.seedVersion >= SEED_VERSION) return
        val files = assets.list("seed").orEmpty().filter { it.endsWith(".json") }.sorted()
        for (file in files) {
            val text = assets.open("seed/$file").use { it.readBytes().toString(Charsets.UTF_8) }
            repository.import(CardJson.parse(text, defaultSource = "seed-" + file.removeSuffix(".json")), System.currentTimeMillis())
        }
        settings.seedVersion = SEED_VERSION
    }

    companion object {
        const val SEED_VERSION = 1
    }
}
