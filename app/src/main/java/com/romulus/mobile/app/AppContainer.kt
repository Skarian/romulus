package com.romulus.mobile.app

import android.content.Context
import androidx.room.Room
import com.romulus.mobile.core.files.FilenameCollisionResolver
import com.romulus.mobile.core.time.ClockProvider
import com.romulus.mobile.core.time.SystemClockProvider
import com.romulus.mobile.data.downloads.QueueCommandBus
import com.romulus.mobile.data.downloads.QueueRuntimeStore
import com.romulus.mobile.data.downloads.HttpTransferEngine
import com.romulus.mobile.data.downloads.RoomQueueRepository
import com.romulus.mobile.data.downloads.local.RomulusDatabase
import com.romulus.mobile.data.files.FileSelectionRepository
import com.romulus.mobile.data.files.SafFileStore
import com.romulus.mobile.data.realdebrid.RealDebridClient
import com.romulus.mobile.data.security.KeystoreApiKeyCipher
import com.romulus.mobile.data.settings.PreferencesSettingsRepository
import com.romulus.mobile.data.source.DefaultSourceRepository
import com.romulus.mobile.worker.QueueController
import okhttp3.OkHttpClient

class AppContainer(
    appContext: Context
) {
    val clockProvider: ClockProvider = SystemClockProvider()
    val filenameCollisionResolver: FilenameCollisionResolver = FilenameCollisionResolver.Default()
    val apiKeyCipher = KeystoreApiKeyCipher()
    val settingsRepository = PreferencesSettingsRepository(appContext, apiKeyCipher)
    val sourceRepository = DefaultSourceRepository(appContext, settingsRepository, clockProvider)
    val realDebridClient = RealDebridClient.create(clockProvider)
    val fileSelectionRepository = FileSelectionRepository(realDebridClient)
    val safFileStore = SafFileStore(appContext, filenameCollisionResolver)
    val transferEngine = HttpTransferEngine(OkHttpClient(), safFileStore)
    val queueRuntimeStore = QueueRuntimeStore()
    val queueCommandBus = QueueCommandBus()
    val database: RomulusDatabase = Room.databaseBuilder(
        appContext,
        RomulusDatabase::class.java,
        "romulus.db"
    )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()
    val queueRepository = RoomQueueRepository(
        taskDao = database.downloadTaskDao(),
        runDao = database.queueRunDao(),
        clockProvider = clockProvider
    )
    val queueController = QueueController(
        context = appContext,
        queueRepository = queueRepository,
        safFileStore = safFileStore,
        queueRuntimeStore = queueRuntimeStore,
        queueCommandBus = queueCommandBus
    )
}
