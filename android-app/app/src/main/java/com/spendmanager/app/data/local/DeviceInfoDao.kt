package com.spendmanager.app.data.local

import androidx.room.*
import com.spendmanager.app.data.model.DeviceInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceInfoDao {
    @Query("SELECT * FROM device_info WHERE id = 1")
    suspend fun get(): DeviceInfo?

    @Query("SELECT * FROM device_info WHERE id = 1")
    fun observe(): Flow<DeviceInfo?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(info: DeviceInfo)

    @Query("DELETE FROM device_info")
    suspend fun clear()
}
