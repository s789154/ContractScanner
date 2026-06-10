package com.example.contractscanner.ui.list

import com.example.contractscanner.data.ContractRecord

sealed class ListItem {
    abstract val id: Long

    data class BatchHeader(
        override val id: Long,
        val batchGroup: String,
        val recordCount: Int,
        val isExpanded: Boolean,
        val recordIds: List<Long> = emptyList(),  // 该批次下所有记录的ID
        val isAllSelected: Boolean = false         // 该批次是否全部选中
    ) : ListItem()

    data class RecordItem(
        override val id: Long,
        val record: ContractRecord,
        val isSelected: Boolean
    ) : ListItem()
}
