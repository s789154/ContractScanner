package com.example.contractscanner.ui.list

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.contractscanner.databinding.FragmentListBinding

class ListFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ListViewModel by viewModels()
    private lateinit var adapter: BatchRecordAdapter

    // 当前展开/折叠的批次
    private val expandedBatches = mutableSetOf<String>()

    // 缓存的列表数据
    private var currentItems: List<ListItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupActionBar()

        viewModel.allRecords.observe(viewLifecycleOwner) { records ->
            updateList(records)
        }

        viewModel.selectionMode.observe(viewLifecycleOwner) { isSelectionMode ->
            binding.actionBar.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            // 选择模式切换时刷新可见项（如 CheckBox 的显示/隐藏）
            adapter.notifyDataSetChanged()
        }

        viewModel.selectedIds.observe(viewLifecycleOwner) { selectedIds ->
            // 只更新列表项的选中状态，不重建批次结构
            if (currentItems.isNotEmpty()) {
                val updatedItems = currentItems.map { item ->
                    when (item) {
                        is ListItem.BatchHeader -> {
                            val allSelected = item.recordIds.isNotEmpty() &&
                                item.recordIds.all { selectedIds.contains(it) }
                            if (allSelected != item.isAllSelected) {
                                item.copy(isAllSelected = allSelected)
                            } else {
                                item
                            }
                        }
                        is ListItem.RecordItem -> {
                            val isSel = selectedIds.contains(item.id)
                            if (isSel != item.isSelected) {
                                item.copy(isSelected = isSel)
                            } else {
                                item
                            }
                        }
                    }
                }
                // 只有当确实有变化时才 submitList，避免无效刷新
                val hasChange = updatedItems.zip(currentItems).any { (a, b) -> a != b }
                if (hasChange) {
                    currentItems = updatedItems
                    adapter.submitList(updatedItems.toList())
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = BatchRecordAdapter(
            onBatchClick = { batchGroup ->
                toggleBatch(batchGroup)
            },
            onBatchScanClick = { batchGroup ->
                navigateToScan(batchGroup)
            },
            onBatchExportClick = { batchGroup ->
                showExportDialog(batchGroup)
            },
            onBatchSelectAllClick = { recordIds ->
                viewModel.toggleBatchSelection(recordIds)
            },
            onRecordClick = { id ->
                viewModel.toggleSelection(id)
            },
            onRecordLongClick = { id ->
                viewModel.toggleSelection(id)
            },
            isSelectionMode = { viewModel.selectionMode.value == true },
            isSelected = { id -> viewModel.selectedIds.value?.contains(id) == true }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        // 禁用 item animator 避免闪烁
        binding.recyclerView.itemAnimator = null
    }

    private fun setupActionBar() {
        // 底部全选按钮：选中数据库中所有记录（包括未展开批次的）
        binding.btnSelectAll.setOnClickListener {
            val records = viewModel.allRecords.value ?: return@setOnClickListener
            val allIds = records.map { it.id }
            viewModel.selectAll(allIds)
        }

        binding.btnDelete.setOnClickListener {
            val ids = viewModel.selectedIds.value?.toList() ?: return@setOnClickListener
            if (ids.isEmpty()) {
                Toast.makeText(requireContext(), "请先选择记录", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定删除选中的 ${ids.size} 条记录吗？")
                .setPositiveButton("删除") { _, _ ->
                    viewModel.deleteByIds(ids)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.btnMove.setOnClickListener {
            val ids = viewModel.selectedIds.value?.toList() ?: return@setOnClickListener
            if (ids.isEmpty()) {
                Toast.makeText(requireContext(), "请先选择记录", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showMoveDialog(ids)
        }

        binding.btnCancel.setOnClickListener {
            viewModel.clearSelection()
        }
    }

    private fun toggleBatch(batchGroup: String) {
        if (expandedBatches.contains(batchGroup)) {
            expandedBatches.remove(batchGroup)
        } else {
            expandedBatches.add(batchGroup)
        }
        viewModel.allRecords.value?.let { updateList(it) }
    }

    private fun updateList(records: List<com.example.contractscanner.data.ContractRecord>) {
        val items = mutableListOf<ListItem>()
        val selectedIds = viewModel.selectedIds.value ?: emptySet()

        // 按批次分组，按批次名降序排列
        val grouped = records.groupBy { it.batchGroup.ifEmpty { "未分组" } }
            .toSortedMap(compareByDescending { it })

        grouped.forEach { (batchGroup, batchRecords) ->
            val isExpanded = expandedBatches.contains(batchGroup)
            val recordIds = batchRecords.map { it.id }
            // 判断该批次是否全部被选中
            val isAllSelected = recordIds.isNotEmpty() && recordIds.all { selectedIds.contains(it) }

            // 基于批次名生成稳定的负数 ID
            val headerId = -(batchGroup.hashCode().toLong())
            items.add(ListItem.BatchHeader(
                id = headerId,
                batchGroup = batchGroup,
                recordCount = batchRecords.size,
                isExpanded = isExpanded,
                recordIds = recordIds,
                isAllSelected = isAllSelected
            ))

            if (isExpanded) {
                batchRecords.forEach { record ->
                    items.add(ListItem.RecordItem(
                        id = record.id,
                        record = record,
                        isSelected = selectedIds.contains(record.id)
                    ))
                }
            }
        }

        currentItems = items.toList()
        adapter.submitList(currentItems)
        binding.tvEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun navigateToScan(batchGroup: String) {
        val action = ListFragmentDirections.actionListFragmentToScanFragment()
        val prefs = requireContext().getSharedPreferences("scan_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("target_batch_group", batchGroup).apply()
        findNavController().navigate(action)
    }

    private fun showExportDialog(batchGroup: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("导出 $batchGroup")
            .setItems(arrayOf("导出为 Excel", "导出为 PDF")) { _, _ ->
                val action = ListFragmentDirections.actionListFragmentToExportFragment()
                val prefs = requireContext().getSharedPreferences("export_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("export_batch_group", batchGroup).apply()
                findNavController().navigate(action)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showMoveDialog(ids: List<Long>) {
        val editText = EditText(requireContext()).apply {
            hint = "输入目标批次名称（如 2024-05-03）"
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(editText)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("移动到批次")
            .setView(layout)
            .setPositiveButton("移动") { _, _ ->
                val batchGroup = editText.text.toString().trim()
                if (batchGroup.isNotEmpty()) {
                    viewModel.moveSelectedToBatch(ids, batchGroup)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
