package com.example.presentation.reports

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.activity.Activity
import com.example.domain.finance.ExpenseCategory
import com.example.domain.finance.FinanceContribution
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.FinanceSummary
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.finance.ScoutDebt
import java.util.Calendar
import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendanceRow
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.attendance.MeetingWithAttendance
import com.example.domain.attendance.ScoutStatistics
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.BadgeProgress
import com.example.domain.scout.Scout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * مصدّر تقارير PDF الأصلي (Native PDF Exporter)
 * يُنشئ تقارير شاملة بصيغة A4 لملف الشبل وسجل الحضور وقوائم الاجتماعات التدريبية وفق تصميم كشفي عربي.
 */
object PdfExporter {

    private val dateFormatter = SimpleDateFormat("EEEE d MMMM yyyy", com.example.presentation.common.AppLocale.AR_DZ)

    private fun getCairoTypeface(context: Context): Typeface? {
        return try {
            ResourcesCompat.getFont(context, R.font.cairo)
        } catch (e: Exception) { null }
    }

    private fun getBoldCairoTypeface(cairoTypeface: Typeface?): Typeface? {
        return if (cairoTypeface != null) Typeface.create(cairoTypeface, Typeface.BOLD) else null
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue
            val words = trimmed.split(Regex("\\s+"))
            var currentLine = ""
            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    currentLine = candidate
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                    }
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
        }
        return lines
    }

    data class ActivityReportAttendanceEntry(
        val scoutFullName: String,
        val status: AttendanceStatus?,
        val points: Int
    )

    fun exportScoutReport(
        scout: Scout,
        stats: ScoutStatistics,
        activities: List<Activity>,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = getCairoTypeface(context)
        val boldCairo = getBoldCairoTypeface(cairoTypeface)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // قياس A4 القياسي (نقاط)
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        drawScoutReport(canvas, scout, stats, activities, cairoTypeface, boldCairo, context)

        pdfDocument.finishPage(page)

        val pdfFile = File(context.cacheDir, "scout_report_${scout.id}.pdf")

        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()

        return pdfFile
    }

    fun shareScoutPdf(file: File, scoutName: String, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "تقرير الشبل: $scoutName")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير الشبل"))
    }

    private fun drawLogo(
        canvas: Canvas,
        context: Context,
        left: Float,
        top: Float,
        targetWidth: Float = 55f,
        targetHeight: Float = 55f
    ) {
        try {
            val options = BitmapFactory.Options().apply {
                inScaled = false
            }
            val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ennujub_logo, options)
            if (logoBitmap != null) {
                val origWidth = logoBitmap.width.toFloat()
                val origHeight = logoBitmap.height.toFloat()
                val aspectRatio = if (origHeight > 0f) origWidth / origHeight else 1f

                val (drawW, drawH) = if (aspectRatio >= 1f) {
                    val w = targetWidth
                    val h = targetWidth / aspectRatio
                    Pair(w, h)
                } else {
                    val h = targetHeight
                    val w = targetHeight * aspectRatio
                    Pair(w, h)
                }

                val paint = Paint().apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                    isDither = true
                }
                val destRect = RectF(left, top, left + drawW, top + drawH)
                canvas.drawBitmap(logoBitmap, null, destRect, paint)
            }
        } catch (_: Exception) {}
    }

    private fun drawScoutReport(
        canvas: Canvas,
        scout: Scout,
        stats: ScoutStatistics,
        activities: List<Activity>,
        cairoTypeface: Typeface?,
        boldCairo: Typeface?,
        context: Context
    ) {
        val textPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 12f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val titlePaint = Paint().apply {
            color = Color.rgb(46, 125, 50) // الأخضر الكشفي
            textSize = 18f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(200, 169, 81) // الذهبي الكشفي
            textSize = 13f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val headerPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 14f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val linePaint = Paint().apply {
            color = Color.rgb(200, 169, 81)
            strokeWidth = 2.5f
            isAntiAlias = true
        }

        val boxPaint = Paint().apply {
            color = Color.rgb(245, 245, 245)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // رسم الترويسة والشعار
        drawLogo(canvas, context, left = 40f, top = 25f, targetWidth = 55f, targetHeight = 55f)

        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 555f, 50f, titlePaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 70f, subtitlePaint)
        canvas.drawLine(40f, 100f, 555f, 100f, linePaint)

        // عنوان الوثيقة
        val docTitlePaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 16f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("بطاقة المتابعة الكشفية وسجل الحضور", 297f, 130f, docTitlePaint)

        // قسم معلومات الشبل
        canvas.drawRoundRect(40f, 150f, 555f, 240f, 12f, 12f, boxPaint)
        canvas.drawText("المعلومات الكشفية والشخصية", 540f, 172f, headerPaint)
        canvas.drawLine(40f, 180f, 555f, 180f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })

        textPaint.textSize = 11f
        canvas.drawText("الاسم واللقب: ${scout.fullName}", 540f, 202f, textPaint)
        canvas.drawText("العمر: ${scout.age} سنة", 300f, 202f, textPaint)
        canvas.drawText("سنة الالتحاق: ${scout.joinYear}", 150f, 202f, textPaint)

        canvas.drawText("ولي الأمر: ${scout.guardianName} (${scout.guardianRelation})", 540f, 224f, textPaint)
        canvas.drawText("هاتف الولي: ${scout.guardianPhone}", 300f, 224f, textPaint)
        canvas.drawText("الحالة: ${if (scout.isActive) "نشط" else "مؤرشف"}", 150f, 224f, textPaint)

        // قسم إحصائيات الحضور والغياب
        canvas.drawRoundRect(40f, 255f, 555f, 355f, 12f, 12f, boxPaint)
        canvas.drawText("إحصائيات الحضور والتقييم الكشفي", 540f, 277f, headerPaint)
        canvas.drawLine(40f, 285f, 555f, 285f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })

        val statHighlightPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 13f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val percentageFormatted = String.format(Locale.US, "%.1f", stats.attendancePercentage)
        canvas.drawText("نسبة الحضور الإجمالية: $percentageFormatted%", 540f, 308f, statHighlightPaint)
        canvas.drawText("إجمالي الأنشطة المسجلة: ${stats.totalActivities}", 280f, 308f, textPaint)

        canvas.drawText("حاضر: ${stats.presentCount}", 540f, 332f, textPaint)
        canvas.drawText("متأخر: ${stats.lateCount}", 440f, 332f, textPaint)
        canvas.drawText("متأخر جداً: ${stats.veryLateCount}", 340f, 332f, textPaint)
        canvas.drawText("غائب بعذر: ${stats.absentExcusedCount}", 240f, 332f, textPaint)
        canvas.drawText("بدون عذر: ${stats.absentUnexcusedCount}", 140f, 332f, textPaint)
        canvas.drawText("رصيد النقاط: ${stats.totalPoints} نقطة", 540f, 350f, statHighlightPaint)

        // جدول الأنشطة الفائتة / الملاحظات
        canvas.drawText("سجل الغيابات المسجلة عن الأنشطة", 555f, 385f, headerPaint)
        canvas.drawLine(40f, 392f, 555f, 392f, linePaint)

        var yPos = 415f
        if (stats.missedActivities.isEmpty()) {
            canvas.drawText("ما شاء الله! لا توجد أي غيابات مسجلة للشبل حتى الآن.", 540f, yPos, textPaint)
        } else {
            for (item in stats.missedActivities.take(12)) {
                val dateStr = if (item.date > 0) SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ).format(Date(item.date)) else ""
                val statusStr = item.status.arabicLabel
                canvas.drawText("• ${item.title} ($statusStr) — $dateStr", 540f, yPos, textPaint)
                yPos += 22f
            }
        }

        // تذييل الصفحة
        canvas.drawLine(40f, 790f, 555f, 790f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val nowStr = dateFormatter.format(Date())
        canvas.drawText("تاريخ استخراج الوثيقة: $nowStr", 555f, 808f, footerPaint)
        canvas.drawText("وثيقة كشفية صادرة عن تطبيق سرب النُّجُب المعتمد", 200f, 808f, footerPaint)
    }

    fun exportActivityAttendancePdf(
        activity: Activity,
        rows: List<AttendanceRow>,
        trainingMeetingNumber: Int?,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = getCairoTypeface(context)
        val boldCairo = getBoldCairoTypeface(cairoTypeface)
        val rowsPerPage = 25
        val totalPages = maxOf(1, (rows.size + rowsPerPage - 1) / rowsPerPage)

        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val startRow = pageIndex * rowsPerPage
            val endRow = minOf(rows.size, (pageIndex + 1) * rowsPerPage)
            val pageRows = if (rows.isNotEmpty()) rows.subList(startRow, endRow) else emptyList()

            drawActivityAttendancePage(
                canvas = canvas,
                activity = activity,
                pageRows = pageRows,
                startRowIndex = startRow,
                trainingMeetingNumber = trainingMeetingNumber,
                pageNumber = pageIndex + 1,
                totalPages = totalPages,
                totalScouts = rows.size,
                cairoTypeface = cairoTypeface,
                boldCairo = boldCairo,
                context = context
            )

            pdfDocument.finishPage(page)
        }

        val pdfFile = File(context.cacheDir, "attendance_${activity.id}.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareActivityAttendancePdf(file: File, activityTitle: String, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "قائمة حضور: $activityTitle")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير الحضور"))
    }

    fun exportActivityFullReportPdf(
        activity: Activity,
        attendanceList: List<Attendance>,
        meetingTopics: List<String> = activity.meetingTopics,
        context: Context
    ): File {
        val entries = attendanceList.map {
            ActivityReportAttendanceEntry(
                scoutFullName = it.scoutFullName.ifBlank { "شبل كشفي" },
                status = it.attendanceStatus,
                points = it.points
            )
        }
        return generateActivityFullReportDocument(activity, entries, meetingTopics, context)
    }

    @JvmName("exportActivityFullReportPdfFromRows")
    fun exportActivityFullReportPdf(
        activity: Activity,
        attendanceRows: List<AttendanceRow>,
        meetingTopics: List<String> = activity.meetingTopics,
        context: Context
    ): File {
        val entries = attendanceRows.map {
            ActivityReportAttendanceEntry(
                scoutFullName = it.scout.fullName.ifBlank { "شبل كشفي" },
                status = it.attendance?.attendanceStatus,
                points = it.attendance?.points ?: 0
            )
        }
        return generateActivityFullReportDocument(activity, entries, meetingTopics, context)
    }

    fun exportActivityDetailsPdf(
        activity: Activity,
        attendanceList: List<AttendanceRow>,
        context: Context
    ): File {
        return exportActivityFullReportPdf(
            activity = activity,
            attendanceRows = attendanceList,
            meetingTopics = activity.meetingTopics,
            context = context
        )
    }

    private fun generateActivityFullReportDocument(
        activity: Activity,
        items: List<ActivityReportAttendanceEntry>,
        meetingTopics: List<String>,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = getCairoTypeface(context)
        val boldCairo = getBoldCairoTypeface(cairoTypeface)

        val attendanceCount = items.size
        val attendancePages = if (attendanceCount <= 20) {
            1
        } else {
            1 + (attendanceCount - 20 + 25) / 26
        }
        val totalPages = 1 + attendancePages

        // الصفحة 1: بطاقة بيانات النشاط الشاملة، الأهداف، الأدوات، المواضيع، والتقرير الأدبي
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            drawActivityReportPage1(
                canvas = page.canvas,
                activity = activity,
                meetingTopics = meetingTopics,
                cairoTypeface = cairoTypeface,
                boldCairo = boldCairo,
                totalPages = totalPages,
                context = context
            )
            pdfDocument.finishPage(page)
        }

        // الصفحة 2 وما يليها: ملخص الحضور وجدول الحضور المفصل
        for (attPageIndex in 0 until attendancePages) {
            val pageNumber = 2 + attPageIndex
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)

            val startIndex = if (attPageIndex == 0) 0 else 20 + (attPageIndex - 1) * 26
            val endIndex = if (attPageIndex == 0) minOf(20, items.size) else minOf(items.size, 20 + attPageIndex * 26)
            val pageItems = if (items.isNotEmpty() && startIndex < items.size) items.subList(startIndex, endIndex) else emptyList()

            drawActivityReportAttendancePage(
                canvas = page.canvas,
                activity = activity,
                pageItems = pageItems,
                allItems = items,
                startRowIndex = startIndex,
                pageNumber = pageNumber,
                totalPages = totalPages,
                isFirstAttendancePage = (attPageIndex == 0),
                cairoTypeface = cairoTypeface,
                boldCairo = boldCairo,
                context = context
            )
            pdfDocument.finishPage(page)
        }

        val pdfFile = File(context.cacheDir, "activity_full_report_${activity.id}.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareActivityPdf(file: File, activityTitle: String, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "التقرير الشامل للنشاط: $activityTitle")
            putExtra(Intent.EXTRA_TEXT, "مرفق التقرير الشامل للنشاط الكشفي ($activityTitle) متضمناً البطاقة الفنية، الأهداف، وسجل الحضور بصيغة PDF.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة التقرير الشامل للنشاط"))
    }

    fun exportTrainingMeetingsRegisterPdf(
        meetings: List<MeetingWithAttendance>,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = getCairoTypeface(context) ?: Typeface.DEFAULT

        val chronologicalMeetings = meetings.sortedBy { it.number }
        val allScouts = chronologicalMeetings.firstOrNull()?.rows?.map { it.scout } ?: emptyList()

        if (chronologicalMeetings.isEmpty() || allScouts.isEmpty()) {
            val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            drawEmptyRegisterMatrixPage(page.canvas, cairoTypeface, context)
            pdfDocument.finishPage(page)
        } else {
            val minRowHeight = 18f
            val maxRowsPerPage = maxOf(1, (457f / minRowHeight).toInt())
            val maxMeetingsPerPage = 5 // max 5 meetings per landscape page (~118f per column)
            val meetingChunks = chronologicalMeetings.chunked(maxMeetingsPerPage)
            val scoutChunks = allScouts.chunked(maxRowsPerPage)

            val totalPages = maxOf(1, meetingChunks.size * scoutChunks.size)
            var pageIndex = 1

            for (meetingChunk in meetingChunks) {
                for (scoutChunk in scoutChunks) {
                    val pageInfo = PdfDocument.PageInfo.Builder(842, 595, pageIndex).create()
                    val page = pdfDocument.startPage(pageInfo)

                    drawTrainingMeetingsMatrixLandscape(
                        canvas = page.canvas,
                        meetings = meetingChunk,
                        allMeetings = chronologicalMeetings,
                        pageScouts = scoutChunk,
                        pageNumber = pageIndex,
                        totalPages = totalPages,
                        cairoTypeface = cairoTypeface,
                        context = context
                    )

                    pdfDocument.finishPage(page)
                    pageIndex++
                }
            }
        }

        val pdfFile = File(context.cacheDir, "training_meetings_register.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareTrainingMeetingsRegisterPdf(file: File, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "سجل الاجتماعات التدريبية — سرب النُّجُب")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة سجل الاجتماعات التدريبية كـ PDF"))
    }

    val ARABIC_MONTHS = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    fun exportMonthlyStatementPdf(
        month: Int,
        year: Int,
        transactions: List<FinanceTransaction>,
        summary: FinanceSummary,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = try {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }

        val cal = Calendar.getInstance()
        val monthTransactions = transactions.filter { tx ->
            cal.timeInMillis = tx.date
            val m = cal.get(Calendar.MONTH) + 1
            val y = cal.get(Calendar.YEAR)
            m == month && y == year
        }.sortedBy { it.date }

        val priorTransactions = transactions.filter { tx ->
            cal.timeInMillis = tx.date
            val m = cal.get(Calendar.MONTH) + 1
            val y = cal.get(Calendar.YEAR)
            y < year || (y == year && m < month)
        }

        val openingBalance = priorTransactions.filter { it.isIncome() }.sumOf { it.amount } -
                priorTransactions.filter { !it.isIncome() }.sumOf { it.amount }
        val totalIncome = monthTransactions.filter { it.isIncome() }.sumOf { it.amount }
        val totalExpenses = monthTransactions.filter { !it.isIncome() }.sumOf { it.amount }
        val closingBalance = openingBalance + totalIncome - totalExpenses

        // FIX 2: Aggregate subscriptions & contributions in monthly statement
        val (aggregatedRaw, individualList) = monthTransactions.partition { tx ->
            tx.type == FinanceTransactionType.CONTRIBUTION ||
                    tx.type.key.equals("contribution", ignoreCase = true) ||
                    tx.type.key.equals("subscription", ignoreCase = true)
        }

        val aggregatedSynthetic = aggregatedRaw.groupBy { tx ->
            var desc = tx.description.trim()
            if (!tx.source.isNullOrBlank() && desc.endsWith(" - ${tx.source}")) {
                desc = desc.removeSuffix(" - ${tx.source}").trim()
            } else if (desc.contains(" - ") && (desc.startsWith("مساهمة:") || desc.startsWith("اشتراك:"))) {
                desc = desc.substringBeforeLast(" - ").trim()
            }
            desc.ifEmpty { tx.description }
        }.map { (groupDesc, txList) ->
            val latestDate = txList.maxOfOrNull { it.date } ?: System.currentTimeMillis()
            val sumAmount = txList.sumOf { it.amount }
            val count = txList.size
            val sampleType = txList.first().type
            val syntheticDesc = if (groupDesc.startsWith("مجموع")) {
                groupDesc
            } else {
                "مجموع $groupDesc"
            }
            FinanceTransaction(
                id = "synthetic_${groupDesc.hashCode()}",
                type = sampleType,
                amount = sumAmount,
                date = latestDate,
                description = "$syntheticDesc ($count)",
                category = null,
                source = "الأشبال",
                createdAt = latestDate
            )
        }

        val finalTableTransactions = (individualList + aggregatedSynthetic).sortedBy { it.date }

        val rowsPerPageFirst = 18
        val rowsPerPageSubsequent = 24
        val totalItems = finalTableTransactions.size
        val totalPages = if (totalItems <= rowsPerPageFirst) {
            1
        } else {
            1 + maxOf(1, (totalItems - rowsPerPageFirst + rowsPerPageSubsequent - 1) / rowsPerPageSubsequent)
        }

        var currentIndex = 0
        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val isFirstPage = (pageIndex == 0)
            val pageSize = if (isFirstPage) rowsPerPageFirst else rowsPerPageSubsequent
            val endIndex = minOf(totalItems, currentIndex + pageSize)
            val pageRows = if (totalItems > 0 && currentIndex < totalItems) {
                finalTableTransactions.subList(currentIndex, endIndex)
            } else {
                emptyList()
            }
            currentIndex = endIndex

            drawMonthlyStatementPage(
                canvas = canvas,
                month = month,
                year = year,
                pageRows = pageRows,
                pageIndex = pageIndex + 1,
                totalPages = totalPages,
                isFirstPage = isFirstPage,
                openingBalance = openingBalance,
                totalIncome = totalIncome,
                totalExpenses = totalExpenses,
                closingBalance = closingBalance,
                cairoTypeface = cairoTypeface,
                context = context
            )

            pdfDocument.finishPage(page)
        }

        val pdfFile = File(context.cacheDir, "monthly_statement_${year}_${month}.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareMonthlyStatementPdf(file: File, month: Int, year: Int, context: Context) {
        val monthName = ARABIC_MONTHS.getOrElse(month - 1) { "شهر $month" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "كشف حساب لشهر $monthName $year — سرب النُّجُب")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة كشف الحساب الشهري كـ PDF"))
    }

    fun exportExpensesByCategoryPdf(
        transactions: List<FinanceTransaction>,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = try {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        val boldCairo = Typeface.create(cairoTypeface, Typeface.BOLD)

        val expenses = transactions.filter { !it.isIncome() }
        val totalExpenses = expenses.sumOf { it.amount }

        val categoryGroups = ExpenseCategory.entries.map { cat ->
            val catExpenses = expenses.filter { (it.category ?: ExpenseCategory.OTHER) == cat }
            val sum = catExpenses.sumOf { it.amount }
            val count = catExpenses.size
            val percentage = if (totalExpenses > 0) (sum.toFloat() / totalExpenses * 100f) else 0f
            CategoryExpenseSummary(
                category = cat,
                totalAmount = sum,
                transactionCount = count,
                percentage = percentage
            )
        }.filter { it.totalAmount > 0 }
            .sortedByDescending { it.totalAmount }

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val minDate = expenses.map { it.date }.filter { it > 0 }.minOrNull()
        val maxDate = expenses.map { it.date }.filter { it > 0 }.maxOrNull()

        drawExpensesByCategoryPage(
            canvas = canvas,
            categoryGroups = categoryGroups,
            totalExpenses = totalExpenses,
            totalCount = expenses.size,
            minDate = minDate,
            maxDate = maxDate,
            cairoTypeface = cairoTypeface,
            boldCairo = boldCairo,
            context = context
        )

        pdfDocument.finishPage(page)

        val pdfFile = File(context.cacheDir, "expenses_by_category.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareExpensesByCategoryPdf(file: File, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "تقرير المصاريف حسب التصنيف — سرب النُّجُب")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير المصاريف حسب التصنيف كـ PDF"))
    }

    data class ActivityDebtInfo(
        val activityTitle: String,
        val totalRequired: Int,
        val totalPaid: Int,
        val totalDebt: Int
    )

    // 1) تقرير الاشتراكات والديون (ملخص عام للمحصّل والمتبقي)
    fun exportSubscriptionsAndDebtsSummaryPdf(
        totalSubscriptionsPaid: Int,
        totalContributionsPaid: Int,
        totalOutstandingDebt: Int,
        activitiesWithDebt: List<ActivityDebtInfo> = emptyList(),
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = try {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        val boldCairo = Typeface.create(cairoTypeface, Typeface.BOLD)

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        drawSubscriptionsSummaryPage(
            canvas = canvas,
            totalSubscriptionsPaid = totalSubscriptionsPaid,
            totalContributionsPaid = totalContributionsPaid,
            totalOutstandingDebt = totalOutstandingDebt,
            activitiesWithDebt = activitiesWithDebt,
            cairoTypeface = cairoTypeface,
            boldCairo = boldCairo,
            context = context
        )

        pdfDocument.finishPage(page)

        val pdfFile = File(context.cacheDir, "subscriptions_and_debts_summary.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareSubscriptionsAndDebtsSummaryPdf(file: File, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "تقرير الاشتراكات والديون — سرب النُّجُب")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير الاشتراكات والديون كـ PDF"))
    }

    // 2) تقرير الديون التفصيلي
    fun exportDetailedDebtsPdf(
        debts: List<ScoutDebt>,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = try {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        val boldCairo = Typeface.create(cairoTypeface, Typeface.BOLD)

        val filteredDebts = debts.filter { it.totalDebt > 0 }.sortedByDescending { it.totalDebt }
        val totalDebtSum = filteredDebts.sumOf { it.totalDebt }
        val totalRequiredSum = filteredDebts.sumOf { it.totalRequired }
        val totalPaidSum = filteredDebts.sumOf { it.totalPaid }

        val rowsPerPage = 22
        val totalItems = filteredDebts.size
        val totalPages = if (totalItems <= rowsPerPage) 1 else ((totalItems + rowsPerPage - 1) / rowsPerPage)

        var currentIndex = 0
        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val isFirstPage = (pageIndex == 0)
            val isLastPage = (pageIndex == totalPages - 1)
            val endIndex = minOf(totalItems, currentIndex + rowsPerPage)
            val pageRows = if (totalItems > 0 && currentIndex < totalItems) {
                filteredDebts.subList(currentIndex, endIndex)
            } else {
                emptyList()
            }
            currentIndex = endIndex

            drawDetailedDebtsPage(
                canvas = canvas,
                pageRows = pageRows,
                pageIndex = pageIndex,
                totalPages = totalPages,
                isFirstPage = isFirstPage,
                isLastPage = isLastPage,
                totalScoutsCount = filteredDebts.size,
                totalRequired = totalRequiredSum,
                totalPaid = totalPaidSum,
                totalDebt = totalDebtSum,
                cairoTypeface = cairoTypeface,
                boldCairo = boldCairo,
                context = context
            )

            pdfDocument.finishPage(page)
        }

        val pdfFile = File(context.cacheDir, "detailed_debts_report.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareDetailedDebtsPdf(file: File, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "تقرير الديون التفصيلي — سرب النُّجُب")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير الديون التفصيلي كـ PDF"))
    }

    // 3) التقرير السنوي الشامل
    fun exportAnnualStatementPdf(
        year: Int,
        transactions: List<FinanceTransaction>,
        summary: FinanceSummary,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = try {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        val boldCairo = Typeface.create(cairoTypeface, Typeface.BOLD)

        val cal = Calendar.getInstance()
        val yearTransactions = transactions.filter { tx ->
            cal.timeInMillis = tx.date
            cal.get(Calendar.YEAR) == year
        }

        val totalIncome = yearTransactions.filter { it.isIncome() }.sumOf { it.amount }
        val totalExpenses = yearTransactions.filter { !it.isIncome() }.sumOf { it.amount }
        val netBalance = totalIncome - totalExpenses

        val monthsStats = (1..12).map { m ->
            val mTx = yearTransactions.filter { tx ->
                cal.timeInMillis = tx.date
                (cal.get(Calendar.MONTH) + 1) == m
            }
            val inc = mTx.filter { it.isIncome() }.sumOf { it.amount }
            val exp = mTx.filter { !it.isIncome() }.sumOf { it.amount }
            AnnualMonthStat(
                monthIndex = m,
                monthName = ARABIC_MONTHS.getOrElse(m - 1) { "شهر $m" },
                income = inc,
                expense = exp,
                net = inc - exp
            )
        }

        val expensesList = yearTransactions.filter { !it.isIncome() }
        val topCategories = ExpenseCategory.entries.map { cat ->
            val catSum = expensesList.filter { (it.category ?: ExpenseCategory.OTHER) == cat }.sumOf { it.amount }
            Pair(cat, catSum)
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(3)

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        drawAnnualStatementPage(
            canvas = canvas,
            year = year,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netBalance = netBalance,
            monthsStats = monthsStats,
            topCategories = topCategories,
            cairoTypeface = cairoTypeface,
            boldCairo = boldCairo,
            context = context
        )

        pdfDocument.finishPage(page)

        val pdfFile = File(context.cacheDir, "annual_statement_$year.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareAnnualStatementPdf(file: File, year: Int, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "التقرير السنوي الشامل لعام $year — سرب النُّجُب")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة التقرير السنوي الشامل كـ PDF"))
    }

    // 4) كشف حساب شبل
    fun exportScoutStatementPdf(
        scout: Scout,
        subscriptions: List<FinanceSubscription>,
        contributions: List<FinanceContribution>,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = try {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        val boldCairo = Typeface.create(cairoTypeface, Typeface.BOLD)

        val sortedSubs = subscriptions.sortedByDescending { it.activityDate }
        val sortedContribs = contributions.sortedByDescending { it.activityDate }

        val totalSubsRequired = sortedSubs.sumOf { it.requiredAmount }
        val totalSubsPaid = sortedSubs.sumOf { it.paidAmount }

        val totalContribsRequired = sortedContribs.sumOf { it.requiredAmount }
        val totalContribsPaid = sortedContribs.sumOf { it.paidAmount }

        val totalRequired = totalSubsRequired + totalContribsRequired
        val totalPaid = totalSubsPaid + totalContribsPaid
        val totalDebt = (totalRequired - totalPaid).coerceAtLeast(0)

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        drawScoutStatementPage(
            canvas = canvas,
            scout = scout,
            subscriptions = sortedSubs,
            contributions = sortedContribs,
            totalRequired = totalRequired,
            totalPaid = totalPaid,
            totalDebt = totalDebt,
            cairoTypeface = cairoTypeface,
            boldCairo = boldCairo,
            context = context
        )

        pdfDocument.finishPage(page)

        val pdfFile = File(context.cacheDir, "scout_statement_${scout.id}.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareScoutStatementPdf(file: File, scoutName: String, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "كشف حساب الشبل: $scoutName — سرب النُّجُب")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة كشف حساب الشبل كـ PDF"))
    }

    private data class AnnualMonthStat(
        val monthIndex: Int,
        val monthName: String,
        val income: Int,
        val expense: Int,
        val net: Int
    )

    private fun drawSubscriptionsSummaryPage(
        canvas: Canvas,
        totalSubscriptionsPaid: Int,
        totalContributionsPaid: Int,
        totalOutstandingDebt: Int,
        activitiesWithDebt: List<ActivityDebtInfo>,
        cairoTypeface: Typeface,
        boldCairo: Typeface,
        context: Context
    ) {
        val primaryGreen = Color.rgb(46, 125, 50)
        val gold = Color.rgb(200, 169, 81)

        val headerPaint = Paint().apply {
            color = primaryGreen
            textSize = 15f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint().apply {
            color = gold
            textSize = 11f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = gold
            strokeWidth = 2f
            isAntiAlias = true
        }

        // Header
        drawLogo(canvas, context, left = 40f, top = 25f, targetWidth = 50f, targetHeight = 50f)
        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 555f, 45f, headerPaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 63f, subtitlePaint)
        canvas.drawLine(40f, 85f, 555f, 85f, linePaint)

        var currentY = 112f

        // Document Title
        val titlePaint = Paint().apply {
            color = primaryGreen
            textSize = 16f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("تقرير الاشتراكات والديون", 297f, currentY, titlePaint)
        currentY += 18f

        val subTitleDocPaint = Paint().apply {
            color = Color.rgb(100, 100, 100)
            textSize = 9.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ملخص عام للمحصّل والمتبقي من اشتراكات ومساهمات الأشبال", 297f, currentY, subTitleDocPaint)
        currentY += 22f

        // 3 Summary Cards: Subscriptions, Contributions, Outstanding Debt
        val cardWidth = 165f
        val cardHeight = 65f
        val gap = 10f

        val cardBgPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardBorderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        val cardTitlePaint = Paint().apply {
            textSize = 9f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val cardValuePaint = Paint().apply {
            textSize = 12f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Card 1: الاشتراكات المحصلة (right)
        val c1Left = 555f - cardWidth
        val c1Right = 555f
        cardBgPaint.color = Color.rgb(243, 248, 243)
        cardBorderPaint.color = Color.rgb(200, 230, 200)
        canvas.drawRoundRect(c1Left, currentY, c1Right, currentY + cardHeight, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(c1Left, currentY, c1Right, currentY + cardHeight, 8f, 8f, cardBorderPaint)
        cardTitlePaint.color = Color.rgb(80, 80, 80)
        canvas.drawText("اشتراكات الاجتماعات", c1Left + cardWidth / 2f, currentY + 24f, cardTitlePaint)
        cardValuePaint.color = primaryGreen
        canvas.drawText("$totalSubscriptionsPaid دج", c1Left + cardWidth / 2f, currentY + 48f, cardValuePaint)

        // Card 2: المساهمات المحصلة (middle)
        val c2Left = c1Left - gap - cardWidth
        val c2Right = c1Left - gap
        cardBgPaint.color = Color.rgb(243, 248, 243)
        cardBorderPaint.color = Color.rgb(200, 230, 200)
        canvas.drawRoundRect(c2Left, currentY, c2Right, currentY + cardHeight, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(c2Left, currentY, c2Right, currentY + cardHeight, 8f, 8f, cardBorderPaint)
        canvas.drawText("مساهمات الأنشطة الكبرى", c2Left + cardWidth / 2f, currentY + 24f, cardTitlePaint)
        cardValuePaint.color = primaryGreen
        canvas.drawText("$totalContributionsPaid دج", c2Left + cardWidth / 2f, currentY + 48f, cardValuePaint)

        // Card 3: الديون المتبقية (left)
        val c3Left = 40f
        val c3Right = 40f + cardWidth
        cardBgPaint.color = Color.rgb(255, 245, 245)
        cardBorderPaint.color = Color.rgb(245, 200, 200)
        canvas.drawRoundRect(c3Left, currentY, c3Right, currentY + cardHeight, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(c3Left, currentY, c3Right, currentY + cardHeight, 8f, 8f, cardBorderPaint)
        canvas.drawText("إجمالي الديون المتبقية", c3Left + cardWidth / 2f, currentY + 24f, cardTitlePaint)
        cardValuePaint.color = Color.rgb(198, 40, 40)
        canvas.drawText("$totalOutstandingDebt دج", c3Left + cardWidth / 2f, currentY + 48f, cardValuePaint)

        currentY += cardHeight + 25f

        // Activities with Debt Table Header
        val sectionTitlePaint = Paint().apply {
            color = primaryGreen
            textSize = 12f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("الأنشطة والفعاليات ذات الديون المتبقية", 555f, currentY, sectionTitlePaint)
        currentY += 12f

        // Table Header
        val thBgPaint = Paint().apply {
            color = Color.rgb(238, 243, 238)
            style = Paint.Style.FILL
        }
        val thBorderPaint = Paint().apply {
            color = Color.rgb(200, 210, 200)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val thTextPaint = Paint().apply {
            color = primaryGreen
            textSize = 9.5f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawRect(40f, currentY, 555f, currentY + 24f, thBgPaint)
        canvas.drawRect(40f, currentY, 555f, currentY + 24f, thBorderPaint)

        canvas.drawText("النشاط", 550f, currentY + 16f, thTextPaint)
        canvas.drawText("إجمالي المطلوب", 350f, currentY + 16f, thTextPaint)
        canvas.drawText("إجمالي المحصل", 230f, currentY + 16f, thTextPaint)
        thTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الدين المتبقي", 45f, currentY + 16f, thTextPaint)

        currentY += 24f

        val textPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 9f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val rowBgPaint = Paint().apply {
            color = Color.rgb(252, 252, 252)
            style = Paint.Style.FILL
        }
        val rowBorderPaint = Paint().apply {
            color = Color.rgb(235, 238, 235)
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }

        if (activitiesWithDebt.isEmpty()) {
            val emptyPaint = Paint().apply {
                color = Color.GRAY
                textSize = 10f
                typeface = cairoTypeface
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("لا توجد ديون مستحقة على أي نشاط — جميع الاشتراكات والمساهمات محصلة بالكامل", 297f, currentY + 30f, emptyPaint)
            currentY += 50f
        } else {
            val displayList = activitiesWithDebt.take(18)
            for ((i, act) in displayList.withIndex()) {
                val rowTop = currentY
                val rowBottom = currentY + 24f
                if (i % 2 == 1) {
                    canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBgPaint)
                }
                canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBorderPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                val titleTrunc = if (act.activityTitle.length > 28) act.activityTitle.take(27) + "…" else act.activityTitle
                canvas.drawText(titleTrunc, 550f, rowTop + 16f, textPaint)
                canvas.drawText("${act.totalRequired} دج", 350f, rowTop + 16f, textPaint)
                canvas.drawText("${act.totalPaid} دج", 230f, rowTop + 16f, textPaint)

                val debtPaint = Paint().apply {
                    color = Color.rgb(198, 40, 40)
                    textSize = 9.5f
                    typeface = boldCairo
                    isAntiAlias = true
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText("${act.totalDebt} دج", 45f, rowTop + 16f, debtPaint)
                currentY += 24f
            }

            // Total row
            val totalRowPaint = Paint().apply {
                color = Color.rgb(245, 245, 245)
                style = Paint.Style.FILL
            }
            canvas.drawRect(40f, currentY, 555f, currentY + 26f, totalRowPaint)
            canvas.drawRect(40f, currentY, 555f, currentY + 26f, thBorderPaint)

            val totalTextPaint = Paint().apply {
                color = primaryGreen
                textSize = 10f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("المجموع الإجمالي للديون المتبقية:", 550f, currentY + 17f, totalTextPaint)
            val totalValPaint = Paint().apply {
                color = Color.rgb(198, 40, 40)
                textSize = 11f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("$totalOutstandingDebt دج", 45f, currentY + 17f, totalValPaint)
        }

        // Footer
        canvas.drawLine(40f, 800f, 555f, 800f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerDate = SimpleDateFormat("yyyy/MM/dd HH:mm", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9f
            typeface = cairoTypeface
            isAntiAlias = true
        }
        footerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("تقرير مالي رسمي — سرب النُّجُب — فوج محمد رحال", 555f, 815f, footerPaint)
        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("تم الإنشاء: $footerDate", 40f, 815f, footerPaint)
    }

    private fun drawDetailedDebtsPage(
        canvas: Canvas,
        pageRows: List<ScoutDebt>,
        pageIndex: Int,
        totalPages: Int,
        isFirstPage: Boolean,
        isLastPage: Boolean,
        totalScoutsCount: Int,
        totalRequired: Int,
        totalPaid: Int,
        totalDebt: Int,
        cairoTypeface: Typeface,
        boldCairo: Typeface,
        context: Context
    ) {
        val primaryGreen = Color.rgb(46, 125, 50)
        val gold = Color.rgb(200, 169, 81)

        val headerPaint = Paint().apply {
            color = primaryGreen
            textSize = 15f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint().apply {
            color = gold
            textSize = 11f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = gold
            strokeWidth = 2f
            isAntiAlias = true
        }

        drawLogo(canvas, context, left = 40f, top = 25f, targetWidth = 50f, targetHeight = 50f)
        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 555f, 45f, headerPaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 63f, subtitlePaint)
        canvas.drawLine(40f, 85f, 555f, 85f, linePaint)

        var currentY = 110f

        if (isFirstPage) {
            val titlePaint = Paint().apply {
                color = primaryGreen
                textSize = 16f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("تقرير الديون التفصيلي للأشبال", 297f, currentY, titlePaint)
            currentY += 18f

            // Summary Box
            val summaryBoxPaint = Paint().apply {
                color = Color.rgb(255, 248, 248)
                style = Paint.Style.FILL
            }
            val summaryBorderPaint = Paint().apply {
                color = Color.rgb(240, 215, 215)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRoundRect(40f, currentY, 555f, currentY + 50f, 8f, 8f, summaryBoxPaint)
            canvas.drawRoundRect(40f, currentY, 555f, currentY + 50f, 8f, 8f, summaryBorderPaint)

            val summaryTextPaint = Paint().apply {
                color = Color.rgb(60, 60, 60)
                textSize = 9.5f
                typeface = cairoTypeface
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            val summaryValPaint = Paint().apply {
                textSize = 11f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }

            canvas.drawText("عدد الأشبال المدينين: $totalScoutsCount شبل", 540f, currentY + 22f, summaryTextPaint)
            canvas.drawText("إجمالي المطلوب: $totalRequired دج | المدفوع: $totalPaid دج", 540f, currentY + 40f, summaryTextPaint)

            summaryValPaint.color = Color.rgb(198, 40, 40)
            canvas.drawText("إجمالي الديون: $totalDebt دج", 55f, currentY + 30f, summaryValPaint)

            currentY += 65f
        }

        // Table Header
        val thBgPaint = Paint().apply {
            color = Color.rgb(238, 243, 238)
            style = Paint.Style.FILL
        }
        val thBorderPaint = Paint().apply {
            color = Color.rgb(200, 210, 200)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val thTextPaint = Paint().apply {
            color = primaryGreen
            textSize = 9.5f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawRect(40f, currentY, 555f, currentY + 24f, thBgPaint)
        canvas.drawRect(40f, currentY, 555f, currentY + 24f, thBorderPaint)

        canvas.drawText("اسم الشبل", 550f, currentY + 16f, thTextPaint)
        canvas.drawText("المطلوب", 330f, currentY + 16f, thTextPaint)
        canvas.drawText("المدفوع", 210f, currentY + 16f, thTextPaint)
        thTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الدين المستحق", 45f, currentY + 16f, thTextPaint)

        currentY += 24f

        if (pageRows.isEmpty() && isFirstPage) {
            val emptyPaint = Paint().apply {
                color = Color.GRAY
                textSize = 10f
                typeface = cairoTypeface
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("لا توجد ديون مستحقة — جميع الأشبال مسددون لالتزاماتهم بالكامل", 297f, currentY + 35f, emptyPaint)
        } else {
            val rowBgPaint = Paint().apply {
                color = Color.rgb(252, 252, 252)
                style = Paint.Style.FILL
            }
            val rowBorderPaint = Paint().apply {
                color = Color.rgb(235, 238, 235)
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
            }
            val textPaint = Paint().apply {
                color = Color.rgb(33, 33, 33)
                textSize = 9.5f
                typeface = cairoTypeface
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            val debtValPaint = Paint().apply {
                color = Color.rgb(198, 40, 40)
                textSize = 9.5f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }

            for ((i, debt) in pageRows.withIndex()) {
                val rowTop = currentY
                val rowBottom = currentY + 24f
                if (i % 2 == 1) {
                    canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBgPaint)
                }
                canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBorderPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                val nameTrunc = if (debt.scoutFullName.length > 25) debt.scoutFullName.take(24) + "…" else debt.scoutFullName
                canvas.drawText(nameTrunc, 550f, rowTop + 16f, textPaint)
                canvas.drawText("${debt.totalRequired} دج", 330f, rowTop + 16f, textPaint)
                canvas.drawText("${debt.totalPaid} دج", 210f, rowTop + 16f, textPaint)

                canvas.drawText("${debt.totalDebt} دج", 45f, rowTop + 16f, debtValPaint)
                currentY += 24f
            }

            if (isLastPage) {
                val totalRowPaint = Paint().apply {
                    color = Color.rgb(245, 245, 245)
                    style = Paint.Style.FILL
                }
                canvas.drawRect(40f, currentY, 555f, currentY + 26f, totalRowPaint)
                canvas.drawRect(40f, currentY, 555f, currentY + 26f, thBorderPaint)

                val totalLabelPaint = Paint().apply {
                    color = primaryGreen
                    textSize = 10f
                    typeface = boldCairo
                    isAntiAlias = true
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("المجموع الكلي للديون المستحقة:", 550f, currentY + 17f, totalLabelPaint)
                val totalNumberPaint = Paint().apply {
                    color = Color.rgb(198, 40, 40)
                    textSize = 11f
                    typeface = boldCairo
                    isAntiAlias = true
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText("$totalDebt دج", 45f, currentY + 17f, totalNumberPaint)
            }
        }

        // Footer
        canvas.drawLine(40f, 800f, 555f, 800f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerDate = SimpleDateFormat("yyyy/MM/dd HH:mm", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9f
            typeface = cairoTypeface
            isAntiAlias = true
        }
        footerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("صفحة ${pageIndex + 1} من $totalPages", 555f, 815f, footerPaint)
        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("تم الإنشاء: $footerDate", 40f, 815f, footerPaint)
    }

    private fun drawAnnualStatementPage(
        canvas: Canvas,
        year: Int,
        totalIncome: Int,
        totalExpenses: Int,
        netBalance: Int,
        monthsStats: List<AnnualMonthStat>,
        topCategories: List<Pair<ExpenseCategory, Int>>,
        cairoTypeface: Typeface,
        boldCairo: Typeface,
        context: Context
    ) {
        val primaryGreen = Color.rgb(46, 125, 50)
        val gold = Color.rgb(200, 169, 81)

        val headerPaint = Paint().apply {
            color = primaryGreen
            textSize = 15f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint().apply {
            color = gold
            textSize = 11f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = gold
            strokeWidth = 2f
            isAntiAlias = true
        }

        drawLogo(canvas, context, left = 40f, top = 25f, targetWidth = 50f, targetHeight = 50f)
        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 555f, 45f, headerPaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 63f, subtitlePaint)
        canvas.drawLine(40f, 85f, 555f, 85f, linePaint)

        var currentY = 112f

        val titlePaint = Paint().apply {
            color = primaryGreen
            textSize = 16f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("التقرير السنوي الشامل لعام $year", 297f, currentY, titlePaint)
        currentY += 22f

        // 3 Overview Cards: Total Income, Total Expenses, Net Balance
        val cardWidth = 165f
        val cardHeight = 60f
        val gap = 10f

        val cardBgPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
        val cardBorderPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }
        val cardTitlePaint = Paint().apply {
            color = Color.rgb(80, 80, 80)
            textSize = 9f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val cardValuePaint = Paint().apply {
            textSize = 12f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Card 1: الواردات (right)
        val c1Left = 555f - cardWidth
        cardBgPaint.color = Color.rgb(243, 248, 243)
        cardBorderPaint.color = Color.rgb(200, 230, 200)
        canvas.drawRoundRect(c1Left, currentY, 555f, currentY + cardHeight, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(c1Left, currentY, 555f, currentY + cardHeight, 8f, 8f, cardBorderPaint)
        canvas.drawText("إجمالي الواردات السنوية", c1Left + cardWidth / 2f, currentY + 22f, cardTitlePaint)
        cardValuePaint.color = primaryGreen
        canvas.drawText("+$totalIncome دج", c1Left + cardWidth / 2f, currentY + 44f, cardValuePaint)

        // Card 2: المصاريف (middle)
        val c2Left = c1Left - gap - cardWidth
        val c2Right = c1Left - gap
        cardBgPaint.color = Color.rgb(255, 245, 245)
        cardBorderPaint.color = Color.rgb(245, 200, 200)
        canvas.drawRoundRect(c2Left, currentY, c2Right, currentY + cardHeight, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(c2Left, currentY, c2Right, currentY + cardHeight, 8f, 8f, cardBorderPaint)
        canvas.drawText("إجمالي المصاريف السنوية", c2Left + cardWidth / 2f, currentY + 22f, cardTitlePaint)
        cardValuePaint.color = Color.rgb(198, 40, 40)
        canvas.drawText("-$totalExpenses دج", c2Left + cardWidth / 2f, currentY + 44f, cardValuePaint)

        // Card 3: الرصيد الصافي (left)
        val c3Left = 40f
        val c3Right = 40f + cardWidth
        cardBgPaint.color = if (netBalance >= 0) Color.rgb(240, 248, 255) else Color.rgb(255, 245, 245)
        cardBorderPaint.color = if (netBalance >= 0) Color.rgb(200, 220, 245) else Color.rgb(245, 200, 200)
        canvas.drawRoundRect(c3Left, currentY, c3Right, currentY + cardHeight, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(c3Left, currentY, c3Right, currentY + cardHeight, 8f, 8f, cardBorderPaint)
        canvas.drawText("الرصيد الصافي للعام", c3Left + cardWidth / 2f, currentY + 22f, cardTitlePaint)
        cardValuePaint.color = if (netBalance >= 0) Color.rgb(25, 118, 210) else Color.rgb(198, 40, 40)
        canvas.drawText("$netBalance دج", c3Left + cardWidth / 2f, currentY + 44f, cardValuePaint)

        currentY += cardHeight + 18f

        // 12 Months Table Title
        val sectionTitlePaint = Paint().apply {
            color = primaryGreen
            textSize = 11f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("توزيع الواردات والمصاريف على مدار أشهر السنة:", 555f, currentY, sectionTitlePaint)
        currentY += 10f

        // Table Header
        val thBgPaint = Paint().apply { color = Color.rgb(238, 243, 238); style = Paint.Style.FILL }
        val thBorderPaint = Paint().apply { color = Color.rgb(200, 210, 200); style = Paint.Style.STROKE; strokeWidth = 1f }
        val thTextPaint = Paint().apply {
            color = primaryGreen
            textSize = 9f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawRect(40f, currentY, 555f, currentY + 22f, thBgPaint)
        canvas.drawRect(40f, currentY, 555f, currentY + 22f, thBorderPaint)

        canvas.drawText("الشهر", 550f, currentY + 15f, thTextPaint)
        canvas.drawText("الواردات", 350f, currentY + 15f, thTextPaint)
        canvas.drawText("المصاريف", 220f, currentY + 15f, thTextPaint)
        thTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الفارق الشهري", 45f, currentY + 15f, thTextPaint)

        currentY += 22f

        val rowBgPaint = Paint().apply { color = Color.rgb(252, 252, 252); style = Paint.Style.FILL }
        val rowBorderPaint = Paint().apply { color = Color.rgb(235, 238, 235); style = Paint.Style.STROKE; strokeWidth = 0.5f }
        val cellTextPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 8.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val netValPaint = Paint().apply {
            textSize = 8.5f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }

        for ((i, stat) in monthsStats.withIndex()) {
            val rowTop = currentY
            val rowBottom = currentY + 20f
            if (i % 2 == 1) {
                canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBgPaint)
            }
            canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBorderPaint)

            cellTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${stat.monthName} (${stat.monthIndex})", 550f, rowTop + 14f, cellTextPaint)
            canvas.drawText("+${stat.income} دج", 350f, rowTop + 14f, cellTextPaint)
            canvas.drawText("-${stat.expense} دج", 220f, rowTop + 14f, cellTextPaint)

            netValPaint.color = if (stat.net >= 0) primaryGreen else Color.rgb(198, 40, 40)
            val prefix = if (stat.net > 0) "+" else ""
            canvas.drawText("$prefix${stat.net} دج", 45f, rowTop + 14f, netValPaint)

            currentY += 20f
        }

        // Total row
        val totalRowPaint = Paint().apply { color = Color.rgb(245, 245, 245); style = Paint.Style.FILL }
        canvas.drawRect(40f, currentY, 555f, currentY + 22f, totalRowPaint)
        canvas.drawRect(40f, currentY, 555f, currentY + 22f, thBorderPaint)

        val totalThPaint = Paint().apply { color = primaryGreen; textSize = 9f; typeface = boldCairo; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
        canvas.drawText("المجموع السنوي:", 550f, currentY + 15f, totalThPaint)
        canvas.drawText("+$totalIncome دج", 350f, currentY + 15f, totalThPaint)
        canvas.drawText("-$totalExpenses دج", 220f, currentY + 15f, totalThPaint)
        val finalNetPaint = Paint().apply {
            color = if (netBalance >= 0) primaryGreen else Color.rgb(198, 40, 40)
            textSize = 9.5f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        val netPrefix = if (netBalance > 0) "+" else ""
        canvas.drawText("$netPrefix$netBalance دج", 45f, currentY + 15f, finalNetPaint)
        currentY += 22f // Bottom of the total row

        // Top 3 Expense Categories
        if (topCategories.isNotEmpty()) {
            currentY += 35f // Clear gap after table

            // Draw a separator line
            val separatorPaint = Paint().apply {
                color = Color.rgb(220, 228, 220)
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawLine(40f, currentY, 555f, currentY, separatorPaint)

            currentY += 20f // Space after separator line

            canvas.drawText("أعلى أبواب المصاريف في هذا العام:", 555f, currentY, sectionTitlePaint)
            currentY += 18f

            for ((cat, amount) in topCategories) {
                val percent = if (totalExpenses > 0) (amount.toFloat() / totalExpenses * 100f).toInt() else 0
                val catText = "• ${cat.arabicLabel}: $amount دج ($percent% من إجمالي المصاريف)"
                cellTextPaint.color = Color.rgb(70, 70, 70)
                cellTextPaint.textSize = 9f
                cellTextPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(catText, 545f, currentY, cellTextPaint)
                currentY += 18f
            }
        }

        // Footer
        canvas.drawLine(40f, 800f, 555f, 800f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerDate = SimpleDateFormat("yyyy/MM/dd HH:mm", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        val footerPaint = Paint().apply { color = Color.GRAY; textSize = 9f; typeface = cairoTypeface; isAntiAlias = true }
        footerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("التقرير السنوي الشامل — سرب النُّجُب — فوج محمد رحال", 555f, 815f, footerPaint)
        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("تم الإنشاء: $footerDate", 40f, 815f, footerPaint)
    }

    private fun drawScoutStatementPage(
        canvas: Canvas,
        scout: Scout,
        subscriptions: List<FinanceSubscription>,
        contributions: List<FinanceContribution>,
        totalRequired: Int,
        totalPaid: Int,
        totalDebt: Int,
        cairoTypeface: Typeface,
        boldCairo: Typeface,
        context: Context
    ) {
        val primaryGreen = Color.rgb(46, 125, 50)
        val gold = Color.rgb(200, 169, 81)

        val headerPaint = Paint().apply {
            color = primaryGreen
            textSize = 15f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint().apply {
            color = gold
            textSize = 11f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = gold
            strokeWidth = 2f
            isAntiAlias = true
        }

        drawLogo(canvas, context, left = 40f, top = 25f, targetWidth = 50f, targetHeight = 50f)
        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 555f, 45f, headerPaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 63f, subtitlePaint)
        canvas.drawLine(40f, 85f, 555f, 85f, linePaint)

        var currentY = 110f

        val titlePaint = Paint().apply {
            color = primaryGreen
            textSize = 16f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("كشف حساب الشبل: ${scout.fullName}", 297f, currentY, titlePaint)
        currentY += 18f

        // Scout Info Box
        val infoBoxPaint = Paint().apply { color = Color.rgb(248, 249, 248); style = Paint.Style.FILL }
        val infoBorderPaint = Paint().apply { color = Color.rgb(220, 226, 220); style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRoundRect(40f, currentY, 555f, currentY + 45f, 8f, 8f, infoBoxPaint)
        canvas.drawRoundRect(40f, currentY, 555f, currentY + 45f, 8f, 8f, infoBorderPaint)

        val infoTextPaint = Paint().apply {
            color = Color.rgb(60, 60, 60)
            textSize = 9.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val ageStr = if (scout.age > 0) "${scout.age} سنة" else "-"
        canvas.drawText("اسم الشبل: ${scout.fullName}   |   العمر: $ageStr", 545f, currentY + 20f, infoTextPaint)
        val guardianInfo = if (scout.guardianName.isNotBlank()) "${scout.guardianName} (${scout.guardianPhone})" else "غير محدد"
        canvas.drawText("الولي: $guardianInfo", 545f, currentY + 36f, infoTextPaint)

        currentY += 55f

        // Summary Mini-Card (Required, Paid, Debt)
        val cardWidth = 165f
        val cardHeight = 48f
        val gap = 10f
        val cardBgPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
        val cardBorderPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }
        val cardTitlePaint = Paint().apply {
            color = Color.rgb(80, 80, 80)
            textSize = 8.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val cardValPaint = Paint().apply {
            textSize = 11f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Required
        val c1Left = 555f - cardWidth
        cardBgPaint.color = Color.rgb(245, 245, 250)
        cardBorderPaint.color = Color.rgb(215, 215, 230)
        canvas.drawRoundRect(c1Left, currentY, 555f, currentY + cardHeight, 6f, 6f, cardBgPaint)
        canvas.drawRoundRect(c1Left, currentY, 555f, currentY + cardHeight, 6f, 6f, cardBorderPaint)
        canvas.drawText("إجمالي المطلوب", c1Left + cardWidth / 2f, currentY + 18f, cardTitlePaint)
        cardValPaint.color = Color.rgb(50, 50, 50)
        canvas.drawText("$totalRequired دج", c1Left + cardWidth / 2f, currentY + 36f, cardValPaint)

        // Paid
        val c2Left = c1Left - gap - cardWidth
        val c2Right = c1Left - gap
        cardBgPaint.color = Color.rgb(243, 248, 243)
        cardBorderPaint.color = Color.rgb(200, 230, 200)
        canvas.drawRoundRect(c2Left, currentY, c2Right, currentY + cardHeight, 6f, 6f, cardBgPaint)
        canvas.drawRoundRect(c2Left, currentY, c2Right, currentY + cardHeight, 6f, 6f, cardBorderPaint)
        canvas.drawText("إجمالي المدفوع", c2Left + cardWidth / 2f, currentY + 18f, cardTitlePaint)
        cardValPaint.color = primaryGreen
        canvas.drawText("$totalPaid دج", c2Left + cardWidth / 2f, currentY + 36f, cardValPaint)

        // Debt
        val c3Left = 40f
        val c3Right = 40f + cardWidth
        cardBgPaint.color = Color.rgb(255, 245, 245)
        cardBorderPaint.color = Color.rgb(245, 200, 200)
        canvas.drawRoundRect(c3Left, currentY, c3Right, currentY + cardHeight, 6f, 6f, cardBgPaint)
        canvas.drawRoundRect(c3Left, currentY, c3Right, currentY + cardHeight, 6f, 6f, cardBorderPaint)
        canvas.drawText("الديون المتبقية", c3Left + cardWidth / 2f, currentY + 18f, cardTitlePaint)
        cardValPaint.color = Color.rgb(198, 40, 40)
        canvas.drawText("$totalDebt دج", c3Left + cardWidth / 2f, currentY + 36f, cardValPaint)

        currentY += cardHeight + 18f

        val sectionTitlePaint = Paint().apply {
            color = primaryGreen
            textSize = 10.5f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        // Section 1: Subscriptions
        canvas.drawText("اشتراكات الاجتماعات التدريبية:", 555f, currentY, sectionTitlePaint)
        currentY += 8f

        val thBgPaint = Paint().apply { color = Color.rgb(238, 243, 238); style = Paint.Style.FILL }
        val thBorderPaint = Paint().apply { color = Color.rgb(200, 210, 200); style = Paint.Style.STROKE; strokeWidth = 1f }
        val thTextPaint = Paint().apply {
            color = primaryGreen
            textSize = 8.5f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawRect(40f, currentY, 555f, currentY + 20f, thBgPaint)
        canvas.drawRect(40f, currentY, 555f, currentY + 20f, thBorderPaint)
        canvas.drawText("النشاط", 550f, currentY + 14f, thTextPaint)
        canvas.drawText("التاريخ", 360f, currentY + 14f, thTextPaint)
        canvas.drawText("المطلوب", 260f, currentY + 14f, thTextPaint)
        canvas.drawText("المدفوع", 160f, currentY + 14f, thTextPaint)
        thTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("المتبقي", 45f, currentY + 14f, thTextPaint)
        currentY += 20f

        val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        val cellTextPaint = Paint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = 8.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val rowBgPaint = Paint().apply { color = Color.rgb(252, 252, 252); style = Paint.Style.FILL }
        val rowBorderPaint = Paint().apply { color = Color.rgb(235, 238, 235); style = Paint.Style.STROKE; strokeWidth = 0.5f }

        if (subscriptions.isEmpty()) {
            val emptyPaint = Paint().apply { color = Color.GRAY; textSize = 9f; typeface = cairoTypeface; textAlign = Paint.Align.CENTER }
            canvas.drawText("لا توجد اشتراكات مسجلة لهذا الشبل", 297f, currentY + 20f, emptyPaint)
            currentY += 32f
        } else {
            val subsToShow = subscriptions.take(7)
            for ((i, sub) in subsToShow.withIndex()) {
                val rowTop = currentY
                val rowBottom = currentY + 18f
                if (i % 2 == 1) canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBgPaint)
                canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBorderPaint)

                cellTextPaint.textAlign = Paint.Align.RIGHT
                val titleTrunc = if (sub.activityTitle.length > 24) sub.activityTitle.take(23) + "…" else sub.activityTitle
                canvas.drawText(titleTrunc, 550f, rowTop + 13f, cellTextPaint)
                val dStr = if (sub.activityDate > 0) dateFmt.format(Date(sub.activityDate)) else "-"
                canvas.drawText(dStr, 360f, rowTop + 13f, cellTextPaint)
                canvas.drawText("${sub.requiredAmount} دج", 260f, rowTop + 13f, cellTextPaint)
                canvas.drawText("${sub.paidAmount} دج", 160f, rowTop + 13f, cellTextPaint)

                val debtPaint = Paint().apply {
                    color = if (sub.debtAmount > 0) Color.rgb(198, 40, 40) else primaryGreen
                    textSize = 8.5f
                    typeface = boldCairo
                    isAntiAlias = true
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText("${sub.debtAmount} دج", 45f, rowTop + 13f, debtPaint)
                currentY += 18f
            }
        }

        currentY += 14f

        // Section 2: Contributions
        canvas.drawText("مساهمات الأنشطة الكبرى والمخيمات:", 555f, currentY, sectionTitlePaint)
        currentY += 8f

        thTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawRect(40f, currentY, 555f, currentY + 20f, thBgPaint)
        canvas.drawRect(40f, currentY, 555f, currentY + 20f, thBorderPaint)
        canvas.drawText("النشاط", 550f, currentY + 14f, thTextPaint)
        canvas.drawText("التاريخ", 360f, currentY + 14f, thTextPaint)
        canvas.drawText("المطلوب", 260f, currentY + 14f, thTextPaint)
        canvas.drawText("المدفوع", 160f, currentY + 14f, thTextPaint)
        thTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("المتبقي", 45f, currentY + 14f, thTextPaint)
        currentY += 20f

        if (contributions.isEmpty()) {
            val emptyPaint = Paint().apply { color = Color.GRAY; textSize = 9f; typeface = cairoTypeface; textAlign = Paint.Align.CENTER }
            canvas.drawText("لا توجد مساهمات مسجلة لهذا الشبل", 297f, currentY + 20f, emptyPaint)
        } else {
            val contribsToShow = contributions.take(7)
            for ((i, con) in contribsToShow.withIndex()) {
                val rowTop = currentY
                val rowBottom = currentY + 18f
                if (i % 2 == 1) canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBgPaint)
                canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBorderPaint)

                cellTextPaint.textAlign = Paint.Align.RIGHT
                val titleTrunc = if (con.activityTitle.length > 24) con.activityTitle.take(23) + "…" else con.activityTitle
                canvas.drawText(titleTrunc, 550f, rowTop + 13f, cellTextPaint)
                val dStr = if (con.activityDate > 0) dateFmt.format(Date(con.activityDate)) else "-"
                canvas.drawText(dStr, 360f, rowTop + 13f, cellTextPaint)
                canvas.drawText("${con.requiredAmount} دج", 260f, rowTop + 13f, cellTextPaint)
                canvas.drawText("${con.paidAmount} دج", 160f, rowTop + 13f, cellTextPaint)

                val debtPaint = Paint().apply {
                    color = if (con.debtAmount > 0) Color.rgb(198, 40, 40) else primaryGreen
                    textSize = 8.5f
                    typeface = boldCairo
                    isAntiAlias = true
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText("${con.debtAmount} دج", 45f, rowTop + 13f, debtPaint)
                currentY += 18f
            }
        }

        // Footer
        canvas.drawLine(40f, 800f, 555f, 800f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerDate = SimpleDateFormat("yyyy/MM/dd HH:mm", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        val footerPaint = Paint().apply { color = Color.GRAY; textSize = 9f; typeface = cairoTypeface; isAntiAlias = true }
        footerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("كشف حساب مالي فردي — سرب النُّجُب — فوج محمد رحال", 555f, 815f, footerPaint)
        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("تم الإنشاء: $footerDate", 40f, 815f, footerPaint)
    }


    private data class CategoryExpenseSummary(
        val category: ExpenseCategory,
        val totalAmount: Int,
        val transactionCount: Int,
        val percentage: Float
    )

    private fun drawMonthlyStatementPage(
        canvas: Canvas,
        month: Int,
        year: Int,
        pageRows: List<FinanceTransaction>,
        pageIndex: Int,
        totalPages: Int,
        isFirstPage: Boolean,
        openingBalance: Int,
        totalIncome: Int,
        totalExpenses: Int,
        closingBalance: Int,
        cairoTypeface: Typeface,
        context: Context
    ) {
        val monthName = ARABIC_MONTHS.getOrElse(month - 1) { "شهر $month" }
        val primaryGreen = Color.rgb(46, 125, 50)
        val gold = Color.rgb(200, 169, 81)

        val boldCairo = Typeface.create(cairoTypeface, Typeface.BOLD)

        val headerPaint = Paint().apply {
            color = primaryGreen
            textSize = 15f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val subtitlePaint = Paint().apply {
            color = gold
            textSize = 11f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val linePaint = Paint().apply {
            color = gold
            strokeWidth = 2f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 9.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        // Header
        drawLogo(canvas, context, left = 40f, top = 25f, targetWidth = 50f, targetHeight = 50f)

        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 555f, 45f, headerPaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 63f, subtitlePaint)
        canvas.drawLine(40f, 85f, 555f, 85f, linePaint)

        var currentY = 110f

        if (isFirstPage) {
            // Document Title
            val titlePaint = Paint().apply {
                color = primaryGreen
                textSize = 16f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("كشف حساب لشهر $monthName $year", 297f, currentY, titlePaint)
            currentY += 20f

            // Summary Card (2x2 Box)
            val summaryBoxPaint = Paint().apply {
                color = Color.rgb(248, 249, 248)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val summaryBorderPaint = Paint().apply {
                color = Color.rgb(220, 226, 220)
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawRoundRect(40f, currentY, 555f, currentY + 70f, 10f, 10f, summaryBoxPaint)
            canvas.drawRoundRect(40f, currentY, 555f, currentY + 70f, 10f, 10f, summaryBorderPaint)

            // Divider in summary box
            canvas.drawLine(297f, currentY + 5f, 297f, currentY + 65f, summaryBorderPaint)
            canvas.drawLine(40f, currentY + 35f, 555f, currentY + 35f, summaryBorderPaint)

            val summaryLabelPaint = Paint().apply {
                color = Color.rgb(100, 100, 100)
                textSize = 9.5f
                typeface = cairoTypeface
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            val summaryValuePaint = Paint().apply {
                color = primaryGreen
                textSize = 11f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }

            // Cell 1: الرصيد الافتتاحي (top right)
            canvas.drawText("الرصيد الافتتاحي:", 545f, currentY + 22f, summaryLabelPaint)
            summaryValuePaint.color = Color.rgb(25, 118, 210)
            canvas.drawText("$openingBalance دج", 310f, currentY + 22f, summaryValuePaint)

            // Cell 2: إجمالي الواردات (top left)
            canvas.drawText("إجمالي الواردات:", 287f, currentY + 22f, summaryLabelPaint)
            summaryValuePaint.color = Color.rgb(46, 125, 50)
            canvas.drawText("+$totalIncome دج", 55f, currentY + 22f, summaryValuePaint)

            // Cell 3: إجمالي المصاريف (bottom right)
            canvas.drawText("إجمالي المصاريف:", 545f, currentY + 56f, summaryLabelPaint)
            summaryValuePaint.color = Color.rgb(198, 40, 40)
            canvas.drawText("-$totalExpenses دج", 310f, currentY + 56f, summaryValuePaint)

            // Cell 4: الرصيد النهائي (bottom left)
            canvas.drawText("الرصيد النهائي:", 287f, currentY + 56f, summaryLabelPaint)
            summaryValuePaint.color = if (closingBalance >= 0) primaryGreen else Color.rgb(198, 40, 40)
            canvas.drawText("$closingBalance دج", 55f, currentY + 56f, summaryValuePaint)

            currentY += 85f
        }

        // Table Header
        val thBgPaint = Paint().apply {
            color = Color.rgb(238, 243, 238)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val thBorderPaint = Paint().apply {
            color = Color.rgb(200, 210, 200)
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        val thTextPaint = Paint().apply {
            color = primaryGreen
            textSize = 9.5f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawRect(40f, currentY, 555f, currentY + 24f, thBgPaint)
        canvas.drawRect(40f, currentY, 555f, currentY + 24f, thBorderPaint)

        canvas.drawText("التاريخ", 550f, currentY + 16f, thTextPaint)
        canvas.drawText("النوع", 475f, currentY + 16f, thTextPaint)
        canvas.drawText("التصنيف / المصدر", 415f, currentY + 16f, thTextPaint)
        canvas.drawText("الوصف", 310f, currentY + 16f, thTextPaint)
        thTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("المبلغ", 45f, currentY + 16f, thTextPaint)

        currentY += 24f

        if (pageRows.isEmpty() && isFirstPage) {
            val emptyPaint = Paint().apply {
                color = Color.GRAY
                textSize = 10f
                typeface = cairoTypeface
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("لا توجد معاملات مسجلة في هذا الشهر", 297f, currentY + 40f, emptyPaint)
        } else {
            val rowDateFmt = SimpleDateFormat("MM/dd", Locale.US)
            val rowBgPaint = Paint().apply {
                color = Color.rgb(252, 252, 252)
                style = Paint.Style.FILL
            }
            val rowBorderPaint = Paint().apply {
                color = Color.rgb(235, 238, 235)
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
            }
            val amountPaint = Paint().apply {
                textSize = 9.5f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }

            for ((i, tx) in pageRows.withIndex()) {
                val rowTop = currentY
                val rowBottom = currentY + 24f

                if (i % 2 == 1) {
                    canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBgPaint)
                }
                canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBorderPaint)

                // Date
                val dStr = if (tx.date > 0) rowDateFmt.format(Date(tx.date)) else "-"
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(dStr, 550f, rowTop + 16f, textPaint)

                // Type
                val typeStr = tx.type.arabicLabel
                canvas.drawText(typeStr, 475f, rowTop + 16f, textPaint)

                // Category or Source
                val catSource = when {
                    !tx.isIncome() -> tx.category?.arabicLabel ?: "مصروف"
                    !tx.source.isNullOrBlank() -> tx.source
                    else -> tx.type.arabicLabel
                }
                val truncatedCat = if (catSource.length > 16) catSource.take(15) + "…" else catSource
                canvas.drawText(truncatedCat, 415f, rowTop + 16f, textPaint)

                // Description
                val desc = if (tx.description.isBlank()) "-" else tx.description
                val truncatedDesc = if (desc.length > 28) desc.take(27) + "…" else desc
                canvas.drawText(truncatedDesc, 310f, rowTop + 16f, textPaint)

                // Amount
                if (tx.isIncome()) {
                    amountPaint.color = primaryGreen
                    canvas.drawText("+${tx.amount} دج", 45f, rowTop + 16f, amountPaint)
                } else {
                    amountPaint.color = Color.rgb(198, 40, 40)
                    canvas.drawText("-${tx.amount} دج", 45f, rowTop + 16f, amountPaint)
                }

                currentY += 24f
            }
        }

        // Footer
        canvas.drawLine(40f, 800f, 555f, 800f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerDate = SimpleDateFormat("yyyy/MM/dd HH:mm", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("تم الإنشاء في $footerDate", 555f, 818f, footerPaint)
        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("صفحة $pageIndex من $totalPages", 40f, 818f, footerPaint)
    }

    private fun drawExpensesByCategoryPage(
        canvas: Canvas,
        categoryGroups: List<CategoryExpenseSummary>,
        totalExpenses: Int,
        totalCount: Int,
        minDate: Long?,
        maxDate: Long?,
        cairoTypeface: Typeface,
        boldCairo: Typeface,
        context: Context
    ) {
        val primaryGreen = Color.rgb(46, 125, 50)
        val gold = Color.rgb(200, 169, 81)
        val errorRed = Color.rgb(211, 47, 47) // Solid Error Red

        val headerPaint = Paint().apply {
            color = primaryGreen
            textSize = 15f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val subtitlePaint = Paint().apply {
            color = gold
            textSize = 11f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val linePaint = Paint().apply {
            color = gold
            strokeWidth = 2f
            isAntiAlias = true
        }

        // Header with high quality logo
        drawLogo(canvas, context, left = 40f, top = 25f, targetWidth = 50f, targetHeight = 50f)

        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 555f, 45f, headerPaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 63f, subtitlePaint)
        canvas.drawLine(40f, 85f, 555f, 85f, linePaint)

        var currentY = 115f

        // Title
        val titlePaint = Paint().apply {
            color = primaryGreen
            textSize = 16f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("تقرير المصاريف حسب التصنيف", 297f, currentY, titlePaint)
        currentY += 24f

        // FIX 3: Top Summary Card with full context (Total, Count, Period)
        val cardPaint = Paint().apply {
            color = Color.rgb(254, 245, 245)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardBorder = Paint().apply {
            color = Color.rgb(240, 210, 210)
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawRoundRect(40f, currentY, 555f, currentY + 62f, 10f, 10f, cardPaint)
        canvas.drawRoundRect(40f, currentY, 555f, currentY + 62f, 10f, 10f, cardBorder)

        val dateFmt = SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ)
        val periodStr = if (minDate != null && maxDate != null && minDate > 0 && maxDate > 0) {
            val d1 = dateFmt.format(Date(minDate))
            val d2 = dateFmt.format(Date(maxDate))
            if (d1 == d2) d1 else "من $d1 إلى $d2"
        } else {
            "جميع الفترات المسجلة"
        }

        val cardTitlePaint = Paint().apply {
            color = errorRed
            textSize = 11.5f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val cardValPaint = Paint().apply {
            color = Color.rgb(50, 50, 50)
            textSize = 10.5f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        val cardSubPaint = Paint().apply {
            color = Color.rgb(100, 100, 100)
            textSize = 9.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        // Line 1: إجمالي المصاريف + عدد العمليات
        canvas.drawText("إجمالي المصاريف المسجلة: $totalExpenses دج", 545f, currentY + 24f, cardTitlePaint)
        canvas.drawText("عدد العمليات: $totalCount", 55f, currentY + 24f, cardValPaint)

        // Line 2: الفترة الزمنية
        canvas.drawText("الفترة: $periodStr", 545f, currentY + 48f, cardSubPaint)

        currentY += 80f

        if (categoryGroups.isEmpty()) {
            val emptyPaint = Paint().apply {
                color = Color.GRAY
                textSize = 12f
                typeface = cairoTypeface
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("لا توجد مصاريف مسجلة حتى الآن", 297f, currentY + 60f, emptyPaint)
        } else {
            val catTitlePaint = Paint().apply {
                color = Color.rgb(33, 33, 33)
                textSize = 11f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            val catAmountPaint = Paint().apply {
                color = errorRed
                textSize = 11f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }
            val barBgPaint = Paint().apply {
                color = Color.rgb(238, 238, 238)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val barFillPaint = Paint().apply {
                color = errorRed
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val pctPaint = Paint().apply {
                color = errorRed
                textSize = 10f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }

            val barTrackLeft = 105f
            val barTrackRight = 555f
            val barWidthTotal = barTrackRight - barTrackLeft // 450f
            val barHeight = 8.5f

            for (item in categoryGroups) {
                // Line 1: Category Title & Count (Right), Amount (Left)
                val catText = "${item.category.arabicLabel} (${item.transactionCount} عمليات)"
                canvas.drawText(catText, 555f, currentY + 14f, catTitlePaint)

                val amountText = "${item.totalAmount} دج"
                canvas.drawText(amountText, 40f, currentY + 14f, catAmountPaint)

                currentY += 20f

                // Line 2: Percentage clearly drawn next to the bar (Left: 40f..100f) + Solid Progress Bar (105f..555f)
                val pctFormatted = String.format(Locale.US, "%.1f", item.percentage) + "%"
                canvas.drawText(pctFormatted, 40f, currentY + barHeight - 0.5f, pctPaint)

                // Progress Bar Track
                canvas.drawRoundRect(barTrackLeft, currentY, barTrackRight, currentY + barHeight, 4.5f, 4.5f, barBgPaint)
                val fillWidth = (barWidthTotal * (item.percentage / 100f)).coerceAtLeast(4f)
                // Solid Color Error Fill
                canvas.drawRoundRect(barTrackLeft, currentY, barTrackLeft + fillWidth, currentY + barHeight, 4.5f, 4.5f, barFillPaint)

                currentY += 28f
            }

            // Total bottom divider & summary
            currentY += 8f
            canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
            currentY += 25f

            val totalBottomPaint = Paint().apply {
                color = primaryGreen
                textSize = 12f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            val totalBottomValPaint = Paint().apply {
                color = errorRed
                textSize = 13.5f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("الإجمالي العام للمصاريف:", 555f, currentY, totalBottomPaint)
            canvas.drawText("$totalExpenses دج", 40f, currentY, totalBottomValPaint)
        }

        // Footer
        canvas.drawLine(40f, 800f, 555f, 800f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerDate = SimpleDateFormat("yyyy/MM/dd HH:mm", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("تم الإنشاء في $footerDate", 555f, 818f, footerPaint)
        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("تقرير مالي صادر عن تطبيق سرب النُّجُب", 40f, 818f, footerPaint)
    }

    private fun drawActivityAttendancePage(
        canvas: Canvas,
        activity: Activity,
        pageRows: List<AttendanceRow>,
        startRowIndex: Int,
        trainingMeetingNumber: Int?,
        pageNumber: Int,
        totalPages: Int,
        totalScouts: Int,
        cairoTypeface: Typeface?,
        boldCairo: Typeface?,
        context: Context
    ) {
        val headerPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 18f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(200, 169, 81)
            textSize = 12f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val linePaint = Paint().apply {
            color = Color.rgb(200, 169, 81)
            strokeWidth = 2f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 11f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val centerTextPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 11f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val tableHeaderPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 11f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // رسم الشعار
        drawLogo(canvas, context, left = 40f, top = 25f, targetWidth = 50f, targetHeight = 50f)

        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 555f, 45f, headerPaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 65f, subtitlePaint)
        canvas.drawLine(40f, 85f, 555f, 85f, linePaint)

        // عنوان النشاط
        val titleText = if (trainingMeetingNumber != null && trainingMeetingNumber > 0) {
            "قائمة الحضور — الاجتماع التدريبي رقم $trainingMeetingNumber"
        } else {
            "قائمة الحضور — ${activity.title} (${activity.activityType.arabicLabel})"
        }
        val docTitlePaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 15f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(titleText, 297f, 115f, docTitlePaint)

        val dateStr = if (activity.startDate > 0) dateFormatter.format(Date(activity.startDate)) else ""
        val infoText = "التاريخ: $dateStr" + if (activity.location.isNotBlank()) " | المكان: ${activity.location}" else ""
        val infoPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(infoText, 297f, 135f, infoPaint)

        // جدول الحضور (RTL)
        var yPos = 160f
        val tableHeaderBg = Paint().apply {
            color = Color.rgb(232, 245, 233)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(40f, yPos, 555f, yPos + 24f, 6f, 6f, tableHeaderBg)

        canvas.drawText("#", 535f, yPos + 16f, tableHeaderPaint)
        canvas.drawText("الاسم واللقب", 410f, yPos + 16f, Paint(tableHeaderPaint).apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText("الحالة", 185f, yPos + 16f, tableHeaderPaint)
        canvas.drawText("النقاط", 80f, yPos + 16f, tableHeaderPaint)

        yPos += 26f

        val rowBgPaint = Paint().apply {
            color = Color.rgb(249, 249, 249)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            color = Color.rgb(220, 220, 220)
            strokeWidth = 0.8f
        }

        pageRows.forEachIndexed { i, row ->
            val rowIndex = startRowIndex + i + 1
            if (i % 2 == 1) {
                canvas.drawRect(40f, yPos, 555f, yPos + 22f, rowBgPaint)
            }

            canvas.drawText("$rowIndex", 535f, yPos + 15f, centerTextPaint)
            canvas.drawText(row.scout.fullName, 510f, yPos + 15f, textPaint)

            val statusText = row.attendance?.attendanceStatus?.arabicLabel ?: "لم يُسجَّل"
            val statusColor = when (row.attendance?.attendanceStatus) {
                AttendanceStatus.PRESENT -> Color.rgb(46, 125, 50)
                AttendanceStatus.LATE -> Color.rgb(245, 124, 0)
                AttendanceStatus.VERY_LATE -> Color.rgb(230, 74, 25)
                AttendanceStatus.ABSENT_EXCUSED -> Color.rgb(230, 81, 0)
                AttendanceStatus.ABSENT_UNEXCUSED -> Color.RED
                null -> Color.GRAY
            }
            val statusPaint = Paint(centerTextPaint).apply {
                color = statusColor
                typeface = boldCairo
                isFakeBoldText = true
            }
            canvas.drawText(statusText, 185f, yPos + 15f, statusPaint)

            val pointsStr = if (activity.activityType.usesPointsSystem()) {
                if (row.attendance != null) "${row.attendance.points}" else "—"
            } else {
                "—"
            }
            canvas.drawText(pointsStr, 80f, yPos + 15f, centerTextPaint)

            canvas.drawLine(40f, yPos + 22f, 555f, yPos + 22f, dividerPaint)
            yPos += 22f
        }

        // تذييل الصفحة
        canvas.drawLine(40f, 790f, 555f, 790f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val nowStr = dateFormatter.format(Date())
        canvas.drawText("تاريخ الاستخراج: $nowStr", 555f, 808f, footerPaint)
        canvas.drawText("صفحة $pageNumber من $totalPages", 60f, 808f, Paint(footerPaint).apply { textAlign = Paint.Align.LEFT })
    }

    private fun drawActivityReportPage1(
        canvas: Canvas,
        activity: Activity,
        meetingTopics: List<String>,
        cairoTypeface: Typeface?,
        boldCairo: Typeface?,
        totalPages: Int,
        context: Context
    ) {
        val headerPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 18f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(200, 169, 81)
            textSize = 12f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val linePaint = Paint().apply {
            color = Color.rgb(200, 169, 81)
            strokeWidth = 2f
            isAntiAlias = true
        }

        val docTitlePaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 16f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val activityTitlePaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 12f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val cardBgPaint = Paint().apply {
            color = Color.rgb(247, 249, 247)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val cardBorderPaint = Paint().apply {
            color = Color.rgb(215, 230, 215)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            isAntiAlias = true
        }

        val separatorPaint = Paint().apply {
            color = Color.rgb(220, 230, 220)
            strokeWidth = 0.8f
            isAntiAlias = true
        }

        val sectionHeaderPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 11f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val labelBoldPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 9.5f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val valuePaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 9.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        // رسم الشعار والترويسة
        drawLogo(canvas, context, left = 40f, top = 25f, targetWidth = 50f, targetHeight = 50f)
        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 555f, 45f, headerPaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 65f, subtitlePaint)
        canvas.drawLine(40f, 85f, 555f, 85f, linePaint)

        // عنوان التقرير
        canvas.drawText("تقرير النشاط الكشفي", 297.5f, 110f, docTitlePaint)
        canvas.drawText("«${activity.title}»", 297.5f, 126f, activityTitlePaint)

        var currentY = 136f

        // --- القسم 1: معلومات النشاط الأساسية ---
        val dateFmt = SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ)
        val startDateStr = if (activity.startDate > 0) dateFmt.format(Date(activity.startDate)) else "—"
        val endDateStr = if (activity.endDate > 0) dateFmt.format(Date(activity.endDate)) else "—"
        val dateDisplay = if (activity.isMultiDay()) "$startDateStr إلى $endDateStr" else startDateStr
        val timeDisplay = if (activity.startTime.isNotBlank() || activity.endTime.isNotBlank()) {
            "${activity.startTime.ifBlank { "—" }} - ${activity.endTime.ifBlank { "—" }}"
        } else {
            "خلال اليوم"
        }
        val locDisplay = activity.location.ifBlank { "المقر الكشفي" }
        val respDisplay = activity.responsibleName.ifBlank { "قيادة السرب" }

        val sec1Height = 98f
        canvas.drawRoundRect(40f, currentY, 555f, currentY + sec1Height, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(40f, currentY, 555f, currentY + sec1Height, 8f, 8f, cardBorderPaint)

        canvas.drawText("معلومات النشاط الأساسية", 540f, currentY + 16f, sectionHeaderPaint)
        canvas.drawLine(40f, currentY + 22f, 555f, currentY + 22f, separatorPaint)

        canvas.drawText("نوع النشاط: ", 540f, currentY + 38f, labelBoldPaint)
        canvas.drawText(activity.activityType.arabicLabel, 470f, currentY + 38f, valuePaint)
        canvas.drawText("تاريخ النشاط: ", 270f, currentY + 38f, labelBoldPaint)
        canvas.drawText(dateDisplay, 200f, currentY + 38f, valuePaint)

        canvas.drawText("التوقيت: ", 540f, currentY + 54f, labelBoldPaint)
        canvas.drawText(timeDisplay, 485f, currentY + 54f, valuePaint)
        canvas.drawText("المكان: ", 270f, currentY + 54f, labelBoldPaint)
        canvas.drawText(locDisplay, 225f, currentY + 54f, valuePaint)

        canvas.drawText("القائد المسؤول: ", 540f, currentY + 70f, labelBoldPaint)
        canvas.drawText(respDisplay, 450f, currentY + 70f, valuePaint)
        canvas.drawText("حالة النشاط: ", 270f, currentY + 70f, labelBoldPaint)
        canvas.drawText(activity.activityStatus.arabicLabel, 205f, currentY + 70f, valuePaint)

        val contribText = if (activity.contributionRequired) "${activity.contributionDefaultAmount ?: 0} دج" else "بدون مساهمة"
        canvas.drawText("مدة النشاط: ", 540f, currentY + 86f, labelBoldPaint)
        canvas.drawText("${activity.durationDays()} يوم", 475f, currentY + 86f, valuePaint)
        canvas.drawText("المساهمة المالية: ", 270f, currentY + 86f, labelBoldPaint)
        canvas.drawText(contribText, 180f, currentY + 86f, valuePaint)

        currentY += sec1Height + 10f

        // --- القسم 2: الأهداف التربوية والأدوات واللوازم ---
        val objText = activity.objectives.ifBlank { "لم تُحدد أهداف تربوية خاصة لهذا النشاط." }
        val objLines = wrapText(objText, valuePaint, 490f).take(3)
        val toolsText = activity.tools.ifBlank { "الأدوات الكشفية الاعتيادية." }
        val toolLines = wrapText(toolsText, valuePaint, 490f).take(2)

        val sec2Height = 30f + 14f + objLines.size * 13f + 14f + toolLines.size * 13f + 6f
        canvas.drawRoundRect(40f, currentY, 555f, currentY + sec2Height, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(40f, currentY, 555f, currentY + sec2Height, 8f, 8f, cardBorderPaint)

        canvas.drawText("الأهداف التربوية والأدوات واللوازم", 540f, currentY + 16f, sectionHeaderPaint)
        canvas.drawLine(40f, currentY + 22f, 555f, currentY + 22f, separatorPaint)

        var yInSec2 = currentY + 36f
        canvas.drawText("الأهداف التربوية:", 540f, yInSec2, labelBoldPaint)
        yInSec2 += 13f
        objLines.forEach { line ->
            canvas.drawText(line, 535f, yInSec2, valuePaint)
            yInSec2 += 13f
        }

        yInSec2 += 4f
        canvas.drawText("الأدوات واللوازم:", 540f, yInSec2, labelBoldPaint)
        yInSec2 += 13f
        toolLines.forEach { line ->
            canvas.drawText(line, 535f, yInSec2, valuePaint)
            yInSec2 += 13f
        }

        currentY += sec2Height + 10f

        // --- القسم 3: مواضيع الاجتماع التدريبي (إن وجدت) ---
        if (meetingTopics.isNotEmpty()) {
            val topicsToShow = meetingTopics.take(4)
            val sec3Height = 28f + topicsToShow.size * 15f + 6f
            canvas.drawRoundRect(40f, currentY, 555f, currentY + sec3Height, 8f, 8f, cardBgPaint)
            canvas.drawRoundRect(40f, currentY, 555f, currentY + sec3Height, 8f, 8f, cardBorderPaint)

            canvas.drawText("محاور ومواضيع الاجتماع التدريبي", 540f, currentY + 16f, sectionHeaderPaint)
            canvas.drawLine(40f, currentY + 22f, 555f, currentY + 22f, separatorPaint)

            topicsToShow.forEachIndexed { idx, topic ->
                canvas.drawText("${idx + 1}. $topic", 535f, currentY + 36f + idx * 15f, valuePaint)
            }
            currentY += sec3Height + 10f
        }

        // --- القسم 4: التقرير الأدبي وخلاصة النشاط ---
        val remainingForSec4 = maxOf(70f, 785f - currentY)
        val reportContent = activity.finalReport.ifBlank { "لم يُسجل تقرير أدبي ختامي للنشاط بعد." }
        val maxReportLines = maxOf(2, ((remainingForSec4 - 34f) / 14f).toInt())
        val reportLines = wrapText(reportContent, valuePaint, 490f).take(maxReportLines)
        val sec4Height = remainingForSec4

        canvas.drawRoundRect(40f, currentY, 555f, currentY + sec4Height, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(40f, currentY, 555f, currentY + sec4Height, 8f, 8f, cardBorderPaint)

        canvas.drawText("التقرير الأدبي وخلاصة النشاط", 540f, currentY + 16f, sectionHeaderPaint)
        canvas.drawLine(40f, currentY + 22f, 555f, currentY + 22f, separatorPaint)

        reportLines.forEachIndexed { idx, line ->
            canvas.drawText(line, 535f, currentY + 36f + idx * 14f, valuePaint)
        }

        // تذييل الصفحة 1
        canvas.drawLine(40f, 792f, 555f, 792f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val nowStr = SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        canvas.drawText("تاريخ استخراج الوثيقة: $nowStr", 555f, 808f, footerPaint)
        canvas.drawText("صفحة 1 من $totalPages | سرب النُّجُب المعتمد", 40f, 808f, Paint(footerPaint).apply { textAlign = Paint.Align.LEFT })
    }

    private fun drawActivityReportAttendancePage(
        canvas: Canvas,
        activity: Activity,
        pageItems: List<ActivityReportAttendanceEntry>,
        allItems: List<ActivityReportAttendanceEntry>,
        startRowIndex: Int,
        pageNumber: Int,
        totalPages: Int,
        isFirstAttendancePage: Boolean,
        cairoTypeface: Typeface?,
        boldCairo: Typeface?,
        context: Context
    ) {
        val headerPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 18f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(200, 169, 81)
            textSize = 12f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val linePaint = Paint().apply {
            color = Color.rgb(200, 169, 81)
            strokeWidth = 2f
            isAntiAlias = true
        }

        val docTitlePaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 15f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val textPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 10f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val centerTextPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 10f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val tableHeaderPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 10.5f
            typeface = boldCairo
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // رسم الشعار والترويسة
        drawLogo(canvas, context, left = 40f, top = 25f, targetWidth = 50f, targetHeight = 50f)
        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 555f, 45f, headerPaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 65f, subtitlePaint)
        canvas.drawLine(40f, 85f, 555f, 85f, linePaint)

        // عنوان الملحق
        canvas.drawText("ملحق سجل الحضور والتقييم: ${activity.title}", 297.5f, 112f, docTitlePaint)

        var currentY = 126f

        // --- القسم 5: ملخص الحضور (في الصفحة الأولى فقط من الحضور) ---
        if (isFirstAttendancePage) {
            val summaryCardBg = Paint().apply {
                color = Color.rgb(246, 249, 246)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val summaryCardBorder = Paint().apply {
                color = Color.rgb(215, 230, 215)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
                isAntiAlias = true
            }
            val summaryHeight = 54f
            canvas.drawRoundRect(40f, currentY, 555f, currentY + summaryHeight, 8f, 8f, summaryCardBg)
            canvas.drawRoundRect(40f, currentY, 555f, currentY + summaryHeight, 8f, 8f, summaryCardBorder)

            val summaryTitlePaint = Paint().apply {
                color = Color.rgb(46, 125, 50)
                textSize = 10.5f
                typeface = boldCairo
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("ملخص وإحصائيات الحضور", 540f, currentY + 16f, summaryTitlePaint)
            canvas.drawLine(40f, currentY + 22f, 555f, currentY + 22f, Paint().apply { color = Color.rgb(220, 230, 220); strokeWidth = 0.8f })

            val presentCount = allItems.count { it.status == AttendanceStatus.PRESENT }
            val lateCount = allItems.count { it.status == AttendanceStatus.LATE || it.status == AttendanceStatus.VERY_LATE }
            val excusedCount = allItems.count { it.status == AttendanceStatus.ABSENT_EXCUSED }
            val unexcusedCount = allItems.count { it.status == AttendanceStatus.ABSENT_UNEXCUSED }
            val totalCount = allItems.size

            val statPaint = Paint().apply {
                textSize = 10f
                typeface = boldCairo
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }

            statPaint.color = Color.rgb(46, 125, 50)
            canvas.drawText("حاضر: $presentCount", 540f, currentY + 40f, statPaint)

            statPaint.color = Color.rgb(245, 124, 0)
            canvas.drawText("متأخر: $lateCount", 430f, currentY + 40f, statPaint)

            statPaint.color = Color.rgb(230, 81, 0)
            canvas.drawText("بعذر: $excusedCount", 330f, currentY + 40f, statPaint)

            statPaint.color = Color.RED
            canvas.drawText("بدون عذر: $unexcusedCount", 240f, currentY + 40f, statPaint)

            statPaint.color = Color.rgb(33, 33, 33)
            canvas.drawText("المجموع: $totalCount شبل", 130f, currentY + 40f, statPaint)

            currentY += summaryHeight + 12f
        }

        // --- القسم 6: جدول الحضور المفصل ---
        val tableHeaderBg = Paint().apply {
            color = Color.rgb(232, 245, 233)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(40f, currentY, 555f, currentY + 22f, 6f, 6f, tableHeaderBg)

        canvas.drawText("#", 535f, currentY + 15f, tableHeaderPaint)
        canvas.drawText("الاسم واللقب", 410f, currentY + 15f, Paint(tableHeaderPaint).apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText("الحالة", 185f, currentY + 15f, tableHeaderPaint)
        canvas.drawText("النقاط", 80f, currentY + 15f, tableHeaderPaint)

        currentY += 24f

        if (pageItems.isEmpty()) {
            val emptyPaint = Paint().apply {
                color = Color.GRAY
                textSize = 11f
                typeface = cairoTypeface
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("لم يتم تسجيل حضور لهذا النشاط حتى الآن.", 297.5f, currentY + 30f, emptyPaint)
        } else {
            val rowBgPaint = Paint().apply {
                color = Color.rgb(249, 249, 249)
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val dividerPaint = Paint().apply {
                color = Color.rgb(220, 220, 220)
                strokeWidth = 0.8f
            }

            pageItems.forEachIndexed { i, item ->
                val rowIndex = startRowIndex + i + 1
                if (i % 2 == 1) {
                    canvas.drawRect(40f, currentY, 555f, currentY + 20f, rowBgPaint)
                }

                canvas.drawText("$rowIndex", 535f, currentY + 14f, centerTextPaint)
                canvas.drawText(item.scoutFullName, 510f, currentY + 14f, textPaint)

                val statusText = item.status?.arabicLabel ?: "لم يُسجَّل"
                val statusColor = when (item.status) {
                    AttendanceStatus.PRESENT -> Color.rgb(46, 125, 50)
                    AttendanceStatus.LATE -> Color.rgb(245, 124, 0)
                    AttendanceStatus.VERY_LATE -> Color.rgb(230, 74, 25)
                    AttendanceStatus.ABSENT_EXCUSED -> Color.rgb(230, 81, 0)
                    AttendanceStatus.ABSENT_UNEXCUSED -> Color.RED
                    null -> Color.GRAY
                }
                val statusPaint = Paint(centerTextPaint).apply {
                    color = statusColor
                    typeface = boldCairo
                    isFakeBoldText = true
                }
                canvas.drawText(statusText, 185f, currentY + 14f, statusPaint)

                val pointsStr = if (activity.activityType.usesPointsSystem()) {
                    if (item.points > 0 || item.status != null) "${item.points}" else "—"
                } else {
                    "—"
                }
                canvas.drawText(pointsStr, 80f, currentY + 14f, centerTextPaint)

                canvas.drawLine(40f, currentY + 20f, 555f, currentY + 20f, dividerPaint)
                currentY += 20f
            }
        }

        // تذييل الصفحة
        canvas.drawLine(40f, 792f, 555f, 792f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val nowStr = SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        canvas.drawText("تاريخ استخراج الوثيقة: $nowStr", 555f, 808f, footerPaint)
        canvas.drawText("صفحة $pageNumber من $totalPages | سرب النُّجُب المعتمد", 40f, 808f, Paint(footerPaint).apply { textAlign = Paint.Align.LEFT })
    }

    private fun drawTrainingMeetingsMatrixLandscape(
        canvas: Canvas,
        meetings: List<MeetingWithAttendance>,
        allMeetings: List<MeetingWithAttendance>,
        pageScouts: List<Scout>,
        pageNumber: Int,
        totalPages: Int,
        cairoTypeface: Typeface,
        context: Context
    ) {
        val boldCairo = Typeface.create(cairoTypeface, Typeface.BOLD)

        // 1. الترويسة العلوية الرسمية
        drawLogo(canvas, context, left = 24f, top = 18f, targetWidth = 38f, targetHeight = 38f)

        val headerPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 13f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 818f, 32f, headerPaint)

        val subtitlePaint = Paint().apply {
            color = Color.rgb(200, 169, 81)
            textSize = 10f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("الكشافة الإسلامية الجزائرية", 818f, 46f, subtitlePaint)

        val titlePaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 16f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("سجل الاجتماعات التدريبية", 421f, 36f, titlePaint)

        canvas.drawLine(24f, 56f, 818f, 56f, Paint().apply { color = Color.rgb(200, 169, 81); strokeWidth = 1.2f })

        // 2. أبعاد الجدول
        val tableTop = 64f
        val headerHeight = 38f
        val tableHeaderBottom = tableTop + headerHeight // 102f

        val availableBodyHeight = 555f - tableHeaderBottom
        val rowHeight = minOf(26f, maxOf(18f, availableBodyHeight / pageScouts.size))
        val tableBottom = tableHeaderBottom + pageScouts.size * rowHeight

        val nameColWidth = 140f
        val totalColWidth = 60f
        val meetingCount = meetings.size
        val availableMeetingWidth = 818f - 24f - nameColWidth - totalColWidth // 594f
        val meetingColWidth = if (meetingCount > 0) availableMeetingWidth / meetingCount else 0f

        val scoutRight = 818f
        val scoutLeft = scoutRight - nameColWidth // 678f
        val totalLeft = 24f
        val totalRight = totalLeft + totalColWidth // 84f

        // 3. خلفية ترويسة الجدول
        val thBgPaint = Paint().apply {
            color = Color.rgb(232, 245, 233)
            style = Paint.Style.FILL
        }
        canvas.drawRect(24f, tableTop, 818f, tableHeaderBottom, thBgPaint)

        val thTextPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 10f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // ترويسة عمود اسم الشبل
        canvas.drawText("الاسم واللقب", (scoutLeft + scoutRight) / 2f, tableTop + 22f, thTextPaint)

        // ترويسات أعمدة الاجتماعات
        val fullDateFormatter = SimpleDateFormat("dd/MM/yyyy", com.example.presentation.common.AppLocale.AR_DZ)
        for (m in 0 until meetingCount) {
            val meeting = meetings[m]
            val mRight = scoutLeft - m * meetingColWidth
            val mLeft = mRight - meetingColWidth
            val mCenterX = (mLeft + mRight) / 2f

            val mHeaderPaint = Paint().apply {
                color = Color.rgb(46, 125, 50)
                textSize = 8.5f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val mDatePaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 8f
                typeface = cairoTypeface
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val paddedNumber = String.format(Locale.ROOT, "%02d", meeting.number)
            val fullDate = if (meeting.activity.startDate > 0) {
                fullDateFormatter.format(Date(meeting.activity.startDate))
            } else "—"

            canvas.drawText("الاجتماع التدريبي $paddedNumber", mCenterX, tableTop + 15f, mHeaderPaint)
            canvas.drawText(fullDate, mCenterX, tableTop + 29f, mDatePaint)
        }

        // ترويسة عمود المجموع
        canvas.drawText("المجموع", (totalLeft + totalRight) / 2f, tableTop + 22f, thTextPaint)

        // 4. أسطر الأشبال
        for (r in pageScouts.indices) {
            val scout = pageScouts[r]
            val yTop = tableHeaderBottom + r * rowHeight
            val yBottom = yTop + rowHeight
            val yBaseline = yTop + rowHeight * 0.68f

            val rowBg = if (r % 2 == 0) Color.WHITE else Color.rgb(250, 250, 250)
            canvas.drawRect(24f, yTop, 818f, yBottom, Paint().apply { color = rowBg })

            // اسم الشبل
            val namePaint = Paint().apply {
                color = Color.rgb(33, 33, 33)
                textSize = if (rowHeight < 20f) 8f else 9f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            val displayName = truncateText(scout.fullName, nameColWidth - 12f, namePaint)
            canvas.drawText(displayName, scoutRight - 6f, yBaseline, namePaint)

            // خلايا الاجتماعات
            for (m in 0 until meetingCount) {
                val meeting = meetings[m]
                val mRight = scoutLeft - m * meetingColWidth
                val mLeft = mRight - meetingColWidth

                val scoutRow = meeting.rows.firstOrNull { it.scout.id == scout.id }
                val status = scoutRow?.attendance?.attendanceStatus

                val (statusWord, cellBg, statusColor) = when (status) {
                    AttendanceStatus.PRESENT -> Triple("حاضر", Color.rgb(232, 245, 233), Color.rgb(46, 125, 50))
                    AttendanceStatus.LATE -> Triple("متأخر", Color.rgb(255, 248, 225), Color.rgb(245, 124, 0))
                    AttendanceStatus.VERY_LATE -> Triple("متأخر جداً", Color.rgb(251, 233, 231), Color.rgb(230, 74, 25))
                    AttendanceStatus.ABSENT_EXCUSED -> Triple("بعذر", Color.rgb(255, 224, 178), Color.rgb(230, 81, 0))
                    AttendanceStatus.ABSENT_UNEXCUSED -> Triple("بدون عذر", Color.rgb(255, 235, 238), Color.RED)
                    null -> Triple("—", Color.rgb(238, 238, 238), Color.GRAY)
                }

                canvas.drawRect(mLeft, yTop, mRight, yBottom, Paint().apply { color = cellBg })

                val statusPaint = Paint().apply {
                    color = statusColor
                    textSize = if (rowHeight < 20f) 7.5f else 8.5f
                    typeface = boldCairo
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(statusWord, (mLeft + mRight) / 2f, yBaseline, statusPaint)
            }

            // خلية المجموع
            val totalPoints = allMeetings.sumOf { meeting ->
                val sr = meeting.rows.firstOrNull { it.scout.id == scout.id }
                if (sr?.attendance != null && sr.attendance.attendanceStatus != null) {
                    sr.attendance.points
                } else 0
            }

            canvas.drawRect(totalLeft, yTop, totalRight, yBottom, Paint().apply { color = Color.rgb(245, 245, 245) })
            val totalPaint = Paint().apply {
                color = Color.BLACK
                textSize = if (rowHeight < 20f) 8.5f else 9.5f
                typeface = boldCairo
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$totalPoints", (totalLeft + totalRight) / 2f, yBaseline, totalPaint)
        }

        // 5. خطوط شبكة الجدول
        val gridPaint = Paint().apply {
            color = Color.rgb(210, 210, 210)
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        canvas.drawRect(24f, tableTop, 818f, tableBottom, gridPaint)
        canvas.drawLine(24f, tableHeaderBottom, 818f, tableHeaderBottom, gridPaint)

        for (r in 1..pageScouts.size) {
            val y = tableHeaderBottom + r * rowHeight
            canvas.drawLine(24f, y, 818f, y, gridPaint)
        }

        canvas.drawLine(scoutLeft, tableTop, scoutLeft, tableBottom, gridPaint)
        canvas.drawLine(totalRight, tableTop, totalRight, tableBottom, gridPaint)
        for (m in 0 until meetingCount) {
            val mLeft = scoutLeft - (m + 1) * meetingColWidth
            canvas.drawLine(mLeft, tableTop, mLeft, tableBottom, gridPaint)
        }

        // 6. التذييل
        val footerLineY = 562f
        canvas.drawLine(24f, footerLineY, 818f, footerLineY, Paint().apply { color = Color.LTGRAY; strokeWidth = 0.8f })

        val footerDate = SimpleDateFormat("yyyy/MM/dd HH:mm", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("تم الإنشاء في $footerDate", 818f, 576f, footerPaint)

        val pageInfoPaint = Paint(footerPaint).apply { textAlign = Paint.Align.LEFT }
        val pageStr = if (totalPages > 1) "صفحة $pageNumber من $totalPages | " else ""
        canvas.drawText("${pageStr}سرب النُّجُب — فوج محمد رحال", 24f, 576f, pageInfoPaint)
    }

    private fun truncateText(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }

    private fun drawEmptyRegisterMatrixPage(canvas: Canvas, cairoTypeface: Typeface, context: Context) {
        val boldCairo = Typeface.create(cairoTypeface, Typeface.BOLD)

        drawLogo(canvas, context, left = 24f, top = 18f, targetWidth = 38f, targetHeight = 38f)

        val titlePaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 18f
            typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val msgPaint = Paint().apply {
            color = Color.GRAY
            textSize = 13f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("سجل الاجتماعات التدريبية — سرب النُّجُب", 421f, 270f, titlePaint)
        canvas.drawText("لا توجد اجتماعات تدريبية مسجلة بعد في التطبيق.", 421f, 305f, msgPaint)

        val footerLineY = 562f
        canvas.drawLine(24f, footerLineY, 818f, footerLineY, Paint().apply { color = Color.LTGRAY; strokeWidth = 0.8f })
        val footerDate = SimpleDateFormat("yyyy/MM/dd HH:mm", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("تم الإنشاء في $footerDate", 818f, 576f, footerPaint)
    }

    fun exportProgressCardPdf(
        scout: Scout,
        badgeProgressList: List<BadgeProgress>,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = try {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }

        // الصفحة الأولى: شارة القبول + شارة الشبل المبتدئ
        val page1Info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = pdfDocument.startPage(page1Info)
        drawProgressCardPage1(page1.canvas, scout, badgeProgressList, cairoTypeface, context)
        pdfDocument.finishPage(page1)

        // الصفحة الثانية: شارة الشبل الثاني + شارة الشبل الأول + الاعتماد
        val page2Info = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = pdfDocument.startPage(page2Info)
        drawProgressCardPage2(page2.canvas, scout, badgeProgressList, cairoTypeface, context)
        pdfDocument.finishPage(page2)

        val pdfFile = File(context.cacheDir, "progress_card_${scout.id}.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareProgressCardPdf(file: File, scoutName: String, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "بطاقة التقدم لشارات الكفاءة: $scoutName")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة بطاقة التقدم"))
    }

    private fun drawProgressCardPage1(
        canvas: Canvas,
        scout: Scout,
        badgeProgressList: List<BadgeProgress>,
        typeface: Typeface,
        context: Context
    ) {
        val boldCairo = Typeface.create(typeface, Typeface.BOLD)

        val textPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 10f
            this.typeface = typeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val titlePaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 16f
            this.typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(200, 169, 81)
            textSize = 12f
            this.typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val headerPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 13f
            this.typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = Color.rgb(200, 169, 81)
            strokeWidth = 2.5f
            isAntiAlias = true
        }
        val boxPaint = Paint().apply {
            color = Color.rgb(246, 248, 246)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // شعار الترويسة
        drawLogo(canvas, context, left = 40f, top = 25f, targetWidth = 55f, targetHeight = 55f)

        canvas.drawText("سرب النُّجُب — فوج محمد رحال", 555f, 48f, titlePaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 68f, subtitlePaint)
        canvas.drawLine(40f, 95f, 555f, 95f, linePaint)

        // عنوان الوثيقة الرئيسي
        val docTitlePaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 15f
            this.typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("بطاقة التقدم لشارات الكفاءة (مرحلة الأشبال)", 297f, 120f, docTitlePaint)

        // قسم معلومات الشبل
        canvas.drawRoundRect(40f, 135f, 555f, 215f, 10f, 10f, boxPaint)
        canvas.drawText("بيانات الشبل ومستوى التقدم", 540f, 155f, headerPaint)
        canvas.drawLine(40f, 162f, 555f, 162f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })

        val grantedCount = badgeProgressList.count { it.isGranted }
        val totalActive = badgeProgressList.sumOf { it.totalActiveRequirements }
        val totalCompleted = badgeProgressList.sumOf { it.completedActiveRequirements }
        val overallPercentage = if (totalActive > 0) (totalCompleted * 100) / totalActive else 0

        canvas.drawText("الاسم واللقب: ${scout.fullName}", 540f, 180f, textPaint)
        canvas.drawText("العمر: ${scout.age} سنة", 320f, 180f, textPaint)
        canvas.drawText("سنة الالتحاق: ${scout.joinYear}", 160f, 180f, textPaint)

        canvas.drawText("الشارات الممنوحة: $grantedCount من 4", 540f, 200f, textPaint)
        canvas.drawText("نسبة الإنجاز الإجمالية: $overallPercentage%", 320f, 200f, textPaint)
        canvas.drawText("الحالة: ${if (scout.isActive) "نشط" else "مؤرشف"}", 160f, 200f, textPaint)

        // رسم شارة 1: القبول وشارة 2: المبتدئ
        var currentY = 230f
        val badge1 = badgeProgressList.firstOrNull { it.level == BadgeLevel.ACCEPTANCE }
        currentY = drawBadgeBox(canvas, "شارة القبول", badge1, currentY, 260f, typeface, boldCairo)

        val badge2 = badgeProgressList.firstOrNull { it.level == BadgeLevel.BEGINNER }
        drawBadgeBox(canvas, "شارة الشبل المبتدئ", badge2, currentY + 15f, 260f, typeface, boldCairo)

        // تذييل الصفحة 1
        canvas.drawLine(40f, 800f, 555f, 800f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerDate = SimpleDateFormat("yyyy/MM/dd HH:mm", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9f
            this.typeface = typeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("تاريخ الاستخراج: $footerDate", 555f, 818f, footerPaint)
        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("صفحة 1 من 2", 40f, 818f, footerPaint)
    }

    private fun drawProgressCardPage2(
        canvas: Canvas,
        scout: Scout,
        badgeProgressList: List<BadgeProgress>,
        typeface: Typeface,
        context: Context
    ) {
        val boldCairo = Typeface.create(typeface, Typeface.BOLD)

        val textPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 10f
            this.typeface = typeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val headerPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 13f
            this.typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = Color.rgb(200, 169, 81)
            strokeWidth = 2f
            isAntiAlias = true
        }

        // ترويسة الصفحة الثانية
        canvas.drawText("سرب النُّجُب — فوج محمد رحال (بطاقة التقدم: ${scout.fullName})", 555f, 45f, headerPaint)
        canvas.drawLine(40f, 55f, 555f, 55f, linePaint)

        // رسم شارة 3: الثاني وشارة 4: الأول
        var currentY = 70f
        val badge3 = badgeProgressList.firstOrNull { it.level == BadgeLevel.SECOND }
        currentY = drawBadgeBox(canvas, "شارة الشبل الثاني", badge3, currentY, 260f, typeface, boldCairo)

        val badge4 = badgeProgressList.firstOrNull { it.level == BadgeLevel.FIRST }
        currentY = drawBadgeBox(canvas, "شارة الشبل الأول", badge4, currentY + 15f, 260f, typeface, boldCairo)

        // قسم التوقيعات والاعتماد
        val signBoxTop = currentY + 20f
        val boxPaint = Paint().apply {
            color = Color.rgb(250, 250, 250)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(40f, signBoxTop, 555f, signBoxTop + 90f, 10f, 10f, boxPaint)
        canvas.drawText("الاعتماد والمصادقة الرسمية", 540f, signBoxTop + 22f, headerPaint)
        canvas.drawLine(40f, signBoxTop + 30f, 555f, signBoxTop + 30f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })

        canvas.drawText("توقيع قائد السرب:", 540f, signBoxTop + 50f, textPaint)
        canvas.drawText("......................................", 540f, signBoxTop + 72f, textPaint)

        canvas.drawText("توقيع ومصادقة قائد الفوج:", 260f, signBoxTop + 50f, textPaint)
        canvas.drawText("......................................", 260f, signBoxTop + 72f, textPaint)

        // تذييل الصفحة 2
        canvas.drawLine(40f, 800f, 555f, 800f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footerDate = SimpleDateFormat("yyyy/MM/dd HH:mm", com.example.presentation.common.AppLocale.AR_DZ).format(Date())
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9f
            this.typeface = typeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("تاريخ الاستخراج: $footerDate", 555f, 818f, footerPaint)
        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("صفحة 2 من 2", 40f, 818f, footerPaint)
    }

    private fun drawBadgeBox(
        canvas: Canvas,
        badgeTitle: String,
        badge: BadgeProgress?,
        topY: Float,
        maxHeight: Float,
        typeface: Typeface,
        boldCairo: Typeface
    ): Float {
        val bottomY = topY + maxHeight
        val boxPaint = Paint().apply {
            color = Color.rgb(252, 252, 252)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(220, 224, 220)
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawRoundRect(40f, topY, 555f, bottomY, 8f, 8f, boxPaint)
        canvas.drawRoundRect(40f, topY, 555f, bottomY, 8f, 8f, borderPaint)

        val headerPaint = Paint().apply {
            color = Color.rgb(46, 125, 50)
            textSize = 12f
            this.typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val statusPaint = Paint().apply {
            color = if (badge?.isGranted == true) Color.rgb(46, 125, 50) else Color.rgb(180, 140, 20)
            textSize = 10f
            this.typeface = boldCairo
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }

        canvas.drawText(badgeTitle, 540f, topY + 20f, headerPaint)

        val isGranted = badge?.isGranted == true
        val statusText = if (isGranted) {
            val dateStr = if (badge?.grantedAt != null) {
                SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ).format(Date(badge.grantedAt))
            } else ""
            "ممنوحة ✔ ($dateStr)"
        } else {
            val pct = badge?.percentage ?: 0
            val comp = badge?.completedActiveRequirements ?: 0
            val tot = badge?.totalActiveRequirements ?: 0
            "قيد الإنجاز ($comp من $tot — $pct%)"
        }
        canvas.drawText(statusText, 55f, topY + 20f, statusPaint)
        canvas.drawLine(40f, topY + 28f, 555f, topY + 28f, Paint().apply { color = Color.rgb(230, 230, 230); strokeWidth = 1f })

        // رسم المتطلبات
        val itemPaint = Paint().apply {
            color = Color.rgb(50, 50, 50)
            textSize = 9.5f
            this.typeface = typeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val dateSubPaint = Paint().apply {
            color = Color.rgb(100, 140, 100)
            textSize = 8.5f
            this.typeface = typeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        var lineY = topY + 45f
        val reqs = badge?.requirements ?: emptyList()
        if (reqs.isEmpty()) {
            canvas.drawText("لا توجد متطلبات مسجلة بعد لهذه الشارة.", 535f, lineY, itemPaint)
        } else {
            for (reqState in reqs.take(8)) {
                if (lineY > bottomY - 15f) break
                val checkSymbol = if (reqState.completed) "[ ✔ ]" else "[   ]"
                canvas.drawText("$checkSymbol  ${reqState.requirement.title}", 540f, lineY, itemPaint)

                if (reqState.completed && reqState.completedAt != null) {
                    val dateFormatted = SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ).format(Date(reqState.completedAt))
                    val leader = reqState.completedByName ?: "القائد"
                    canvas.drawText("($dateFormatted — $leader)", 180f, lineY, dateSubPaint)
                }
                lineY += 24f
            }
        }

        return bottomY
    }

    data class ScoutRawPoints(
        val scoutName: String,
        val attendanceScore: Double?,
        val leaderPoints: Double?,
        val quranPoints: Int?,
        val notebookPoints: Int?,
        val progressScore: Int? = null,
        val totalScore: Number? = null,
        val kafaaPoints: Int? = null,
        val hobbyPoints: Int? = null,
        val rank: Int = 0,
        val overallAverage: Double? = null
    )

    fun exportRawPointsPdf(
        scoutPointsList: List<ScoutRawPoints>,
        context: Context
    ): File {
        val pdfDocument = PdfDocument()
        val cairoFont = getCairoTypeface(context)
        val cairoBold = getBoldCairoTypeface(cairoFont)

        val pageWidth = 595
        val pageHeight = 842
        val rowsPerPage = 25

        val totalPages = ((scoutPointsList.size + rowsPerPage - 1) / rowsPerPage).coerceAtLeast(1)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = cairoBold
            textAlign = Paint.Align.CENTER
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        val rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F9F9F9")
            style = Paint.Style.FILL
        }

        val colWidths = floatArrayOf(
            30f,   // 1) الرقم
            130f,  // 2) الاسم
            55f,   // 3) تقييم الحضور
            55f,   // 4) تقييم القادة
            55f,   // 5) حفظ القرآن
            55f,   // 6) الكراس
            50f,   // 7) نقاط التقدم
            55f,   // 8) المجموع الكلي
            55f    // 9) المعدل العام
        )
        val totalTableWidth = colWidths.sum()
        val leftMargin = (pageWidth - totalTableWidth) / 2f

        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // رأس الصفحة الكشفية
            var yPos = 35f
            val nationalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 9.5f
                typeface = cairoFont
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("الجمهورية الجزائرية الديمقراطية الشعبية", pageWidth / 2f, yPos, nationalPaint)
            yPos += 14f
            canvas.drawText("الكشافة الإسلامية الجزائرية — المحافظة الولائية لسطيف", pageWidth / 2f, yPos, nationalPaint)
            yPos += 14f
            canvas.drawText("فوج محمد رحال — سرب النُّجُب للأشبال", pageWidth / 2f, yPos, nationalPaint)
            yPos += 20f

            drawLogo(canvas, context, left = leftMargin, top = 25f, targetWidth = 45f, targetHeight = 45f)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1B5E20")
                textSize = 15f
                typeface = cairoBold
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("سجل تقييم الأشبال (نقاط)", pageWidth / 2f, yPos, titlePaint)
            yPos += 14f

            val sdf = SimpleDateFormat("dd/MM/yyyy", com.example.presentation.common.AppLocale.AR_DZ)
            val subInfo = "تاريخ التقرير: ${sdf.format(Date())} | إجمالي الأشبال: ${scoutPointsList.size}"
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 8.5f
                typeface = cairoFont
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(subInfo, pageWidth / 2f, yPos, subPaint)
            yPos += 18f

            // رسم جدول النقاط الخام
            val startX = leftMargin + totalTableWidth
            val tableTop = yPos
            val rowHeight = 24f

            // ترويسة الجدول
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E8F5E9")
                style = Paint.Style.FILL
            }
            canvas.drawRect(leftMargin, tableTop, leftMargin + totalTableWidth, tableTop + rowHeight, headerBgPaint)
            canvas.drawRect(leftMargin, tableTop, leftMargin + totalTableWidth, tableTop + rowHeight, borderPaint)

            val headers = listOf("الرقم", "الاسم", "تقييم الحضور", "تقييم القادة", "حفظ القرآن", "الكراس", "نقاط التقدم", "المجموع الكلي", "المعدل العام")
            var currentX = startX
            for (i in headers.indices) {
                val w = colWidths[i]
                currentX -= w
                canvas.drawText(headers[i], currentX + w / 2f, tableTop + 16f, headerPaint)
                canvas.drawLine(currentX, tableTop, currentX, tableTop + rowHeight, borderPaint)
            }

            var currentY = tableTop + rowHeight
            val pageRows = scoutPointsList.drop(pageIndex * rowsPerPage).take(rowsPerPage)

            pageRows.forEachIndexed { idx, item ->
                val globalIndex = pageIndex * rowsPerPage + idx + 1
                if (idx % 2 == 1) {
                    canvas.drawRect(leftMargin, currentY, leftMargin + totalTableWidth, currentY + rowHeight, rowBgPaint)
                }
                canvas.drawRect(leftMargin, currentY, leftMargin + totalTableWidth, currentY + rowHeight, borderPaint)

                var xCell = startX
                // 1) الرقم
                xCell -= colWidths[0]
                val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 9.5f
                    typeface = cairoFont
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(globalIndex.toString(), xCell + colWidths[0] / 2f, currentY + 16f, centerPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 2) اسم الشبل
                xCell -= colWidths[1]
                val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(item.scoutName, xCell + colWidths[1] - 8f, currentY + 16f, namePaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 3) تقييم الحضور (نقاط من 20)
                xCell -= colWidths[2]
                val attendanceText = formatOutOfTwenty(item.attendanceScore)
                val attColor = if (item.attendanceScore != null) Color.parseColor("#2E7D32") else Color.GRAY
                val attPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = attColor
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(attendanceText, xCell + colWidths[2] / 2f, currentY + 16f, attPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 4) تقييم القادة (نقاط من 20)
                xCell -= colWidths[3]
                val leaderText = formatOutOfTwenty(item.leaderPoints)
                val leaderColor = if (item.leaderPoints != null) Color.parseColor("#1B5E20") else Color.GRAY
                val leaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = leaderColor
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(leaderText, xCell + colWidths[3] / 2f, currentY + 16f, leaderPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 5) تقييم حفظ القرآن (نقاط من 20)
                xCell -= colWidths[4]
                val quranText = formatOutOfTwenty(item.quranPoints)
                val quranColor = if (item.quranPoints != null) Color.parseColor("#0D47A1") else Color.GRAY
                val quranPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = quranColor
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(quranText, xCell + colWidths[4] / 2f, currentY + 16f, quranPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 6) تقييم الكراس (نقاط من 20)
                xCell -= colWidths[5]
                val notebookText = formatOutOfTwenty(item.notebookPoints)
                val notebookColor = if (item.notebookPoints != null) Color.parseColor("#E65100") else Color.GRAY
                val notebookPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = notebookColor
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(notebookText, xCell + colWidths[5] / 2f, currentY + 16f, notebookPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 7) نقاط التقدم
                xCell -= colWidths[6]
                val progText = if (item.progressScore != null) item.progressScore.toString() else "—"
                val progColor = if (item.progressScore != null && item.progressScore > 0) Color.parseColor("#1565C0") else Color.GRAY
                val progPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = progColor
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(progText, xCell + colWidths[6] / 2f, currentY + 16f, progPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 8) المجموع الكلي
                xCell -= colWidths[7]
                val scoreDouble = item.totalScore?.toDouble()
                val totalText = if (scoreDouble != null) {
                    if (scoreDouble % 1.0 == 0.0) scoreDouble.toInt().toString() else String.format(Locale.US, "%.1f", scoreDouble)
                } else "—"
                val totalColor = when {
                    scoreDouble == null -> Color.GRAY
                    scoreDouble < 50.0 -> Color.parseColor("#D32F2F")
                    scoreDouble < 80.0 -> Color.parseColor("#F57C00")
                    else -> Color.parseColor("#2E7D32")
                }
                val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = totalColor
                    textSize = 10f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(totalText, xCell + colWidths[7] / 2f, currentY + 16f, totalPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowHeight, borderPaint)

                // 9) المعدل العام (MUST BE LAST)
                xCell -= colWidths[8]
                val avg = item.overallAverage ?: if (item.totalScore != null) item.totalScore.toDouble() / 4.0 else null
                val avgText = if (avg != null) formatOutOfTwenty(avg) else "—"
                val avgColor = when {
                    avg == null -> Color.GRAY
                    avg < 10.0 -> Color.parseColor("#D32F2F")
                    avg < 16.0 -> Color.parseColor("#F57C00")
                    else -> Color.parseColor("#2E7D32")
                }
                val avgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = avgColor
                    textSize = 10f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(avgText, xCell + colWidths[8] / 2f, currentY + 16f, avgPaint)

                currentY += rowHeight
            }

            // أسفل الصفحة
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 8.5f
                typeface = cairoFont
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("صفحة ${pageIndex + 1} من $totalPages — سرب النُّجُب للأشبال", pageWidth / 2f, pageHeight - 20f, footerPaint)

            pdfDocument.finishPage(page)
        }

        val cacheDir = context.cacheDir
        val file = File(cacheDir, "Scout_Raw_Points_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()

        return file
    }

    private fun formatOutOfTwenty(value: Number?): String {
        if (value == null) return "—"
        val d = value.toDouble().coerceAtLeast(0.0)
        return if (d % 1.0 == 0.0) {
            "${d.toInt()}/20"
        } else {
            String.format(Locale.US, "%.1f/20", d)
        }
    }

    fun shareRawPointsPdf(file: File, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "سجل تقييم الأشبال (نقاط) — سرب النُّجُب")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة سجل تقييم الأشبال كـ PDF"))
    }
}


