package com.callrecorder.app.di

import android.content.Context
import androidx.room.Room
import com.callrecorder.app.data.api.ApiClient
import com.callrecorder.app.data.local.AppDb
import com.callrecorder.app.data.local.MIGRATION_3_4
import com.callrecorder.app.data.local.MIGRATION_4_5
import com.callrecorder.app.data.local.TokenStore
import com.callrecorder.app.data.repository.AuthRepository
import com.callrecorder.app.data.repository.CallRepository
import com.callrecorder.app.data.repository.StoreRepository
import com.callrecorder.app.util.RecordingScanner
import com.callrecorder.app.data.repository.CalendarRepository
import com.callrecorder.app.data.repository.NotesRepository
import com.callrecorder.app.data.repository.KeywordRepository
import com.callrecorder.app.data.repository.CustomerRepository

class AppContainer(context: Context) {
    val tokenStore = TokenStore(context)
    val api = ApiClient.create(tokenStore)

    private val db = Room.databaseBuilder(context, AppDb::class.java, "callrec.db")
        .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
        .fallbackToDestructiveMigration()
        .build()
    val recordingDao = db.recordingDao()
    private val manualCalendarEventDao = db.manualCalendarEventDao()

    val scanner = RecordingScanner(context)

    val authRepo = AuthRepository(api, tokenStore)
    val storeRepo = StoreRepository(api, tokenStore)
    val callRepo = CallRepository(api, recordingDao)

    val calendarRepo = CalendarRepository(api, manualCalendarEventDao)

    val notesRepo = NotesRepository(api)

    val keywordRepo = KeywordRepository(api)

    val customerRepo = CustomerRepository(api)

    // 외부 캘린더 OAuth 딥링크 콜백 브릿지 (Activity -> Compose)
    val calendarOAuthBridge = CalendarOAuthBridge()
}
