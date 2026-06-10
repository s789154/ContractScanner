package com.example.contractscanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contracts")
data class ContractRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sellerName: String,
    val orderId: String,
    val signDate: String,
    val contractType: String = "",  // 合同类别，如"采购合同"、"合同更改表"、"保证金协议"、"反商业贿赂协议"
    val changeTerms: String = "",  // 变更条款（仅合同更改表使用）
    val addTime: Long = System.currentTimeMillis(),
    val batchGroup: String = ""  // 批次分组标识，格式 "yyyy-MM-dd" 或自定义批次名
)
