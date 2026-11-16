package com.textpic.studio.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Insert
    suspend fun insert(template: TemplateEntity): Long

    @Update
    suspend fun update(template: TemplateEntity)

    @Delete
    suspend fun delete(template: TemplateEntity)

    @Query("SELECT * FROM templates ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY createdAt DESC")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int

    @Query("DELETE FROM templates")
    suspend fun deleteAll()
}
