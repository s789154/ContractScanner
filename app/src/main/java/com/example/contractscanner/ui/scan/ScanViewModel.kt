package com.example.contractscanner.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.contractscanner.ContractScannerApp
import com.example.contractscanner.data.ContractRecord
import com.example.contractscanner.data.ContractRepository
import kotlinx.coroutines.launch

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ContractRepository =
        (application as ContractScannerApp).repository

    private val _extractedData = MutableLiveData<Triple<String, String, String>?>()
    val extractedData: LiveData<Triple<String, String, String>?> = _extractedData

    // 完整的提取数据（包含合同类别和变更条款）
    private val _extractedDataFull = MutableLiveData<ContractData?>()
    val extractedDataFull: LiveData<ContractData?> = _extractedDataFull

    private val _scanState = MutableLiveData<ScanState>()
    val scanState: LiveData<ScanState> = _scanState

    private val _navigateToConfirm = MutableLiveData<Boolean>()
    val navigateToConfirm: LiveData<Boolean> = _navigateToConfirm

    // 当前扫描归属的批次
    var currentBatchGroup: String = ""

    init {
        _scanState.value = ScanState.SCANNING
    }

    fun onDataExtracted(
        sellerName: String,
        orderId: String,
        signDate: String,
        contractType: String = "",
        changeTerms: String = ""
    ) {
        _extractedData.value = Triple(sellerName, orderId, signDate)
        _extractedDataFull.value = ContractData(sellerName, orderId, signDate, contractType, changeTerms)
        _scanState.value = ScanState.FOUND
        _navigateToConfirm.value = true
    }

    fun onConfirmNavigated() {
        _navigateToConfirm.value = false
    }

    fun resetScan() {
        _extractedData.value = null
        _extractedDataFull.value = null
        _scanState.value = ScanState.SCANNING
    }

    fun saveRecord(
        sellerName: String,
        orderId: String,
        signDate: String,
        contractType: String = "",
        changeTerms: String = "",
        batchGroup: String = ""
    ) {
        viewModelScope.launch {
            val record = ContractRecord(
                sellerName = sellerName,
                orderId = orderId,
                signDate = signDate,
                contractType = contractType,
                changeTerms = changeTerms,
                batchGroup = batchGroup
            )
            repository.insert(record)
        }
    }

    enum class ScanState {
        SCANNING, FOUND
    }

    data class ContractData(
        val sellerName: String,
        val orderId: String,
        val signDate: String,
        val contractType: String,
        val changeTerms: String = ""
    )
}
