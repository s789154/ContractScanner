package com.example.contractscanner.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.contractscanner.ContractScannerApp
import com.example.contractscanner.data.ContractRecord
import com.example.contractscanner.data.ContractRepository
import kotlinx.coroutines.launch

class ListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ContractRepository =
        (application as ContractScannerApp).repository

    val allRecords = repository.allRecords
    val allBatchGroups = repository.allBatchGroups

    private val _selectionMode = MutableLiveData<Boolean>(false)
    val selectionMode: LiveData<Boolean> = _selectionMode

    private val _selectedIds = MutableLiveData<Set<Long>>(emptySet())
    val selectedIds: LiveData<Set<Long>> = _selectedIds

    fun delete(record: ContractRecord) {
        viewModelScope.launch {
            repository.delete(record)
        }
    }

    fun deleteByIds(ids: List<Long>) {
        viewModelScope.launch {
            repository.deleteByIds(ids)
            clearSelection()
        }
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value ?: emptySet()
        val newSet = if (current.contains(id)) {
            current - id
        } else {
            current + id
        }
        _selectedIds.value = newSet

        // 如果有选中项但不在选择模式，自动进入选择模式
        if (newSet.isNotEmpty() && _selectionMode.value != true) {
            _selectionMode.value = true
        }

        // 如果没有选中项且在选择模式，自动退出选择模式
        if (newSet.isEmpty() && _selectionMode.value == true) {
            _selectionMode.value = false
        }
    }

    fun selectAll(recordIds: List<Long>) {
        _selectedIds.value = recordIds.toSet()
        if (recordIds.isNotEmpty() && _selectionMode.value != true) {
            _selectionMode.value = true
        }
    }

    /**
     * 切换某批次的全选/取消全选状态
     */
    fun toggleBatchSelection(recordIds: List<Long>) {
        if (recordIds.isEmpty()) return
        val current = _selectedIds.value ?: emptySet()
        val batchSet = recordIds.toSet()
        val allSelected = batchSet.all { current.contains(it) }

        val newSet = if (allSelected) {
            current - batchSet
        } else {
            current + batchSet
        }
        _selectedIds.value = newSet

        if (newSet.isNotEmpty() && _selectionMode.value != true) {
            _selectionMode.value = true
        }
        if (newSet.isEmpty() && _selectionMode.value == true) {
            _selectionMode.value = false
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _selectionMode.value = false
    }

    fun setSelectionMode(enabled: Boolean) {
        _selectionMode.value = enabled
        if (!enabled) {
            _selectedIds.value = emptySet()
        }
    }

    fun moveSelectedToBatch(ids: List<Long>, batchGroup: String) {
        viewModelScope.launch {
            repository.updateBatchGroup(ids, batchGroup)
            clearSelection()
        }
    }

}
