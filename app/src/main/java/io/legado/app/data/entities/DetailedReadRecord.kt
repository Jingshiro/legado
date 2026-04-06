package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detailedReadRecord")
data class DetailedReadRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookName: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val readIteration: Int = 0
)
