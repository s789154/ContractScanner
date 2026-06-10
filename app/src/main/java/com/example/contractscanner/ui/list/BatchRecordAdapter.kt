package com.example.contractscanner.ui.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.contractscanner.R
import com.example.contractscanner.databinding.ItemBatchHeaderBinding
import com.example.contractscanner.databinding.ItemRecordSelectableBinding
import java.text.SimpleDateFormat
import java.util.*

class BatchRecordAdapter(
    private val onBatchClick: (String) -> Unit,
    private val onBatchScanClick: (String) -> Unit,
    private val onBatchExportClick: (String) -> Unit,
    private val onBatchSelectAllClick: (List<Long>) -> Unit,  // 批次全选/取消全选
    private val onRecordClick: (Long) -> Unit,
    private val onRecordLongClick: (Long) -> Unit,
    private val isSelectionMode: () -> Boolean,
    private val isSelected: (Long) -> Boolean
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_RECORD = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ListItem.BatchHeader -> TYPE_HEADER
            is ListItem.RecordItem -> TYPE_RECORD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemBatchHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                BatchHeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemRecordSelectableBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                RecordViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.BatchHeader -> (holder as BatchHeaderViewHolder).bind(item)
            is ListItem.RecordItem -> (holder as RecordViewHolder).bind(item)
        }
    }

    inner class BatchHeaderViewHolder(
        private val binding: ItemBatchHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(header: ListItem.BatchHeader) {
            binding.tvBatchName.text = header.batchGroup
            binding.tvCount.text = "${header.recordCount} 条"
            binding.ivExpand.rotation = if (header.isExpanded) 90f else 0f

            // 绑定全选按钮状态（使用 item 中的状态，避免每次重新查询）
            binding.cbSelectAll.isChecked = header.isAllSelected

            binding.root.setOnClickListener {
                onBatchClick(header.batchGroup)
            }
            binding.btnScan.setOnClickListener {
                onBatchScanClick(header.batchGroup)
            }
            binding.btnExport.setOnClickListener {
                onBatchExportClick(header.batchGroup)
            }
            binding.cbSelectAll.setOnClickListener {
                onBatchSelectAllClick(header.recordIds)
            }
        }
    }

    inner class RecordViewHolder(
        private val binding: ItemRecordSelectableBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ListItem.RecordItem) {
            val record = item.record

            // 显示合同类别
            if (record.contractType.isNotEmpty()) {
                binding.tvContractType.visibility = View.VISIBLE
                binding.tvContractType.text = record.contractType
            } else {
                binding.tvContractType.visibility = View.GONE
            }

            binding.tvSellerName.text = record.sellerName
            binding.tvOrderId.text = "订单号: ${record.orderId}"
            binding.tvSignDate.text = "签订日期: ${record.signDate}"

            // 显示变更条款
            if (record.changeTerms.isNotEmpty()) {
                binding.tvChangeTerms.visibility = View.VISIBLE
                binding.tvChangeTerms.text = "变更条款: ${record.changeTerms}"
            } else {
                binding.tvChangeTerms.visibility = View.GONE
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.tvAddTime.text = "录入: ${sdf.format(Date(record.addTime))}"

            val selMode = isSelectionMode()
            binding.cbSelect.visibility = if (selMode) View.VISIBLE else View.GONE
            // 直接使用 item 中的选中状态，避免交叉查询
            binding.cbSelect.isChecked = item.isSelected

            // 关键修复：使用 OnClickListener 替代 OnCheckedChangeListener
            // 避免代码设置 isChecked 时触发回调导致无限循环/闪退
            binding.cbSelect.setOnClickListener {
                onRecordClick(record.id)
            }

            binding.root.setOnClickListener {
                if (selMode) {
                    onRecordClick(record.id)
                }
            }
            binding.root.setOnLongClickListener {
                onRecordLongClick(record.id)
                true
            }
        }
    }

    /**
     * DiffUtil 回调：完整比较所有字段，确保选中状态变化能触发 onBindViewHolder
     */
    class DiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            // 完整比较，包括 isSelected / isAllSelected 等状态字段
            return oldItem == newItem
        }
    }
}
