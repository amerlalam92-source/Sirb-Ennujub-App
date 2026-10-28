package com.example.presentation.evaluation.matrix

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.presentation.common.AppLocale
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

/**
 * مساعد تصدير مصفوفة التقييم الشاملة بصيغة PDF ومشاركتها
 */
object EvaluationMatrixPdfHelper {

    private fun getCairoTypeface(context: Context): Typeface? {
        return try {
            ResourcesCompat.getFont(context, R.font.cairo)
        } catch (_: Exception) {
            null
        }
    }

    fun exportMatrixPdf(
        rows: List<ScoutMatrixRow>,
        totalMeetingsHeld: Int,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoFont = getCairoTypeface(context)
        val cairoBold = if (cairoFont != null) Typeface.create(cairoFont, Typeface.BOLD) else null

        // A4 Landscape: 842 x 595 pt
        val pageWidth = 842
        val pageHeight = 595
        val rowsPerPage = 12

        val totalPages = ((rows.size + rowsPerPage - 1) / rowsPerPage).coerceAtLeast(1)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10f
            typeface = cairoFont
            textAlign = Paint.Align.RIGHT
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            typeface = cairoBold
            textAlign = Paint.Align.CENTER
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F9F9F9")
            style = Paint.Style.FILL
        }

        // ترتيب الأعمدة الجديد (10 أعمدة):
        // 1. الترتيب (40f)
        // 2. اسم الشبل (145f)
        // 3. تقييم الحضور (75f)
        // 4. تقييم القادة (75f)
        // 5. تقييم الحفظ (75f)
        // 6. تقييم الكراس (75f)
        // 7. شارات الكفاية (75f)
        // 8. شارات الهواية (75f)
        // 9. المجموع الكلي (80f)
        // 10. المعدل العام (85f - MUST BE LAST)
        val colWidths = floatArrayOf(
            40f,   // 1. الترتيب
            145f,  // 2. اسم الشبل
            75f,   // 3. تقييم الحضور
            75f,   // 4. تقييم القادة
            75f,   // 5. تقييم الحفظ
            75f,   // 6. تقييم الكراس
            75f,   // 7. شارات الكفاية
            75f,   // 8. شارات الهواية
            80f,   // 9. المجموع الكلي
            85f    // 10. المعدل العام
        )
        val totalTableWidth = colWidths.sum()
        val leftMargin = (pageWidth - totalTableWidth) / 2f

        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // رأس الصفحة الكشفية
            var yPos = 32f
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1B5E20")
                textSize = 15f
                typeface = cairoBold
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("الجمهورية الجزائرية الديمقراطية الشعبية", pageWidth / 2f, yPos, textPaint.apply { textAlign = Paint.Align.CENTER })
            yPos += 15f
            canvas.drawText("الكشافة الإسلامية الجزائرية — المحافظة الولائية لسطيف", pageWidth / 2f, yPos, textPaint)
            yPos += 15f
            canvas.drawText("فوج محمد رحال — سرب النُّجُب للأشبال", pageWidth / 2f, yPos, textPaint)
            yPos += 20f

            canvas.drawText("المصفوفة الشاملة لتقييم الأشبال", pageWidth / 2f, yPos, titlePaint)
            yPos += 14f

            val sdf = SimpleDateFormat("dd/MM/yyyy", AppLocale.AR_DZ)
            val subInfo = "تاريخ التقرير: ${sdf.format(Date())} | عدد الاجتماعات المعتمدة: $totalMeetingsHeld | إجمالي الأشبال: ${rows.size}"
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 9f
                typeface = cairoFont
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(subInfo, pageWidth / 2f, yPos, subPaint)
            yPos += 18f

            // رسم جدول المصفوفة
            val startX = leftMargin + totalTableWidth
            val tableTop = yPos
            val rowHeight = 22f

