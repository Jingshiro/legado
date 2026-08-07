package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.DetailedReadRecord

@Dao
interface DetailedReadRecordDao {

    // 唯一索引 (bookName, startTime, endTime) 兜底：完全重复的 session 会被 IGNORE，从源头杜绝重复写入
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(record: DetailedReadRecord)

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun update(record: DetailedReadRecord)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
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
