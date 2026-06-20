package com.example.player.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.themeColorDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_color_settings")

object ThemeColorPreference {
    private val THEME_COLOR_KEY = intPreferencesKey("theme_color_index")

    fun themeColorIndexFlow(context: Context): Flow<Int> =
        context.themeColorDataStore.data.map { preferences ->
            preferences[THEME_COLOR_KEY] ?: 2
        }

    suspend fun setThemeColorIndex(context: Context, index: Int) {
        context.themeColorDataStore.edit { preferences ->
            preferences[THEME_COLOR_KEY] = index
        }
    }

    fun getThemeColorIndexSync(context: Context): Int {
        return try {
            runBlocking {
                context.themeColorDataStore.data.first()[THEME_COLOR_KEY] ?: 2
            }
        } catch (_: Exception) {
            2
        }
    }
}
