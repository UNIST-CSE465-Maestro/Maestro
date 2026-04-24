package com.maestro.app.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalProfile(
    val displayName: String? = null,
    val avatarPath: String? = null
)

class ProfileLocalDataSource(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences(
        "maestro_profile",
        Context.MODE_PRIVATE
    )
    private val profileDir =
        File(context.filesDir, "profile").also { it.mkdirs() }
    private val avatarFile = File(profileDir, "avatar.jpg")
    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<LocalProfile> = _profile.asStateFlow()

    fun getProfile(): LocalProfile = _profile.value

    private fun readProfile(): LocalProfile {
        val storedPath = prefs.getString(KEY_AVATAR_PATH, null)
        val avatarPath = storedPath?.takeIf { File(it).exists() }
        return LocalProfile(
            displayName = prefs.getString(KEY_DISPLAY_NAME, null),
            avatarPath = avatarPath
        )
    }

    suspend fun setDisplayName(name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            prefs.edit().remove(KEY_DISPLAY_NAME).apply()
        } else {
            prefs.edit().putString(KEY_DISPLAY_NAME, trimmed).apply()
        }
        _profile.value = readProfile()
    }

    suspend fun saveAvatar(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                avatarFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            prefs.edit()
                .putString(KEY_AVATAR_PATH, avatarFile.absolutePath)
                .apply()
            _profile.value = readProfile()
            avatarFile.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_PATH = "avatar_path"
    }
}
