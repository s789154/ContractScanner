package com.example.contractscanner.data

import androidx.lifecycle.LiveData

class ContractRepository(private val contractDao: ContractDao) {

    val allRecords: LiveData<List<ContractRecord>> = contractDao.getAllRecords()
    val allBatchGroups: LiveData<List<String>> = contractDao.getAllBatchGroups()

    suspend fun insert(record: ContractRecord): Long {
        return contractDao.insert(record)
    }

    suspend fun delete(record: ContractRecord) {
        contractDao.delete(record)
    }

    suspend fun deleteByIds(ids: List<Long>) {
        contractDao.deleteByIds(ids)
    }

    suspend fun updateBatchGroup(ids: List<Long>, batchGroup: String) {
        contractDao.updateBatchGroup(ids, batchGroup)
    }

    suspend fun getAllRecordsSync(): List<ContractRecord> {
        return contractDao.getAllRecordsSync()
    }

    suspend fun getRecordsByBatchGroup(batchGroup: String): List<ContractRecord> {
        return contractDao.getRecordsByBatchGroup(batchGroup)
    }

    fun getRecordsByBatchGroupLive(batchGroup: String): LiveData<List<ContractRecord>> {
        return contractDao.getRecordsByBatchGroupLive(batchGroup)
    }

    suspend fun findDuplicate(sellerName: String, orderId: String, signDate: String): ContractRecord? {
        return contractDao.findDuplicate(sellerName, orderId, signDate)
    }

    suspend fun isDuplicate(sellerName: String, orderId: String, signDate: String): Boolean {
        return contractDao.findDuplicate(sellerName, orderId, signDate) != null
    }
}
