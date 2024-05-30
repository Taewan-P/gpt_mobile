package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting

interface SettingRepository {
    suspend fun fetchPlatforms(): List<Platform>
    suspend fun fetchThemes(): ThemeSetting
    suspend fun updatePlatforms(platforms: List<Platform>)
    suspend fun updateThemes(themeSetting: ThemeSetting)
}
