package com.example.contractscanner.ui.export

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.contractscanner.ContractScannerApp
import com.example.contractscanner.data.ContractRecord
import com.example.contractscanner.data.ContractRepository
import com.example.contractscanner.util.SimpleXlsxWriter
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ContractRepository =
        (application as ContractScannerApp).repository

    private val _exportResult = MutableLiveData<ExportResult>()
    val exportResult: LiveData<ExportResult> = _exportResult

    fun exportExcel(context: Context, batchGroup: String = "") {
        viewModelScope.launch {
            val records = if (batchGroup.isNotEmpty()) {
                repository.getRecordsByBatchGroup(batchGroup)
            } else {
                repository.getAllRecordsSync()
            }
            if (records.isEmpty()) {
                _exportResult.value = ExportResult.Error("没有记录可导出")
                return@launch
            }

            // 判断是否包含合同更改表（有变更条款）
            val hasChangeTerms = records.any { it.changeTerms.isNotEmpty() }

            val result = withContext(Dispatchers.IO) {
                try {
                    // 文件名
                    val fileName = "采购合同交接单_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"

                    // 交接日期 = 导出当天日期
                    val handoverDate = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
                    val subtitle = "交接日期：$handoverDate"

                    val headers = if (hasChangeTerms) {
                        listOf("序号", "合同类别", "乙方（卖方）公司名称", "订单号", "签订日期", "变更条款")
                    } else {
                        listOf("序号", "合同类别", "乙方（卖方）公司名称", "订单号", "签订日期")
                    }

                    val uri = saveFileToStorage(context, fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { os ->
                        SimpleXlsxWriter(os).write(
                            title = "益海嘉里（盘锦）食品工业有限公司采购合同交接单",
                            subtitle = subtitle,
                            headers = headers,
                            rows = records.mapIndexed { index, record ->
                                if (hasChangeTerms) {
                                    listOf(
                                        (index + 1).toString(),
                                        record.contractType,
                                        record.sellerName,
                                        record.orderId,
                                        record.signDate,
                                        record.changeTerms
                                    )
                                } else {
                                    listOf(
                                        (index + 1).toString(),
                                        record.contractType,
                                        record.sellerName,
                                        record.orderId,
                                        record.signDate
                                    )
                                }
                            },
                            hasChangeTerms = hasChangeTerms
                        )
                    }
                    if (uri != null) {
                        ExportResult.Success("Excel导出成功", uri)
                    } else {
                        ExportResult.Error("保存文件失败")
                    }
                } catch (e: Exception) {
                    ExportResult.Error("导出Excel失败: ${e.message}")
                }
            }
            _exportResult.value = result
        }
    }

    fun exportPdf(context: Context, batchGroup: String = "") {
        viewModelScope.launch {
            val records = if (batchGroup.isNotEmpty()) {
                repository.getRecordsByBatchGroup(batchGroup)
            } else {
                repository.getAllRecordsSync()
            }
            if (records.isEmpty()) {
                _exportResult.value = ExportResult.Error("没有记录可导出")
                return@launch
            }

            // 判断是否包含合同更改表（有变更条款）
            val hasChangeTerms = records.any { it.changeTerms.isNotEmpty() }

            val result = withContext(Dispatchers.IO) {
                try {
                    val fileName = "采购合同交接单_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"

                    val uri = saveFileToStorage(context, fileName, "application/pdf") { outputStream ->
                        val pdfWriter = PdfWriter(outputStream)
                        val pdfDocument = PdfDocument(pdfWriter)
                        val document = Document(pdfDocument)

                        val font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H")
                        val boldFont = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H")

                        // 每页行数限制（不含标题和表头）
                        val rowsPerPage = 20
                        val totalPages = (records.size + rowsPerPage - 1) / rowsPerPage

                        for (pageIndex in 0 until totalPages) {
                            if (pageIndex > 0) {
                                document.add(com.itextpdf.layout.element.AreaBreak(com.itextpdf.layout.properties.AreaBreakType.NEXT_PAGE))
                            }

                            // 标题
                            val title = Paragraph("益海嘉里（盘锦）食品工业有限公司采购合同交接单")
                                .setFont(boldFont)
                                .setFontSize(16f)
                                .setTextAlignment(TextAlignment.CENTER)
                            document.add(title)

                            // 交接日期
                            val handoverDate = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
                            val datePara = Paragraph("交接日期：$handoverDate")
                                .setFont(font)
                                .setFontSize(12f)
                            document.add(datePara)

                            document.add(Paragraph().setHeight(8f))

                            // 表格
                            val columnWidths = if (hasChangeTerms) {
                                floatArrayOf(1f, 2f, 5f, 2f, 2f, 3f)
                            } else {
                                floatArrayOf(1f, 2f, 5f, 2f, 2f)
                            }
                            val table = Table(UnitValue.createPercentArray(columnWidths))
                                .setWidth(UnitValue.createPercentValue(100f))

                            val headers = if (hasChangeTerms) {
                                listOf("序号", "合同类别", "乙方（卖方）公司名称", "订单号", "签订日期", "变更条款")
                            } else {
                                listOf("序号", "合同类别", "乙方（卖方）公司名称", "订单号", "签订日期")
                            }
                            headers.forEach { header ->
                                val cell = Cell().add(Paragraph(header).setFont(boldFont).setFontSize(11f))
                                    .setTextAlignment(TextAlignment.CENTER)
                                table.addHeaderCell(cell)
                            }

                            // 当前页的数据
                            val startIndex = pageIndex * rowsPerPage
                            val endIndex = minOf(startIndex + rowsPerPage, records.size)
                            for (i in startIndex until endIndex) {
                                val record = records[i]
                                table.addCell(Cell().add(Paragraph((i + 1).toString()).setFont(font).setFontSize(10f))
                                    .setTextAlignment(TextAlignment.CENTER))
                                table.addCell(Cell().add(Paragraph(record.contractType).setFont(font).setFontSize(10f))
                                    .setTextAlignment(TextAlignment.CENTER))
                                table.addCell(Cell().add(Paragraph(record.sellerName).setFont(font).setFontSize(10f)))
                                table.addCell(Cell().add(Paragraph(record.orderId).setFont(font).setFontSize(10f))
                                    .setTextAlignment(TextAlignment.CENTER))
                                table.addCell(Cell().add(Paragraph(record.signDate).setFont(font).setFontSize(10f))
                                    .setTextAlignment(TextAlignment.CENTER))
                                if (hasChangeTerms) {
                                    table.addCell(Cell().add(Paragraph(record.changeTerms).setFont(font).setFontSize(10f)))
                                }
                            }

                            // 如果当前页数据不足，填充空行（保持页脚位置一致）
                            val emptyRows = rowsPerPage - (endIndex - startIndex)
                            for (i in 0 until emptyRows) {
                                repeat(if (hasChangeTerms) 6 else 5) {
                                    table.addCell(Cell().add(Paragraph("").setFont(font).setFontSize(10f)))
                                }
                            }

                            document.add(table)

                            // 页码
                            document.add(Paragraph().setHeight(12f))
                            val pageNumberPara = Paragraph("第 ${pageIndex + 1} 页 / 共 $totalPages 页")
                                .setFont(font)
                                .setFontSize(10f)
                                .setTextAlignment(TextAlignment.CENTER)
                            document.add(pageNumberPara)

                            // 签字区域
                            document.add(Paragraph().setHeight(20f))
                            val signTable = Table(floatArrayOf(1f, 1f))
                                .setWidth(UnitValue.createPercentValue(100f))
                            signTable.addCell(Cell().add(Paragraph("交出人签字：________________").setFont(font).setFontSize(11f))
                                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
                            signTable.addCell(Cell().add(Paragraph("接收人签字：________________").setFont(font).setFontSize(11f))
                                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                                .setTextAlignment(TextAlignment.RIGHT))
                            document.add(signTable)

                            val dateTable = Table(floatArrayOf(1f, 1f))
                                .setWidth(UnitValue.createPercentValue(100f))
                            dateTable.addCell(Cell().add(Paragraph("日期：______年______月______日").setFont(font).setFontSize(11f))
                                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
                            dateTable.addCell(Cell().add(Paragraph("日期：______年______月______日").setFont(font).setFontSize(11f))
                                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                                .setTextAlignment(TextAlignment.RIGHT))
                            document.add(dateTable)
                        }

                        document.close()
                    }

                    if (uri != null) {
                        ExportResult.Success("PDF导出成功", uri)
                    } else {
                        ExportResult.Error("保存文件失败")
                    }
                } catch (e: Exception) {
                    ExportResult.Error("导出PDF失败: ${e.message}")
                }
            }
            _exportResult.value = result
        }
    }

    private fun saveFileToStorage(
        context: Context,
        fileName: String,
        mimeType: String,
        writeAction: (java.io.OutputStream) -> Unit
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { os -> writeAction(os) }
            }
            uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { fos -> writeAction(fos) }
            Uri.fromFile(file)
        }
    }

    sealed class ExportResult {
        data class Success(val message: String, val uri: Uri) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }
}
