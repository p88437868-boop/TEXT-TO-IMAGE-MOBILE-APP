package com.textpic.studio.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DailyCount(val day: String, val count: Int)
data class StyleCount(val styleKey: String, val count: Int)
data class HourCount(val hour: String, val count: Int)
data class DailyStatusCount(val day: String, val status: String, val count: Int)
data class DailyAverage(val day: String, val average: Double)

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query(
        """
        SELECT * FROM messages
        WHERE (:query = '' OR (text IS NOT NULL AND text LIKE '%' || :query || '%'))
        AND (:status = '' OR status = :status)
        ORDER BY pinned DESC, createdAt DESC
        """,
    )
    fun pagingSource(query: String, status: String): PagingSource<Int, MessageEntity>

    @Query("SELECT * FROM messages ORDER BY pinned DESC, createdAt DESC")
    suspend fun getAll(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE createdAt < :cutoff")
    suspend fun getOlderThan(cutoff: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT * FROM messages ORDER BY pinned DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE status IN ('INSERTED', 'SHARED')")
    fun observeInsertedShared(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE status IN ('INSERTED', 'SHARED') AND createdAt >= :since")
    fun observeInsertedSharedSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE createdAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT AVG(chars) FROM messages")
    fun observeAverageChars(): Flow<Double?>

    @Query("SELECT AVG(chars) FROM messages WHERE createdAt >= :since")
    fun observeAverageCharsSince(since: Long): Flow<Double?>

    @Query(
        "SELECT styleKey, COUNT(*) as count FROM messages " +
            "WHERE createdAt >= :since GROUP BY styleKey ORDER BY count DESC",
    )
    fun observeStyleCountsSince(since: Long): Flow<List<StyleCount>>

    @Query("SELECT styleKey, COUNT(*) as count FROM messages GROUP BY styleKey ORDER BY count DESC")
    fun observeStyleCounts(): Flow<List<StyleCount>>

    @Query(
        """
        SELECT date(createdAt / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS count
        FROM messages WHERE createdAt >= :since
        GROUP BY day ORDER BY day
        """,
    )
    fun observeDailyCounts(since: Long): Flow<List<DailyCount>>

    @Query(
        """
        SELECT date(createdAt / 1000, 'unixepoch', 'localtime') AS day, status, COUNT(*) AS count
        FROM messages WHERE createdAt >= :since
        GROUP BY day, status ORDER BY day
        """,
    )
    fun observeDailyStatusCounts(since: Long): Flow<List<DailyStatusCount>>

    @Query(
        """
        SELECT strftime('%H', createdAt / 1000, 'unixepoch', 'localtime') AS hour, COUNT(*) AS count
        FROM messages WHERE createdAt >= :since
        GROUP BY hour ORDER BY hour
        """,
    )
    fun observeHourCounts(since: Long): Flow<List<HourCount>>

    @Query(
        """
        SELECT date(createdAt / 1000, 'unixepoch', 'localtime') AS day, AVG(chars) AS average
        FROM messages WHERE createdAt >= :since
        GROUP BY day ORDER BY day
        """,
    )
    fun observeDailyAverageChars(since: Long): Flow<List<DailyAverage>>
}

