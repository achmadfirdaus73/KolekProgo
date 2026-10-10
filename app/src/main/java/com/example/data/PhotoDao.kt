package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photo_records ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<PhotoRecord>>

    @Query("SELECT * FROM photo_records WHERE syncStatus = :status ORDER BY timestamp DESC")
    fun getPhotosBySyncStatus(status: String): Flow<List<PhotoRecord>>

    @Query("SELECT * FROM photo_records WHERE syncStatus != 'SYNCED' ORDER BY timestamp ASC")
    suspend fun getPendingSyncPhotosList(): List<PhotoRecord>

    @Query("SELECT * FROM photo_records WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: Long): PhotoRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoRecord): Long

    @Update
    suspend fun update(photo: PhotoRecord)

    @Delete
    suspend fun delete(photo: PhotoRecord)

    @Query("DELETE FROM photo_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
