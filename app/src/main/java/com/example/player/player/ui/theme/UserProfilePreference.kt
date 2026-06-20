package com.example.player.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userProfileDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile")

object UserProfilePreference {
    private val USER_NAME_KEY = stringPreferencesKey("user_name")
    private val AVATAR_URI_KEY = stringPreferencesKey("avatar_uri")

    fun userNameFlow(context: Context): Flow<String> =
        context.userProfileDataStore.data.map { it[USER_NAME_KEY] ?: "用户" }

    fun avatarUriFlow(context: Context): Flow<String?> =
        context.userProfileDataStore.data.map { it[AVATAR_URI_KEY] }

    suspend fun setUserName(context: Context, name: String) {
        context.userProfileDataStore.edit { it[USER_NAME_KEY] = name }
    }

    suspend fun setAvatarUri(context: Context, uri: String) {
        context.userProfileDataStore.edit { it[AVATAR_URI_KEY] = uri }
    }
}
