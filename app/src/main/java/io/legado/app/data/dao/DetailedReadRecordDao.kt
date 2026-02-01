package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.legado.app.data.entities.DetailedReadRecord

@Dao
interface DetailedReadRecordDao {

    @Insert
    fun insert(record: DetailedReadRecord)

    @Insert
    fun insertAll(records: List<DetailedReadRecord>)

    @Query("select * from detailedReadRecord order by bookName asc, startTime asc")
    fun all(): List<DetailedReadRecord>

    @Query("delete from detailedReadRecord")
    fun clear()

    @Query("delete from detailedReadRecord where bookName = :bookName")
    fun deleteByBookName(bookName: String)
}
