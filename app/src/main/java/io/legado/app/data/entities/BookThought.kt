package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "book_thoughts",
    indices = [
        Index(value = ["bookName", "bookAuthor"]),
        Index(value = ["bookName", "bookAuthor", "chapterIndex"])
    ]
)
data class BookThought(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookName: String = "",
    val bookAuthor: String = "",
    val chapterIndex: Int = 0,
    val chapterName: String = "",
    val selectedText: String = "",
    val textHash: String = "",
    val thought: String = "",
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
) : Parcelable
