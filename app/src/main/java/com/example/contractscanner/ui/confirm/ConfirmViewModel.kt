package com.example.contractscanner.ui.confirm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.contractscanner.ContractScannerApp
import com.example.contractscanner.data.ContractRecord
import com.example.contractscanner.data.ContractRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ConfirmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ContractRepository =
        (application as ContractScannerApp).repository

    fun saveRecord(
        sellerName: String,
        orderId: String,
        signDate: String,
        contractType: String = "",
        changeTerms: String = "",
        batchGroup: String = ""
    ) {
        viewModelScope.launch {
            val actualBatch = batchGroup.ifEmpty {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            }
            val record = ContractRecord(
                sellerName = sellerName,
                orderId = orderId,
                signDate = signDate,
                contractType = contractType,
                changeTerms = changeTerms,
                batchGroup = actualBatch
            )
            repository.insert(record)
        }
    }

    fun checkDuplicate(sellerName: String, orderId: String, signDate: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val isDuplicate = repository.isDuplicate(sellerName, orderId, signDate)
            callback(isDuplicate)
        }
    }
}
