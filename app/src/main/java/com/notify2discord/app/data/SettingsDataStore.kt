package com.notify2discord.app.data

import android.content.Context
import androidx.datastore.core.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.api.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore

// アプリ設定を保存する DataStore
// corruptionHandler: ファイル損傷時に空の設定で復元し、クラッシュを防止する
val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)