            // ترويسة الجدول
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E8F5E9")
                style = Paint.Style.FILL
            }
            canvas.drawRect(leftMargin, tableTop, leftMargin + totalTableWidth, tableTop + rowHeight, headerBgPaint)
            canvas.drawRect(leftMargin, tableTop, leftMargin + totalTableWidth, tableTop + rowHeight, borderPaint)

            val headers = listOf(
                "الترتيب",
                "اسم الشبل",
                "تقييم الحضور",
                "تقييم القادة",
                "تقييم الحفظ",
                "تقييم الكراس",
                "شارات الكفاية",
                "شارات الهواية",
                "المجموع الكلي",
                "المعدل العام"
            )
            var currentX = startX
            for (i in headers.indices) {
                val w = colWidths[i]
                currentX -= w
                canvas.drawText(headers[i], currentX + w / 2f, tableTop + 15f, headerPaint)
                canvas.drawLine(currentX, tableTop, currentX, tableTop + rowHeight, borderPaint)
            }

            var currentY = tableTop + rowHeight
            val pageRows = rows.drop(pageIndex * rowsPerPage).take(rowsPerPage)

            pageRows.forEachIndexed { idx, row ->
                if (idx % 2 == 1) {
                    canvas.drawRect(leftMargin, currentY, leftMargin + totalTableWidth, currentY + rowHeight, rowBgPaint)
                }
                canvas.drawRect(leftMargin, currentY, leftMargin + totalTableWidth, currentY + rowHeight, borderPaint)

                var xCell = startX
                // 1) الترتيب
                xCell -= colWidths[0]
                val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 10f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("${row.rank}", xCell + colWidths[0] / 2f, currentY + 15f, rankPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 2) اسم الشبل
                xCell -= colWidths[1]
                val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 10f
                    typeface = cairoBold
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(row.scoutName, xCell + colWidths[1] - 8f, currentY + 15f, namePaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 3) تقييم الحضور
                xCell -= colWidths[2]
                drawCellScore(canvas, formatScore(row.attendanceScore), row.attendanceScore, xCell, currentY, colWidths[2], rowHeight, cairoFont)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 4) تقييم القادة
                xCell -= colWidths[3]
                drawCellScore(canvas, formatScore(row.leaderScore), row.leaderScore, xCell, currentY, colWidths[3], rowHeight, cairoFont)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 5) تقييم الحفظ
                xCell -= colWidths[4]
                drawCellScore(canvas, formatScore(row.quranScore), row.quranScore, xCell, currentY, colWidths[4], rowHeight, cairoFont)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 6) تقييم الكراس
                xCell -= colWidths[5]
                drawCellScore(canvas, formatScore(row.notebookScore), row.notebookScore, xCell, currentY, colWidths[5], rowHeight, cairoFont)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 7) شارات الكفاية
                xCell -= colWidths[6]
                val kafaaText = if (row.kafaaPoints != null) row.kafaaPoints.toString() else "—"
                val kafaaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (row.kafaaPoints != null && row.kafaaPoints > 0) Color.parseColor("#1565C0") else Color.GRAY
                    textSize = 10f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(kafaaText, xCell + colWidths[6] / 2f, currentY + 15f, kafaaPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 8) شارات الهواية
                xCell -= colWidths[7]
                val hobbyText = if (row.hobbyPoints != null) row.hobbyPoints.toString() else "—"
                val hobbyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (row.hobbyPoints != null && row.hobbyPoints > 0) Color.parseColor("#1565C0") else Color.GRAY
                    textSize = 10f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(hobbyText, xCell + colWidths[7] / 2f, currentY + 15f, hobbyPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 9) المجموع الكلي
                xCell -= colWidths[8]
                val totalText = if (row.totalScore != null) {
                    if (row.totalScore % 1.0 == 0.0) row.totalScore.toInt().toString() else String.format(java.util.Locale.US, "%.1f", row.totalScore)
                } else "—"
                val totalColor = when {
                    row.totalScore == null -> Color.GRAY
                    row.totalScore < 50.0 -> Color.parseColor("#D32F2F")
                    row.totalScore < 80.0 -> Color.parseColor("#F57C00")
                    else -> Color.parseColor("#2E7D32")
                }
                val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = totalColor
                    textSize = 10.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(totalText, xCell + colWidths[8] / 2f, currentY + 15f, totalPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 10) المعدل العام (MUST BE LAST)
                xCell -= colWidths[9]
                drawCellScore(canvas, formatScore(row.overallScore), row.overallScore, xCell, currentY, colWidths[9], rowHeight, cairoBold, isOverall = true)

                currentY += rowHeight
            }

            // صندوق شرح طريقة الحساب في أسفل الصفحة
            val legendBoxTop = (currentY + 10f).coerceAtMost(pageHeight - 110f)
            val legendBoxHeight = 78f
            val legendBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F9FBE7")
                style = Paint.Style.FILL
            }
            val legendBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#DCEDC8")
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            val legendRect = RectF(leftMargin, legendBoxTop, leftMargin + totalTableWidth, legendBoxTop + legendBoxHeight)
            canvas.drawRoundRect(legendRect, 6f, 6f, legendBgPaint)
            canvas.drawRoundRect(legendRect, 6f, 6f, legendBorderPaint)

            var legY = legendBoxTop + 14f
            val legTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1B5E20")
                textSize = 9f
                typeface = cairoBold
                textAlign = Paint.Align.RIGHT
            }
            val legTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#333333")
                textSize = 8f
                typeface = cairoFont
                textAlign = Paint.Align.RIGHT
            }
            val rightTextX = leftMargin + totalTableWidth - 12f
            canvas.drawText("شرح طريقة الحساب:", rightTextX, legY, legTitlePaint)
            legY += 13f
            canvas.drawText("• المعدل العام: هو ناتج قسمة المجموع الكلي على 4.", rightTextX, legY, legTextPaint)
            legY += 12f
            canvas.drawText("• المجموع الكلي: هو مجموع درجات الحضور + القادة + الحفظ + الكراس + نقاط شارات الكفاية + نقاط شارات الهواية الإضافية.", rightTextX, legY, legTextPaint)
            legY += 12f
            canvas.drawText("• شارات الكفاية: 10 نقاط لكل شارة.", rightTextX, legY, legTextPaint)
            legY += 12f
            canvas.drawText("• شارات الهواية: 5 نقاط لكل شارة إضافية تزيد عن المطلوب في بطاقة التقدم.", rightTextX, legY, legTextPaint)

            // أسفل الصفحة
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 8.5f
                typeface = cairoFont
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("صفحة ${pageIndex + 1} من $totalPages", pageWidth / 2f, pageHeight - 14f, footerPaint)

            pdfDocument.finishPage(page)
        }

        val cacheDir = context.cacheDir
        val file = File(cacheDir, "Evaluation_Matrix_${System.currentTimeMillis()}.pdf")
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        outputStream.close()
        pdfDocument.close()

        return file
    }

    private fun formatScore(score: Double?): String {
        if (score == null) return "—"
        val positive = score.coerceAtLeast(0.0)
        return if (positive % 1.0 == 0.0) {
            "${positive.toInt()}/20"
        } else {
            String.format(java.util.Locale.US, "%.1f/20", positive)
        }
    }

    private fun drawCellScore(
        canvas: Canvas,
        text: String,
        score: Double?,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        font: Typeface?,
        isOverall: Boolean = false
    ) {
        val colorInt = when {
            score == null -> Color.parseColor("#9E9E9E")
            score < 10.0 -> Color.parseColor("#D32F2F")
            score < 16.0 -> Color.parseColor("#F57C00")
            else -> Color.parseColor("#2E7D32")
        }
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            textSize = if (isOverall) 10.5f else 9.5f
            typeface = font
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text, x + width / 2f, y + 15f, pctPaint)
    }

    fun shareMatrixPdf(file: File, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "المصفوفة الشاملة لتقييم الأشبال — سرب النُّجُب")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة المصفوفة كـ PDF"))
    }
}
