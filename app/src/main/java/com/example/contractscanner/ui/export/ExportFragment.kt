package com.example.contractscanner.ui.export

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.contractscanner.databinding.FragmentExportBinding

class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExportViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 从 SharedPreferences 读取批次信息
        val prefs = requireContext().getSharedPreferences("export_prefs", android.content.Context.MODE_PRIVATE)
        val batchGroup = prefs.getString("export_batch_group", "") ?: ""

        binding.btnExportExcel.setOnClickListener {
            viewModel.exportExcel(requireContext(), batchGroup)
        }

        binding.btnExportPdf.setOnClickListener {
            viewModel.exportPdf(requireContext(), batchGroup)
        }

        // 显示当前导出范围
        if (batchGroup.isNotEmpty()) {
            binding.tvExportInfo.text = "导出批次: $batchGroup"
        } else {
            binding.tvExportInfo.text = "导出全部记录"
        }

        viewModel.exportResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ExportViewModel.ExportResult.Success -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
                is ExportViewModel.ExportResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
