package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.BookThought
import kotlinx.coroutines.flow.Flow

@Dao
interface BookThoughtDao {

    @get:Query("SELECT * FROM book_thoughts ORDER BY createTime DESC")
    val all: List<BookThought>

    @Query(
        """
        SELECT * FROM book_thoughts
        WHERE bookName = :bookName AND bookAuthor = :bookAuthor
        ORDER BY chapterIndex, createTime
    """
    )
    fun flowByBook(bookName: String, bookAuthor: String): Flow<List<BookThought>>

    @Query(
        """
        SELECT * FROM book_thoughts
        WHERE bookName = :bookName AND bookAuthor = :bookAuthor
        AND (chapterName LIKE '%'||:key||'%' OR selectedText LIKE '%'||:key||'%' OR thought LIKE '%'||:key||'%')
        ORDER BY chapterIndex, createTime
    """
    )
    fun flowSearch(bookName: String, bookAuthor: String, key: String): Flow<List<BookThought>>

    @Query(
        """
        SELECT * FROM book_thoughts
        WHERE bookName = :bookName AND bookAuthor = :bookAuthor
        ORDER BY chapterIndex, createTime
    """
    )
    fun getByBook(bookName: String, bookAuthor: String): List<BookThought>

    @Query(
        """
        SELECT * FROM book_thoughts
        WHERE bookName = :bookName AND bookAuthor = :bookAuthor
        AND chapterIndex = :chapterIndex
        ORDER BY createTime
    """
    )
    fun getByChapter(bookName: String, bookAuthor: String, chapterIndex: Int): List<BookThought>

    @Query(
        """
        SELECT * FROM book_thoughts
        WHERE bookName = :bookName AND bookAuthor = :bookAuthor
        AND chapterIndex = :chapterIndex AND selectedText = :selectedText
        ORDER BY createTime
    """
    )
    fun findByText(
        bookName: String,
        bookAuthor: String,
        chapterIndex: Int,
        selectedText: String
    ): List<BookThought>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg thought: BookThought): List<Long>

    @Update
    fun update(vararg thought: BookThought)

    @Delete
    fun delete(vararg thought: BookThought)

    @Query("DELETE FROM book_thoughts WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM book_thoughts WHERE bookName = :bookName AND bookAuthor = :bookAuthor")
    fun deleteByBook(bookName: String, bookAuthor: String)
}
