package com.oyesuno.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.oyesuno.core.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Update
    suspend fun updateBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId ORDER BY timestampMs ASC")
    fun getBookmarksForRecording(recordingId: Long): Flow<List<BookmarkEntity>>

    @Query("DELETE FROM bookmarks WHERE recordingId = :recordingId")
    suspend fun deleteBookmarksForRecording(recordingId: Long)

    @Query("SELECT * FROM bookmarks WHERE label LIKE '%' || :query || '%' ORDER BY timestampMs ASC")
    fun searchBookmarks(query: String): Flow<List<BookmarkEntity>>
}
