package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2

@Dao
interface PlatformV2Dao {

    @Query("SELECT * FROM platform_v2 ORDER BY platform_id ASC")
    suspend fun getPlatforms(): List<PlatformV2>

    @Query("SELECT * FROM platform_v2 WHERE platform_id = :id")
    suspend fun getPlatform(id: Int): PlatformV2?

    @Insert
    suspend fun addPlatform(platform: PlatformV2): Long

    @Update
    suspend fun editPlatform(platform: PlatformV2)

    @Delete
    suspend fun deletePlatform(platform: PlatformV2)
}
