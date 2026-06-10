package com.example.contractscanner.util

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 支持打印标题（重复表头）、页码、签字页脚的 XLSX 生成器
 */
class SimpleXlsxWriter(private val outputStream: OutputStream) {

    /**
     * 写入交接单
     *
     * @param title 主标题
     * @param subtitle 副标题（如交接日期）
     * @param headers 表头
     * @param rows 数据行
     * @param hasChangeTerms 是否包含变更条款列
     * @param printTitleRow 打印时重复显示的行号（1-based），通常是表头所在行
     */
    fun write(
        title: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        hasChangeTerms: Boolean = false,
        printTitleRow: Int = 3
    ) {
        ZipOutputStream(outputStream).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(contentTypes().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(rels().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write(workbookRels().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(workbook(printTitleRow).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/styles.xml"))
            zos.write(styles().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(
                worksheet(title, subtitle, headers, rows, hasChangeTerms).toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    /**
     * workbook.xml 中定义打印标题（definedNames），使表头在打印时每页都出现
     */
    private fun workbook(printTitleRow: Int) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="采购合同交接单" sheetId="1" r:id="rId1"/></sheets>
<definedNames><definedName name="_xlnm.Print_Titles" localSheetId="0">'采购合同交接单'!$$printTitleRow:$$printTitleRow</definedName></definedNames>
</workbook>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="16"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><name val="Calibri"/></font>
</fonts>
<fills count="1"><fill><patternFill patternType="none"/></fill></fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right><top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border>
</borders>
<cellXfs count="6">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"><alignment horizontal="left" vertical="center"/></xf>
</cellXfs>
</styleSheet>"""

    private fun worksheet(
        title: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        hasChangeTerms: Boolean
    ): String {
        val sb = StringBuilder()
        val colCount = headers.size

        // 列宽定义：序号、类别、卖方、订单号、日期、变更条款(可选)
        val colWidths = when (colCount) {
            6 -> listOf(8, 14, 38, 16, 16, 22)  // 含变更条款
            else -> listOf(8, 14, 42, 18, 18)    // 不含变更条款
        }

        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<cols>""")
        for (i in 0 until colCount) {
            val colLetter = ('A'.code + i).toChar()
            val width = colWidths.getOrElse(i) { 14 }
            sb.append("""<col min="${i + 1}" max="${i + 1}" width="$width" customWidth="1"/>""")
        }
        sb.append("""</cols>
<sheetData>""")

        val t = escapeXml(title)
        val s = escapeXml(subtitle)
        // 标题行（合并单元格）
        sb.append("""<row r="1" ht="32"><c r="A1" s="1" t="inlineStr"><is><t>$t</t></is></c></row>""")
        // 副标题行
        sb.append("""<row r="2"><c r="A2" t="inlineStr"><is><t>$s</t></is></c></row>""")
        // 表头行（打印标题行）
        sb.append("""<row r="3" ht="22">""")
        headers.forEachIndexed { i, h ->
            val c = ('A'.code + i).toChar()
            sb.append("""<c r="$c${3}" s="3" t="inlineStr"><is><t>${escapeXml(h)}</t></is></c>""")
        }
        sb.append("</row>")

        // 数据行
        rows.forEachIndexed { ri, row ->
            val rn = ri + 4
            sb.append("""<row r="$rn" ht="20">""")
            row.forEachIndexed { ci, cell ->
                val c = ('A'.code + ci).toChar()
                val st = if (ci == 0) "4" else "2"
                sb.append("""<c r="$c$rn" s="$st" t="inlineStr"><is><t>${escapeXml(cell)}</t></is></c>""")
            }
            sb.append("</row>")
        }

        // 空一行后添加签字区域
        val signStartRow = rows.size + 5
        // 签字行1：交出人
        sb.append("""<row r="$signStartRow" ht="24">""")
        sb.append("""<c r="A$signStartRow" s="5" t="inlineStr"><is><t>交出人签字：</t></is></c>""")
        sb.append("""<c r="B$signStartRow" s="2" t="inlineStr"><is><t></t></is></c>""")
        // 接收人放在右侧两列合并
        val receiverCol = 'A'.code + colCount - 2
        val receiverColLetter = receiverCol.toChar()
        sb.append("""<c r="$receiverColLetter$signStartRow" s="5" t="inlineStr"><is><t>接收人签字：</t></is></c>""")
        sb.append("""<c r="${(receiverCol + 1).toChar()}$signStartRow" s="2" t="inlineStr"><is><t></t></is></c>""")
        sb.append("</row>")

        // 签字行2：日期
        val dateRow = signStartRow + 1
        sb.append("""<row r="$dateRow" ht="24">""")
        sb.append("""<c r="A$dateRow" s="5" t="inlineStr"><is><t>日期：</t></is></c>""")
        sb.append("""<c r="B$dateRow" s="2" t="inlineStr"><is><t>      年    月    日</t></is></c>""")
        sb.append("""<c r="$receiverColLetter$dateRow" s="5" t="inlineStr"><is><t>日期：</t></is></c>""")
        sb.append("""<c r="${(receiverCol + 1).toChar()}$dateRow" s="2" t="inlineStr"><is><t>      年    月    日</t></is></c>""")
        sb.append("</row>")

        sb.append("</sheetData>")

        // 合并单元格：标题行跨所有列
        val endCol = ('A'.code + colCount - 1).toChar()
        sb.append("""<mergeCells count="1"><mergeCell ref="A1:${endCol}1"/></mergeCells>""")

        // 页面设置：打印标题 + 页眉页脚（页码 + 签字提示）
        sb.append("""
<printOptions gridLines="false" headings="false"/>
<pageMargins left="0.7" right="0.7" top="0.75" bottom="1.0" header="0.3" footer="0.5"/>
<pageSetup paperSize="9" orientation="portrait" fitToWidth="1" fitToHeight="0"/>
<headerFooter>
<oddHeader>&amp;C&amp;"Calibri,Bold"&amp;11采购合同交接单</oddHeader>
<oddFooter>&amp;L交出人签字：__________    接收人签字：__________&amp;R第 &amp;P 页 / 共 &amp;N 页</oddFooter>
</headerFooter>
</worksheet>""")

        return sb.toString()
    }
}
