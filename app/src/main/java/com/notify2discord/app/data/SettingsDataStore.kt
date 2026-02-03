package com.notify2discord.app.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

// アプリ設定を保存する DataStore
val Context.settingsDataStore by preferencesDataStore(name = "settings")
