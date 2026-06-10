package com.example.contractscanner.ui.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.contractscanner.databinding.FragmentConfirmBinding

class ConfirmFragment : Fragment() {

    private var _binding: FragmentConfirmBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConfirmViewModel by viewModels()
    private val args: ConfirmFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etContractType.setText(args.contractType)
        binding.etSellerName.setText(args.sellerName)
        binding.etOrderId.setText(args.orderId)
        binding.etSignDate.setText(args.signDate)
        binding.etChangeTerms.setText(args.changeTerms)

        // 如果是合同更改表，显示变更条款输入框
        val isChangeForm = args.contractType == "合同更改表"
        if (isChangeForm) {
            binding.tvChangeTermsLabel.visibility = View.VISIBLE
            binding.etChangeTerms.visibility = View.VISIBLE
        }

        // 如果是保证金协议或反商业贿赂协议，隐藏订单号（可选）
        if (args.contractType == "保证金协议" || args.contractType == "反商业贿赂协议") {
            // 这些类型通常没有订单号，可以留空或隐藏
        }

        // 显示当前批次信息
        val batchGroup = args.batchGroup
        if (batchGroup.isNotEmpty()) {
            binding.tvBatchInfo.visibility = View.VISIBLE
            binding.tvBatchInfo.text = "归属批次: $batchGroup"
        } else {
            binding.tvBatchInfo.visibility = View.GONE
        }

        binding.btnConfirm.setOnClickListener {
            val contractType = binding.etContractType.text.toString().trim()
            val sellerName = binding.etSellerName.text.toString().trim()
            val orderId = binding.etOrderId.text.toString().trim()
            val signDate = binding.etSignDate.text.toString().trim()
            val changeTerms = binding.etChangeTerms.text.toString().trim()

            // 根据合同类型校验必填项
            val needsOrderId = contractType != "保证金协议" && contractType != "反商业贿赂协议"
            if (sellerName.isEmpty() || (needsOrderId && orderId.isEmpty()) || signDate.isEmpty()) {
                Toast.makeText(requireContext(), "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.checkDuplicate(sellerName, orderId, signDate) { isDuplicate ->
                if (isDuplicate) {
                    Toast.makeText(requireContext(), "该记录已存在（订单号+签订日期+卖方公司名相同），已自动去重", Toast.LENGTH_LONG).show()
                    findNavController().popBackStack()
                } else {
                    viewModel.saveRecord(
                        sellerName = sellerName,
                        orderId = orderId,
                        signDate = signDate,
                        contractType = contractType,
                        changeTerms = changeTerms,
                        batchGroup = batchGroup
                    )
                    Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
        }

        binding.btnRescan.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
