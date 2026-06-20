package com.example.player.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.darkModeDataStore: DataStore<Preferences> by preferencesDataStore(name = "dark_mode_settings")

object DarkModePreference {
    private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode_enabled")

    val darkModeEnabledFlow: (Context) -> Flow<Boolean?> = { context ->
        context.darkModeDataStore.data.map { preferences ->
            preferences[DARK_MODE_KEY]
        }
    }

    suspend fun setDarkModeEnabled(context: Context, enabled: Boolean) {
        context.darkModeDataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }

    suspend fun clearDarkModePreference(context: Context) {
        context.darkModeDataStore.edit { preferences ->
            preferences.remove(DARK_MODE_KEY)
        }
    }

    fun getDarkModeKey() = DARK_MODE_KEY

    fun getDarkModeSync(context: Context): Boolean? {
        return try {
            runBlocking {
                context.darkModeDataStore.data.first()[DARK_MODE_KEY]
            }
        } catch (_: Exception) {
            null
        }
    }
}
