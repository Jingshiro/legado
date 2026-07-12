package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.DetailedReadRecord

@Dao
interface DetailedReadRecordDao {

    @Insert
    fun insert(record: DetailedReadRecord)

    @Update
    fun update(record: DetailedReadRecord)

    @Insert
    fun insertAll(records: List<DetailedReadRecord>)

    @Query("select * from detailedReadRecord order by bookName asc, startTime asc")
    fun all(): List<DetailedReadRecord>

    @Query("select * from detailedReadRecord where bookName = :bookName order by startTime asc")
    fun getByBookName(bookName: String): List<DetailedReadRecord>

    @Query("select * from detailedReadRecord where bookName like '%' || :bookName || '%' order by startTime asc")
    fun searchByBookName(bookName: String): List<DetailedReadRecord>

    @Query("select * from detailedReadRecord where startTime >= :startTime and endTime <= :endTime order by startTime asc")
    fun getByTimeRange(startTime: Long, endTime: Long): List<DetailedReadRecord>

    @Query("select * from detailedReadRecord where bookName like '%' || :bookName || '%' and startTime >= :startTime and endTime <= :endTime order by startTime asc")
    fun search(bookName: String, startTime: Long, endTime: Long): List<DetailedReadRecord>

    @Query("select min(startTime) from detailedReadRecord where bookName = :bookName")
    fun getEarliestStartTime(bookName: String): Long?

    @Query("select * from detailedReadRecord where bookName = :bookName order by endTime desc limit 1")
    fun getLastRecord(bookName: String): DetailedReadRecord?

    @Query("delete from detailedReadRecord")
    fun clear()

    @Query("delete from detailedReadRecord where bookName = :bookName")
    fun deleteByBookName(bookName: String)
}
