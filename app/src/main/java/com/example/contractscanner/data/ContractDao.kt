package com.example.contractscanner.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ContractDao {

    @Insert
    suspend fun insert(record: ContractRecord): Long

    @Delete
    suspend fun delete(record: ContractRecord)

    @Query("DELETE FROM contracts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE contracts SET batchGroup = :batchGroup WHERE id IN (:ids)")
    suspend fun updateBatchGroup(ids: List<Long>, batchGroup: String)

    @Query("SELECT * FROM contracts ORDER BY addTime DESC")
    fun getAllRecords(): LiveData<List<ContractRecord>>

    @Query("SELECT * FROM contracts ORDER BY addTime DESC")
    suspend fun getAllRecordsSync(): List<ContractRecord>

    @Query("SELECT * FROM contracts WHERE batchGroup = :batchGroup ORDER BY addTime DESC")
    suspend fun getRecordsByBatchGroup(batchGroup: String): List<ContractRecord>

    @Query("SELECT * FROM contracts WHERE batchGroup = :batchGroup ORDER BY addTime DESC")
    fun getRecordsByBatchGroupLive(batchGroup: String): LiveData<List<ContractRecord>>

    @Query("SELECT DISTINCT batchGroup FROM contracts ORDER BY batchGroup DESC")
    fun getAllBatchGroups(): LiveData<List<String>>

    @Query("SELECT * FROM contracts WHERE sellerName = :sellerName AND orderId = :orderId AND signDate = :signDate LIMIT 1")
    suspend fun findDuplicate(sellerName: String, orderId: String, signDate: String): ContractRecord?

    @Query("SELECT * FROM contracts WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): ContractRecord?
}
