package com.example.contractscanner.ui.scan

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.example.contractscanner.R
import com.example.contractscanner.databinding.FragmentScanBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executors
import java.util.regex.Pattern

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanViewModel by viewModels()
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor = Executors.newSingleThreadExecutor()

    // 扫描状态
    private var isScanning = true        // 实时扫描是否开启
    private var isRealTimeMode = true    // true=实时扫描模式, false=拍照模式
    private var lastProcessTime = 0L
    private var scanCount = 0

    // 已识别到的字段，识别到后固定不再刷新
    private var foundSeller: String? = null
    private var foundOrder: String? = null
    private var foundDate: String? = null
    private var foundContractType: String? = null  // 合同类别
    private var foundChangeTerms: String? = null   // 变更条款

    // TextRecognizer 在 onCreate 中初始化，在 onDestroy 中关闭
    private var textRecognizer: com.google.mlkit.vision.text.TextRecognizer? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 关键修复：在这里创建 TextRecognizer，而不是作为属性直接初始化
        // 这样 onDestroyView 不会关闭它，只有 onDestroy 才会关闭
        textRecognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 恢复相机执行器（如果从confirm返回，可能已被关闭）
        if (cameraExecutor.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        // 读取从列表页传递过来的批次信息
        val prefs = requireContext().getSharedPreferences("scan_prefs", android.content.Context.MODE_PRIVATE)
        val targetBatch = prefs.getString("target_batch_group", "") ?: ""
        if (targetBatch.isNotEmpty()) {
            viewModel.currentBatchGroup = targetBatch
            prefs.edit().remove("target_batch_group").apply()
        }

        startCamera()
        updateButtonUI()

        binding.btnCapture.setOnClickListener {
            if (isRealTimeMode) {
                // 实时模式下：拍照识别
                capturePhoto()
            } else {
                // 拍照模式下：切回实时扫描
                switchToRealTimeMode()
            }
        }

        binding.btnToggleScan.setOnClickListener {
            toggleScanMode()
        }

        viewModel.navigateToConfirm.observe(viewLifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                viewModel.onConfirmNavigated()
                val data = viewModel.extractedDataFull.value ?: return@observe
                val action = ScanFragmentDirections.actionScanFragmentToConfirmFragment(
                    data.sellerName, data.orderId, data.signDate, data.contractType, data.changeTerms, viewModel.currentBatchGroup
                )
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    findNavController().navigate(action)
                }
            }
        }
    }

    /** 切换扫描/拍照模式 */
    private fun toggleScanMode() {
        isRealTimeMode = !isRealTimeMode
        if (isRealTimeMode) {
            switchToRealTimeMode()
        } else {
            // 切换到拍照模式：停止实时扫描，但保持相机预览
            isScanning = false
            updateStatus("已切换到拍照模式，点击左侧按钮拍照识别")
        }
        updateButtonUI()
    }

    private fun switchToRealTimeMode() {
        isRealTimeMode = true
        resetScanInternal()
        updateStatus("实时扫描模式 - 请将合同对准摄像头")
        updateButtonUI()
    }

    private fun updateButtonUI() {
        if (isRealTimeMode) {
            binding.btnCapture.text = "拍照识别"
            binding.btnToggleScan.text = "扫描识别"
        } else {
            binding.btnCapture.text = "扫描识别"
            binding.btnToggleScan.text = "拍照模式"
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (isScanning && isRealTimeMode) {
                                processImageRealtime(imageProxy)
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    imageCapture
                )
                if (isRealTimeMode) {
                    updateStatus("相机已启动，请对准合同...")
                } else {
                    updateStatus("拍照模式 - 点击拍照识别按钮")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                updateStatus("相机启动失败: ${e.message}")
                Toast.makeText(requireContext(), "相机启动失败", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processImageRealtime(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime < 800) {
            imageProxy.close()
            return
        }
        lastProcessTime = currentTime
        scanCount++

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val recognizer = textRecognizer
        if (recognizer == null) {
            Log.e(TAG, "TextRecognizer is null")
            imageProxy.close()
            return
        }

        try {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    val preview = fullText.take(100).replace("\n", "|")
                    handler.post {
                        updateDebugInfo("扫描: $scanCount | 长度: ${fullText.length}\n$preview")
                    }
                    extractFields(fullText)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Text recognition failed", e)
                    handler.post {
                        updateDebugInfo("识别失败: ${e.message}")
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Process image error", e)
            imageProxy.close()
        }
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: run {
            Toast.makeText(requireContext(), "相机未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        isScanning = false
        updateStatus("正在拍照识别...")

        val recognizer = textRecognizer
        if (recognizer == null) {
            updateStatus("识别引擎未就绪")
            isScanning = true
            return
        }

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val mediaImage = image.image
                    if (mediaImage != null) {
                        try {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                image.imageInfo.rotationDegrees
                            )
                            recognizer.process(inputImage)
                                .addOnSuccessListener { visionText ->
                                    val fullText = visionText.text
                                    handler.post {
                                        updateDebugInfo("拍照:\n${fullText.take(200)}")
                                        extractFields(fullText, force = true)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    handler.post {
                                        updateStatus("拍照识别失败: ${e.message}")
                                        isScanning = true
                                    }
                                }
                                .addOnCompleteListener {
                                    image.close()
                                }
                        } catch (e: Exception) {
                            image.close()
                            handler.post {
                                updateStatus("处理出错: ${e.message}")
                                isScanning = true
                            }
                        }
                    } else {
                        image.close()
                        handler.post {
                            isScanning = true
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    handler.post {
                        updateStatus("拍照失败: ${exception.message}")
                        isScanning = true
                    }
                }
            }
        )
    }

    // ============================================
    // 合同类型检测常量
    // ============================================
    companion object {
        const val TYPE_PURCHASE = "采购合同"
        const val TYPE_CHANGE = "合同更改表"
        const val TYPE_DEPOSIT = "保证金协议"
        const val TYPE_ANTI_BRIBERY = "反商业贿赂协议"
        private const val TAG = "ScanFragment"
    }

    /**
     * 检测合同类型并返回类型常量
     */
    private fun detectContractType(text: String): String {
        return when {
            text.contains("合同更改表") -> TYPE_CHANGE
            text.contains("保证金协议") -> TYPE_DEPOSIT
            text.contains("反商业贿赂协议") -> TYPE_ANTI_BRIBERY
            text.contains("采购合同") -> TYPE_PURCHASE
            else -> ""
        }
    }

    /**
     * 判断当前合同类型是否需要订单号
     */
    private fun requiresOrderId(contractType: String): Boolean {
        return contractType == TYPE_PURCHASE || contractType == TYPE_CHANGE
    }

    /**
     * 判断当前合同类型是否需要变更条款
     */
    private fun requiresChangeTerms(contractType: String): Boolean {
        return contractType == TYPE_CHANGE
    }

    /**
     * 获取当前需要匹配的字段数量
     */
    private fun getRequiredFieldCount(contractType: String): Int {
        return when (contractType) {
            TYPE_CHANGE -> 5  // 类别、卖方、订单号、日期、变更条款
            TYPE_DEPOSIT, TYPE_ANTI_BRIBERY -> 3  // 类别、卖方、日期
            TYPE_PURCHASE -> 4  // 类别、卖方、订单号、日期
            else -> 4
        }
    }

    /**
     * 将各种日期格式统一转换为 "yyyy年MM月dd日" 格式
     * 支持格式：2025年12月10日、2026/2/9、2026.2.9、2026-02-09 等
     */
    private fun normalizeDate(dateStr: String): String {
        val normalized = dateStr.trim()

        // 如果已经是标准格式，直接返回
        val standardPattern = Regex("""^(\d{4})年(\d{1,2})月(\d{1,2})日$""")
        if (standardPattern.matches(normalized)) {
            return normalized
        }

        // 提取年、月、日数字
        val numberPattern = Regex("""(\d{4})\s*[年/\\-\\.]\s*(\d{1,2})\s*[月/\\-\\.]\s*(\d{1,2})\s*日?""")
        val match = numberPattern.find(normalized)
        if (match != null) {
            val year = match.groupValues[1]
            val month = match.groupValues[2].padStart(2, '0')
            val day = match.groupValues[3].padStart(2, '0')
            return "${year}年${month}月${day}日"
        }

        // 如果无法解析，返回原始值
        return normalized
    }

    private fun extractFields(text: String, force: Boolean = false) {
        if (!isScanning && !force) return

        // 延迟检测合同类型，直到文本中有足够内容
        val detectedType = detectContractType(text)
        if (detectedType.isNotEmpty() && foundContractType == null) {
            foundContractType = detectedType
        }

        // 如果还没检测到类型，继续扫描
        val currentType = foundContractType
        if (currentType == null) return

        val requiredCount = getRequiredFieldCount(currentType)

        // 检查是否所有需要的字段都已找到
        val hasSeller = foundSeller != null
        val hasOrder = foundOrder != null || !requiresOrderId(currentType)
        val hasDate = foundDate != null
        val hasType = foundContractType != null
        val hasChangeTerms = foundChangeTerms != null || !requiresChangeTerms(currentType)

        if (hasSeller && hasOrder && hasDate && hasType && hasChangeTerms) return

        val lines = text.split("\n", "\r")

        // 根据合同类型使用不同的匹配模式
        val sellerPattern: Pattern
        val orderPattern: Pattern
        val datePattern: Pattern
        val changeTermsPattern: Pattern?

        when (currentType) {
            TYPE_CHANGE -> {
                // 合同更改表
                sellerPattern = Pattern.compile(
                    "卖\\s*方\\s*名\\s*称\\s*[：:]\\s*(.+)",
                    Pattern.CASE_INSENSITIVE
                )
                orderPattern = Pattern.compile(
                    "合同\\s*号\\s*码\\s*[(（]简称[\"\u201c\u201d]原\\s*合\\s*同[\"\u201c\u201d][)）]\\s*[：:]\\s*(.+)",
                    Pattern.CASE_INSENSITIVE
                )
                datePattern = Pattern.compile(
                    "变\\s*更\\s*日\\s*期\\s*[：:]\\s*([\\d]{4}[.\\-年/][\\d]{1,2}[.\\-月/][\\d]{1,2}[日]?)",
                    Pattern.CASE_INSENSITIVE
                )
                changeTermsPattern = Pattern.compile(
                    "变\\s*更\\s*条\\s*款\\s*[：:]\\s*(.+)",
                    Pattern.CASE_INSENSITIVE
                )
            }
            TYPE_DEPOSIT -> {
                // 保证金协议
                sellerPattern = Pattern.compile(
                    "乙\\s*方\\s*[(（]\\s*卖\\s*方\\s*[)）]\\s*[：:]\\s*(.+)",
                    Pattern.CASE_INSENSITIVE
                )
                orderPattern = Pattern.compile("NEVER_MATCH") // 不需要订单号
                datePattern = Pattern.compile(
                    "签\\s*署\\s*日\\s*期\\s*[：:]\\s*([\\d]{4}[.\\-年/][\\d]{1,2}[.\\-月/][\\d]{1,2}[日]?)",
                    Pattern.CASE_INSENSITIVE
                )
                changeTermsPattern = null
            }
            TYPE_ANTI_BRIBERY -> {
                // 反商业贿赂协议
                sellerPattern = Pattern.compile(
                    "乙\\s*方\\s*[：:]\\s*(.+)",
                    Pattern.CASE_INSENSITIVE
                )
                orderPattern = Pattern.compile("NEVER_MATCH") // 不需要订单号
                datePattern = Pattern.compile(
                    "由\\s*以\\s*下\\s*双\\s*方\\s*于\\s*([\\d]{4}[.\\-年/][\\d]{1,2}[.\\-月/][\\d]{1,2}[日]?)\\s*签",
                    Pattern.CASE_INSENSITIVE
                )
                changeTermsPattern = null
            }
            else -> {
                // 采购合同（默认）
                sellerPattern = Pattern.compile(
                    "卖\\s*方\\s*[(（]\\s*乙\\s*方\\s*[)）]\\s*[：:]\\s*(.+)",
                    Pattern.CASE_INSENSITIVE
                )
                orderPattern = Pattern.compile(
                    "订\\s*单\\s*号\\s*[：:]\\s*(\\d{6,12})",
                    Pattern.CASE_INSENSITIVE
                )
                datePattern = Pattern.compile(
                    "签\\s*订\\s*日\\s*期\\s*[：:]\\s*([\\d]{4}[.\\-年/][\\d]{1,2}[.\\-月/][\\d]{1,2}[日]?)",
                    Pattern.CASE_INSENSITIVE
                )
                changeTermsPattern = null
            }
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // 卖方匹配
            if (foundSeller == null) {
                val m = sellerPattern.matcher(line)
                if (m.find()) {
                    val candidate = m.group(1)?.trim()
                    if (candidate != null
                        && !candidate.contains("买方")
                        && !candidate.contains("甲方")
                        && !candidate.contains("益海嘉里")
                        && candidate.length >= 4
                    ) {
                        foundSeller = candidate
                    }
                }
            }

            // 订单号匹配（仅需要订单号的合同类型）
            if (foundOrder == null && requiresOrderId(currentType)) {
                val m = orderPattern.matcher(line)
                if (m.find()) {
                    foundOrder = m.group(1)?.trim()
                }
            }

            // 日期匹配
            if (foundDate == null) {
                val m = datePattern.matcher(line)
                if (m.find()) {
                    val rawDate = m.group(1)?.trim()
                    if (rawDate != null) {
                        foundDate = normalizeDate(rawDate)
                    }
                }
            }

            // 变更条款匹配（仅合同更改表）
            if (foundChangeTerms == null && changeTermsPattern != null) {
                val m = changeTermsPattern.matcher(line)
                if (m.find()) {
                    foundChangeTerms = m.group(1)?.trim()
                }
            }
        }

        // 更新UI
        handler.post {
            val sb = StringBuilder()
            sb.append("类别: ${foundContractType ?: "未匹配"}\n")
            sb.append("卖方: ${foundSeller ?: "未匹配"}\n")
            if (requiresOrderId(currentType)) {
                sb.append("订单号: ${foundOrder ?: "未匹配"}\n")
            }
            sb.append("日期: ${foundDate ?: "未匹配"}")
            if (requiresChangeTerms(currentType)) {
                sb.append("\n变更条款: ${foundChangeTerms ?: "未匹配"}")
            }
            updateStatus(sb.toString())
        }

        // 检查是否所有字段都匹配成功
        val allMatched = hasSeller && hasOrder && hasDate && hasType && hasChangeTerms
        if (allMatched) {
            isScanning = false
            vibrate()
            handler.post {
                val sb = StringBuilder()
                sb.append("识别成功！\n")
                sb.append("类别: $foundContractType\n")
                sb.append("卖方: $foundSeller\n")
                if (requiresOrderId(currentType)) {
                    sb.append("订单号: $foundOrder\n")
                }
                sb.append("日期: $foundDate")
                if (requiresChangeTerms(currentType)) {
                    sb.append("\n变更条款: $foundChangeTerms")
                }
                updateStatus(sb.toString())
            }
            viewModel.onDataExtracted(
                foundSeller!!,
                foundOrder ?: "",
                foundDate!!,
                foundContractType!!,
                foundChangeTerms ?: ""
            )
        }
    }

    private fun updateStatus(message: String) {
        binding.tvStatus.text = message
    }

    private fun updateDebugInfo(info: String) {
        binding.tvDebug.text = info
    }

    private fun vibrate() {
        try {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (_: Exception) {}
    }

    private fun resetScanInternal() {
        isScanning = true
        scanCount = 0
        lastProcessTime = 0L
        foundSeller = null
        foundOrder = null
        foundDate = null
        foundContractType = null
        foundChangeTerms = null
        viewModel.resetScan()
        updateDebugInfo("")
    }

    override fun onResume() {
        super.onResume()
        // 从确认页返回时，如果是实时模式，重置并继续扫描
        if (isRealTimeMode) {
            resetScanInternal()
            updateStatus("请对准合同继续扫描")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        try {
            // 只关闭 cameraExecutor，不关 textRecognizer
            cameraExecutor.shutdown()
        } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 关键修复：在这里关闭 TextRecognizer，而不是 onDestroyView
        try {
            textRecognizer?.close()
            textRecognizer = null
        } catch (_: Exception) {}
    }
}

