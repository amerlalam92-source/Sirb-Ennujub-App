package com.example.presentation.reports

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.activity.Activity
import com.example.domain.activity.ActivityType
import com.example.domain.auth.Leader
import com.example.domain.finance.ExpenseCategory
import com.example.domain.finance.FinanceContribution
import com.example.domain.finance.FinanceSubscription
import com.example.domain.finance.FinanceSummary
import com.example.domain.finance.FinanceTransaction
import com.example.domain.finance.FinanceTransactionType
import com.example.domain.finance.ScoutDebt
import com.example.domain.log.ActivityLog
import com.example.domain.settings.CustomTerms
import com.example.domain.settings.replaceCustomWords
import java.util.Calendar
import com.example.domain.attendance.Attendance
import com.example.domain.attendance.AttendanceRow
import com.example.domain.attendance.AttendanceStatus
import com.example.domain.attendance.MeetingWithAttendance
import com.example.domain.attendance.ScoutStatistics
import com.example.domain.progress.BadgeLevel
import com.example.domain.progress.BadgeProgress
import com.example.domain.scout.Scout
import com.example.presentation.finance.reports.ScoutPaymentDetail
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

    var currentLogoBase64: String? = null
    var currentTerms: CustomTerms? = null

    private fun effectiveTerms(terms: CustomTerms?): CustomTerms = terms ?: currentTerms ?: CustomTerms()

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
                        currentLine = ""
                    }
                    if (maxWidth > 0f && paint.measureText(word) > maxWidth) {
                        var remaining = word
                        while (remaining.isNotEmpty()) {
                            val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                            val chunk = remaining.substring(0, count)
                            remaining = remaining.substring(count)
                            if (remaining.isNotEmpty()) {
                                lines.add(chunk)
                            } else {
                                currentLine = chunk
                            }
                        }
                    } else {
                        currentLine = word
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
        }
        return lines
    }

    private data class MonthlyStatementRow(
        val dateStr: String,
        val typeLines: List<String>,
        val categoryLines: List<String>,
        val descriptionLines: List<String>,
        val amountStr: String,
        val isIncome: Boolean,
        val rowHeight: Float
    )

    private data class ActivityDetailsCardBlock(
        val title: String,
        val lines: List<String>
    )

    fun exportScoutReport(
        scout: Scout,
        stats: ScoutStatistics,
        activities: List<Activity>,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = getCairoTypeface(context)
        val boldCairo = getBoldCairoTypeface(cairoTypeface)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // قياس A4 القياسي (نقاط)
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val trainingActivities = activities.filter {
            it.type == ActivityType.TRAINING_MEETING.key || it.activityType == ActivityType.TRAINING_MEETING
        }
        drawScoutReport(canvas, scout, stats, trainingActivities, cairoTypeface, boldCairo, context, terms)

        pdfDocument.finishPage(page)

        val pdfFile = File(context.cacheDir, "scout_report_${scout.id}.pdf")

        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()

        return pdfFile
    }

    fun shareScoutPdf(
        file: File,
        scoutName: String,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "تقرير ${terms.scoutSingularDefinite}: $scoutName")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير ${terms.scoutSingularDefinite}"))
    }

    private fun getLogoBitmap(context: Context, logoBase64: String? = null): Bitmap? {
        val effectiveBase64 = if (!logoBase64.isNullOrEmpty()) {
            logoBase64
        } else {
            currentLogoBase64 ?: try {
                context.getSharedPreferences("branding_prefs", Context.MODE_PRIVATE)
                    .getString("logo_base64", null)
            } catch (_: Exception) { null }
        }

        return if (!effectiveBase64.isNullOrEmpty()) {
            try {
                val imageBytes = Base64.decode(effectiveBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) {
                BitmapFactory.decodeResource(context.resources, R.drawable.ennujub_logo)
            }
        } else {
            BitmapFactory.decodeResource(context.resources, R.drawable.ennujub_logo)
        }
    }

    private fun drawLogo(
        canvas: Canvas,
        context: Context,
        left: Float,
        top: Float,
        targetWidth: Float = 55f,
        targetHeight: Float = 55f,
        logoBase64: String? = null
    ) {
        try {
            val logoBitmap = getLogoBitmap(context, logoBase64)
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
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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

        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 50f, titlePaint)
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

        val trainingMeetingIds = activities
            .filter { it.type == ActivityType.TRAINING_MEETING.key || it.activityType == ActivityType.TRAINING_MEETING }
            .map { it.id }
            .toSet()
        val filteredMissedActivities = if (activities.isNotEmpty()) {
            stats.missedActivities.filter { it.activityId in trainingMeetingIds }
        } else {
            stats.missedActivities
        }

        var yPos = 415f
        if (filteredMissedActivities.isEmpty()) {
            canvas.drawText("ما شاء الله! لا توجد أي غيابات مسجلة لـ${terms.scoutSingularDefinite} حتى الآن.", 540f, yPos, textPaint)
        } else {
            for (item in filteredMissedActivities.take(12)) {
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
        canvas.drawText("وثيقة كشفية صادرة عن تطبيق ${terms.groupName} ${terms.unitNameDefinite} المعتمد", 200f, 808f, footerPaint)
    }

    fun exportActivityAttendancePdf(
        activity: Activity,
        rows: List<AttendanceRow>,
        trainingMeetingNumber: Int?,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = getCairoTypeface(context)
        val boldCairo = getBoldCairoTypeface(cairoTypeface)
        val activeRows = rows.filter { it.scout.isActive }
        val rowsPerPage = 25
        val totalPages = maxOf(1, (activeRows.size + rowsPerPage - 1) / rowsPerPage)

        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val startRow = pageIndex * rowsPerPage
            val endRow = minOf(activeRows.size, (pageIndex + 1) * rowsPerPage)
            val pageRows = if (activeRows.isNotEmpty()) activeRows.subList(startRow, endRow) else emptyList()

            drawActivityAttendancePage(
                canvas = canvas,
                activity = activity,
                pageRows = pageRows,
                startRowIndex = startRow,
                trainingMeetingNumber = trainingMeetingNumber,
                pageNumber = pageIndex + 1,
                totalPages = totalPages,
                totalScouts = activeRows.size,
                cairoTypeface = cairoTypeface,
                boldCairo = boldCairo,
                context = context,
                terms = terms
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

    fun shareActivityAttendancePdf(
        file: File,
        activityTitle: String,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
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

    fun exportActivityDetailsPdf(
        activity: Activity,
        meetingTopics: List<String> = activity.meetingTopics,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        return generateActivityDetailsDocument(activity, meetingTopics, context, terms)
    }

    fun shareActivityPdf(
        file: File,
        activityTitle: String,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "بطاقة تفاصيل النشاط: $activityTitle")
            putExtra(Intent.EXTRA_TEXT, "مرفق بطاقة تفاصيل النشاط الكشفي ($activityTitle) متضمناً البيانات الفنية، الأهداف، الأدوات، والتقرير الأدبي بصيغة PDF.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة تفاصيل النشاط"))
    }

    fun exportTrainingMeetingsRegisterPdf(
        meetings: List<MeetingWithAttendance>,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = getCairoTypeface(context) ?: Typeface.DEFAULT

        val chronologicalMeetings = meetings.sortedBy { it.number }
        val allScouts = (chronologicalMeetings.firstOrNull()?.rows?.map { it.scout } ?: emptyList())
            .filter { it.isActive }

        if (chronologicalMeetings.isEmpty() || allScouts.isEmpty()) {
            val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            drawEmptyRegisterMatrixPage(page.canvas, cairoTypeface, context, terms)
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
                        context = context,
                        terms = terms
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

    fun shareTrainingMeetingsRegisterPdf(
        file: File,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "سجل الاجتماعات التدريبية — ${terms.groupName} ${terms.unitNameDefinite}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة سجل الاجتماعات التدريبية كـ PDF"))
    }

    val ARABIC_MONTHS = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    private fun isValidIncomeTransaction(tx: FinanceTransaction): Boolean {
        return !tx.isRolledBack && (
            tx.type == FinanceTransactionType.DONATION ||
                tx.type == FinanceTransactionType.ADVANCE_PAYMENT ||
                tx.type == FinanceTransactionType.CONTRIBUTION
            )
    }

    private fun effectiveSubscriptionDate(sub: FinanceSubscription): Long {
        return if (sub.recordedAt > 0L) sub.recordedAt else sub.activityDate
    }

    fun exportMonthlyStatementPdf(
        month: Int,
        year: Int,
        transactions: List<FinanceTransaction>,
        subscriptions: List<FinanceSubscription> = emptyList(),
        summary: FinanceSummary,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = try {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }

        val activeTransactions = transactions.filter { !it.isRolledBack }
        val cal = Calendar.getInstance()
        val monthTransactions = activeTransactions.filter { tx ->
            cal.timeInMillis = tx.date
            val m = cal.get(Calendar.MONTH) + 1
            val y = cal.get(Calendar.YEAR)
            m == month && y == year
        }.sortedBy { it.date }

        val priorTransactions = activeTransactions.filter { tx ->
            cal.timeInMillis = tx.date
            val m = cal.get(Calendar.MONTH) + 1
            val y = cal.get(Calendar.YEAR)
            y < year || (y == year && m < month)
        }

        val paidSubscriptions = subscriptions.filter { it.paidAmount > 0 }
        val monthSubscriptions = paidSubscriptions.filter { sub ->
            cal.timeInMillis = effectiveSubscriptionDate(sub)
            val m = cal.get(Calendar.MONTH) + 1
            val y = cal.get(Calendar.YEAR)
            m == month && y == year
        }
        val priorSubscriptions = paidSubscriptions.filter { sub ->
            cal.timeInMillis = effectiveSubscriptionDate(sub)
            val m = cal.get(Calendar.MONTH) + 1
            val y = cal.get(Calendar.YEAR)
            y < year || (y == year && m < month)
        }

        val priorTxIncome = priorTransactions
            .filter { isValidIncomeTransaction(it) || it.type == FinanceTransactionType.OPENING_BALANCE }
            .sumOf { it.amount }
        val priorSubsIncome = priorSubscriptions.sumOf { it.paidAmount }
        val priorExpenses = priorTransactions
            .filter { it.type == FinanceTransactionType.EXPENSE }
            .sumOf { it.amount }
        val openingBalance = (priorTxIncome + priorSubsIncome) - priorExpenses

        val monthTxIncome = monthTransactions.filter { isValidIncomeTransaction(it) }.sumOf { it.amount }
        val monthSubsIncome = monthSubscriptions.sumOf { it.paidAmount }
        val totalIncome = monthTxIncome + monthSubsIncome
        val totalExpenses = monthTransactions
            .filter { it.type == FinanceTransactionType.EXPENSE }
            .sumOf { it.amount }
        val closingBalance = openingBalance + totalIncome - totalExpenses

        val monthTableSourceTxs = monthTransactions.filter {
            isValidIncomeTransaction(it) || it.type == FinanceTransactionType.EXPENSE
        }

        // تجميع المساهمات والمعاملات المماثلة في جدول كشف الحساب الشهري
        val (aggregatedRaw, individualList) = monthTableSourceTxs.partition { tx ->
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
                source = terms.scoutPluralDefinite,
                createdAt = latestDate
            )
        }

        val aggregatedSubscriptions = monthSubscriptions
            .groupBy { sub -> sub.activityId.ifBlank { sub.activityTitle.ifBlank { "نشاط كشفي" } } }
            .map { (activityKey, subList) ->
                val latestDate = subList.maxOfOrNull { effectiveSubscriptionDate(it) } ?: System.currentTimeMillis()
                val sumAmount = subList.sumOf { it.paidAmount }
                val count = subList.size
                val activityTitle = subList.firstOrNull { it.activityTitle.isNotBlank() }?.activityTitle ?: "نشاط كشفي"
                FinanceTransaction(
                    id = "synthetic_sub_${activityKey.hashCode()}",
                    type = FinanceTransactionType.CONTRIBUTION,
                    amount = sumAmount,
                    date = latestDate,
                    description = "مجموع اشتراك: $activityTitle ($count)",
                    category = null,
                    source = terms.scoutPluralDefinite,
                    createdAt = latestDate
                )
            }

        val finalTableTransactions = (individualList + aggregatedSynthetic + aggregatedSubscriptions)
            .sortedBy { it.date }

        // عرض الأعمدة المعدل (RTL من 555f إلى 40f، المجموع = 515f):
        // التاريخ: 44f (555..511) | النوع: 54f (511..457) | التصنيف: 86f (457..371) | الوصف: 256f (371..115) | المبلغ: 75f (115..40)
        val colTypeWidth = 54f
        val colCatWidth = 86f
        val colDescWidth = 256f

        val measureRowPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 9.5f
            typeface = cairoTypeface
            isAntiAlias = true
        }
        val rowDateFmt = SimpleDateFormat("MM/dd", Locale.US)
        val rowLineHeight = 13f

        val preparedRows = finalTableTransactions.map { tx ->
            val dStr = if (tx.date > 0) rowDateFmt.format(Date(tx.date)) else "-"
            val typeStr = if (tx.id.startsWith("synthetic_sub_")) "اشتراك" else tx.type.arabicLabel
            val catSource = when {
                !tx.isIncome() -> tx.category?.arabicLabel ?: "مصروف"
                !tx.source.isNullOrBlank() -> tx.source
                else -> tx.type.arabicLabel
            }
            val desc = if (tx.description.isBlank()) "-" else tx.description

            val typeLines = wrapText(typeStr, measureRowPaint, colTypeWidth - 8f).ifEmpty { listOf(typeStr) }
            val catLines = wrapText(catSource, measureRowPaint, colCatWidth - 10f).ifEmpty { listOf(catSource) }
            val descLines = wrapText(desc, measureRowPaint, colDescWidth - 10f).ifEmpty { listOf(desc) }

            val maxLines = maxOf(1, typeLines.size, catLines.size, descLines.size)
            val dynamicRowHeight = maxOf(24f, 10f + maxLines * rowLineHeight)
            val amountStr = if (tx.isIncome()) "+${tx.amount} دج" else "-${tx.amount} دج"

            MonthlyStatementRow(
                dateStr = dStr,
                typeLines = typeLines,
                categoryLines = catLines,
                descriptionLines = descLines,
                amountStr = amountStr,
                isIncome = tx.isIncome(),
                rowHeight = dynamicRowHeight
            )
        }

        // السعة العمودية المتاحة لصفوف الجدول قبل خط التذييل (y = 800f -> الحد الأقصى 788f)
        // الصفحة الأولى: 110f + 20f (العنوان) + 96f (بطاقة الملخص) + 24f (ترويسة الجدول) = 250f -> المتاح 538f
        // الصفحات التالية: 110f + 24f (ترويسة الجدول) = 134f -> المتاح 654f
        val firstPageCapacity = 788f - 250f
        val subsequentPageCapacity = 788f - 134f

        val pages = mutableListOf<List<MonthlyStatementRow>>()
        if (preparedRows.isEmpty()) {
            pages.add(emptyList())
        } else {
            var currentPageRows = mutableListOf<MonthlyStatementRow>()
            var usedHeight = 0f
            var currentLimit = firstPageCapacity
            for (row in preparedRows) {
                if (usedHeight + row.rowHeight > currentLimit && currentPageRows.isNotEmpty()) {
                    pages.add(currentPageRows)
                    currentPageRows = mutableListOf()
                    usedHeight = 0f
                    currentLimit = subsequentPageCapacity
                }
                currentPageRows.add(row)
                usedHeight += row.rowHeight
            }
            if (currentPageRows.isNotEmpty()) {
                pages.add(currentPageRows)
            }
        }

        val totalPages = pages.size
        pages.forEachIndexed { pageIndex, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            drawMonthlyStatementPage(
                canvas = canvas,
                month = month,
                year = year,
                pageRows = pageRows,
                pageIndex = pageIndex + 1,
                totalPages = totalPages,
                isFirstPage = (pageIndex == 0),
                openingBalance = openingBalance,
                totalIncome = totalIncome,
                txIncome = monthTxIncome,
                subsIncome = monthSubsIncome,
                totalExpenses = totalExpenses,
                closingBalance = closingBalance,
                cairoTypeface = cairoTypeface,
                context = context,
                terms = terms
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

    fun shareMonthlyStatementPdf(
        file: File,
        month: Int,
        year: Int,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val monthName = ARABIC_MONTHS.getOrElse(month - 1) { "شهر $month" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "كشف حساب لشهر $monthName $year — ${terms.groupName} ${terms.unitNameDefinite}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة كشف الحساب الشهري كـ PDF"))
    }

    fun exportExpensesByCategoryPdf(
        transactions: List<FinanceTransaction>,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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
            context = context,
            terms = terms
        )

        pdfDocument.finishPage(page)

        val pdfFile = File(context.cacheDir, "expenses_by_category.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareExpensesByCategoryPdf(
        file: File,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "تقرير المصاريف حسب التصنيف — ${terms.groupName} ${terms.unitNameDefinite}")
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
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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
            context = context,
            terms = terms
        )

        pdfDocument.finishPage(page)

        val pdfFile = File(context.cacheDir, "subscriptions_and_debts_summary.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareSubscriptionsAndDebtsSummaryPdf(
        file: File,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "تقرير الاشتراكات والديون — ${terms.groupName} ${terms.unitNameDefinite}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير الاشتراكات والديون كـ PDF"))
    }

    // 2) تقرير الديون التفصيلي
    fun exportDetailedDebtsPdf(
        debts: List<ScoutDebt>,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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
                context = context,
                terms = terms
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

    fun shareDetailedDebtsPdf(
        file: File,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "تقرير الديون التفصيلي — ${terms.groupName} ${terms.unitNameDefinite}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير الديون التفصيلي كـ PDF"))
    }

    // 3) التقرير السنوي الشامل
    fun exportAnnualStatementPdf(
        year: Int,
        transactions: List<FinanceTransaction>,
        subscriptions: List<FinanceSubscription> = emptyList(),
        summary: FinanceSummary,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = try {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        val boldCairo = Typeface.create(cairoTypeface, Typeface.BOLD)

        val cal = Calendar.getInstance()
        val activeTransactions = transactions.filter { !it.isRolledBack }
        val yearTransactions = activeTransactions.filter { tx ->
            cal.timeInMillis = tx.date
            cal.get(Calendar.YEAR) == year
        }
        val priorTransactions = activeTransactions.filter { tx ->
            cal.timeInMillis = tx.date
            cal.get(Calendar.YEAR) < year
        }

        val paidSubscriptions = subscriptions.filter { it.paidAmount > 0 }
        val yearSubscriptions = paidSubscriptions.filter { sub ->
            cal.timeInMillis = effectiveSubscriptionDate(sub)
            cal.get(Calendar.YEAR) == year
        }
        val priorSubscriptions = paidSubscriptions.filter { sub ->
            cal.timeInMillis = effectiveSubscriptionDate(sub)
            cal.get(Calendar.YEAR) < year
        }

        val priorTxIncome = priorTransactions
            .filter { isValidIncomeTransaction(it) || it.type == FinanceTransactionType.OPENING_BALANCE }
            .sumOf { it.amount }
        val priorSubsIncome = priorSubscriptions.sumOf { it.paidAmount }
        val priorExpenses = priorTransactions
            .filter { it.type == FinanceTransactionType.EXPENSE }
            .sumOf { it.amount }
        val openingBalance = (priorTxIncome + priorSubsIncome) - priorExpenses

        val yearTxIncome = yearTransactions.filter { isValidIncomeTransaction(it) }.sumOf { it.amount }
        val yearSubsIncome = yearSubscriptions.sumOf { it.paidAmount }
        val totalIncome = yearTxIncome + yearSubsIncome
        val totalExpenses = yearTransactions
            .filter { it.type == FinanceTransactionType.EXPENSE }
            .sumOf { it.amount }
        val netBalance = totalIncome - totalExpenses
        val closingBalance = openingBalance + netBalance

        val monthsStats = (1..12).map { m ->
            val mTx = yearTransactions.filter { tx ->
                cal.timeInMillis = tx.date
                (cal.get(Calendar.MONTH) + 1) == m
            }
            val mSubs = yearSubscriptions.filter { sub ->
                cal.timeInMillis = effectiveSubscriptionDate(sub)
                (cal.get(Calendar.MONTH) + 1) == m
            }
            val inc = mTx.filter { isValidIncomeTransaction(it) }.sumOf { it.amount } +
                mSubs.sumOf { it.paidAmount }
            val exp = mTx.filter { it.type == FinanceTransactionType.EXPENSE }.sumOf { it.amount }
            AnnualMonthStat(
                monthIndex = m,
                monthName = ARABIC_MONTHS.getOrElse(m - 1) { "شهر $m" },
                income = inc,
                expense = exp,
                net = inc - exp
            )
        }

        val expensesList = yearTransactions.filter { it.type == FinanceTransactionType.EXPENSE }
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
            txIncome = yearTxIncome,
            subsIncome = yearSubsIncome,
            openingBalance = openingBalance,
            totalExpenses = totalExpenses,
            netBalance = netBalance,
            closingBalance = closingBalance,
            monthsStats = monthsStats,
            topCategories = topCategories,
            cairoTypeface = cairoTypeface,
            boldCairo = boldCairo,
            context = context,
            terms = terms
        )

        pdfDocument.finishPage(page)

        val pdfFile = File(context.cacheDir, "annual_statement_$year.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareAnnualStatementPdf(
        file: File,
        year: Int,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "التقرير السنوي الشامل لعام $year — ${terms.groupName} ${terms.unitNameDefinite}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة التقرير السنوي الشامل كـ PDF"))
    }

    // 4) كشف حساب شبل
    fun exportScoutStatementPdf(
        scout: Scout,
        subscriptions: List<FinanceSubscription>,
        contributions: List<FinanceContribution> = emptyList(),
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms(),
        fileNameOverride: String? = null
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
            context = context,
            terms = terms
        )

        pdfDocument.finishPage(page)

        val rawName = scout.fullName.ifBlank { scout.name }.ifBlank { scout.id }
        val safeName = rawName.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_")
            .trim('_')
            .ifBlank { scout.id.ifBlank { "scout" } }
        val fileName = fileNameOverride ?: "Scout_Statement_${safeName}.pdf"
        val pdfFile = File(context.cacheDir, fileName)
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareScoutStatementPdf(
        file: File,
        scoutName: String,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "كشف حساب ${terms.scoutSingularDefinite}: $scoutName — ${terms.groupName} ${terms.unitNameDefinite}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة كشف حساب ${terms.scoutSingularDefinite} كـ PDF"))
    }

    /**
     * مشاركة عدة ملفات PDF دفعة واحدة عبر تطبيقات التواصل
     */
    fun shareMultiplePdfs(
        uris: List<Uri>,
        subject: String,
        chooserTitle: String,
        context: Context
    ) {
        if (uris.isEmpty()) return
        val arrayListUris = ArrayList(uris)
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListUris)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
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
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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
        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 45f, headerPaint)
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
        canvas.drawText("ملخص عام للمحصّل والمتبقي من اشتراكات ومساهمات ${terms.scoutPluralDefinite}", 297f, currentY, subTitleDocPaint)
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
        canvas.drawText("تقرير مالي رسمي — ${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 815f, footerPaint)
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
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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
        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 45f, headerPaint)
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
            canvas.drawText("تقرير الديون التفصيلي لـ${terms.scoutPluralDefinite}", 297f, currentY, titlePaint)
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

            canvas.drawText("عدد ${terms.scoutPluralDefinite} المدينين: $totalScoutsCount ${terms.scoutSingular}", 540f, currentY + 22f, summaryTextPaint)
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

        canvas.drawText("اسم ${terms.scoutSingularDefinite}", 550f, currentY + 16f, thTextPaint)
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
            canvas.drawText("لا توجد ديون مستحقة — جميع ${terms.scoutPluralDefinite} مسددون لالتزاماتهم بالكامل", 297f, currentY + 35f, emptyPaint)
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
        txIncome: Int = 0,
        subsIncome: Int = 0,
        openingBalance: Int = 0,
        totalExpenses: Int,
        netBalance: Int,
        closingBalance: Int = netBalance,
        monthsStats: List<AnnualMonthStat>,
        topCategories: List<Pair<ExpenseCategory, Int>>,
        cairoTypeface: Typeface,
        boldCairo: Typeface,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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
        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 45f, headerPaint)
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

        val breakdownPaint = Paint().apply {
            color = Color.rgb(90, 90, 90)
            textSize = 8.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val breakdownLine = "تفصيل الواردات: الاشتراكات $subsIncome دج + المعاملات الأخرى $txIncome دج  |  الرصيد الافتتاحي: $openingBalance دج  |  الرصيد النهائي: $closingBalance دج"
        canvas.drawText(breakdownLine, 297f, currentY + cardHeight + 14f, breakdownPaint)

        currentY += cardHeight + 28f

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
        canvas.drawText("التقرير السنوي الشامل — ${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 815f, footerPaint)
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
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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
        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 45f, headerPaint)
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
        canvas.drawText("كشف حساب ${terms.scoutSingularDefinite}: ${scout.fullName}", 297f, currentY, titlePaint)
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
        canvas.drawText("اسم ${terms.scoutSingularDefinite}: ${scout.fullName}   |   العمر: $ageStr", 545f, currentY + 20f, infoTextPaint)
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
            canvas.drawText("لا توجد اشتراكات مسجلة لـ${terms.scoutSingularDefinite}", 297f, currentY + 20f, emptyPaint)
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
            canvas.drawText("لا توجد مساهمات مسجلة لـ${terms.scoutSingularDefinite}", 297f, currentY + 20f, emptyPaint)
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
        canvas.drawText("كشف حساب مالي فردي — ${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 815f, footerPaint)
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
        pageRows: List<MonthlyStatementRow>,
        pageIndex: Int,
        totalPages: Int,
        isFirstPage: Boolean,
        openingBalance: Int,
        totalIncome: Int,
        txIncome: Int = 0,
        subsIncome: Int = 0,
        totalExpenses: Int,
        closingBalance: Int,
        cairoTypeface: Typeface,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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

        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 45f, headerPaint)
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

            val breakdownPaint = Paint().apply {
                color = Color.rgb(95, 95, 95)
                textSize = 8.5f
                typeface = cairoTypeface
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "تفصيل الواردات: الاشتراكات $subsIncome دج + المعاملات الأخرى $txIncome دج",
                297f,
                currentY + 83f,
                breakdownPaint
            )

            currentY += 96f
        }

        // مواضع الأعمدة المحدّثة (RTL من 555f إلى 40f)
        val dateRightX = 550f
        val typeRightX = 506f
        val catRightX = 452f
        val descRightX = 366f
        val amountLeftX = 45f

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

        canvas.drawText("التاريخ", dateRightX, currentY + 16f, thTextPaint)
        canvas.drawText("النوع", typeRightX, currentY + 16f, thTextPaint)
        canvas.drawText("التصنيف / المصدر", catRightX, currentY + 16f, thTextPaint)
        canvas.drawText("الوصف", descRightX, currentY + 16f, thTextPaint)
        thTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("المبلغ", amountLeftX, currentY + 16f, thTextPaint)

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
            val textFm = textPaint.fontMetrics
            val amountFm = amountPaint.fontMetrics
            val rowLineHeight = 13f

            fun drawWrappedColumn(lines: List<String>, x: Float, rowTop: Float, rowHeight: Float) {
                val totalH = lines.size * rowLineHeight
                val startBaseline = rowTop + (rowHeight - totalH) / 2f + rowLineHeight / 2f -
                    (textFm.ascent + textFm.descent) / 2f
                lines.forEachIndexed { idx, line ->
                    canvas.drawText(line, x, startBaseline + idx * rowLineHeight, textPaint)
                }
            }

            for ((i, row) in pageRows.withIndex()) {
                val rowTop = currentY
                val rowHeight = row.rowHeight
                val rowBottom = rowTop + rowHeight

                if (i % 2 == 1) {
                    canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBgPaint)
                }
                canvas.drawRect(40f, rowTop, 555f, rowBottom, rowBorderPaint)

                val centerY = rowTop + rowHeight / 2f
                val singleLineTextY = centerY - (textFm.ascent + textFm.descent) / 2f
                val singleLineAmountY = centerY - (amountFm.ascent + amountFm.descent) / 2f

                // Date
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(row.dateStr, dateRightX, singleLineTextY, textPaint)

                // Type (wrapped if needed)
                drawWrappedColumn(row.typeLines, typeRightX, rowTop, rowHeight)

                // Category or Source (wrapped)
                drawWrappedColumn(row.categoryLines, catRightX, rowTop, rowHeight)

                // Description (full multi-line wrapped text, no truncation)
                drawWrappedColumn(row.descriptionLines, descRightX, rowTop, rowHeight)

                // Amount
                amountPaint.color = if (row.isIncome) primaryGreen else Color.rgb(198, 40, 40)
                canvas.drawText(row.amountStr, amountLeftX, singleLineAmountY, amountPaint)

                currentY += rowHeight
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
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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

        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 45f, headerPaint)
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
        canvas.drawText("تقرير مالي صادر عن تطبيق ${terms.groupName} ${terms.unitNameDefinite}", 40f, 818f, footerPaint)
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
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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

        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 45f, headerPaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 65f, subtitlePaint)
        canvas.drawLine(40f, 85f, 555f, 85f, linePaint)

        // عنوان النشاط
        val titleText = if (trainingMeetingNumber != null && trainingMeetingNumber > 0) {
            "قائمة الحضور — الاجتماع التدريبي رقم $trainingMeetingNumber"
        } else {
            "قائمة الحضور — ${activity.title} (${activity.activityType.displayLabel(terms)})"
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

    private fun generateActivityDetailsDocument(
        activity: Activity,
        meetingTopics: List<String>,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoTypeface = getCairoTypeface(context)
        val boldCairo = getBoldCairoTypeface(cairoTypeface)

        val measurePaint = Paint().apply {
            textSize = 9.5f
            typeface = cairoTypeface
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val sections = mutableListOf<ActivityDetailsCardBlock>()

        val objText = activity.objectives.ifBlank { "لم تُحدد أهداف تربوية خاصة لهذا النشاط." }
        val objLines = wrapText(objText, measurePaint, 490f).ifEmpty { listOf(objText) }
        sections.add(ActivityDetailsCardBlock("الأهداف التربوية", objLines))

        val toolsText = activity.tools.ifBlank { "الأدوات الكشفية الاعتيادية." }
        val toolLines = wrapText(toolsText, measurePaint, 490f).ifEmpty { listOf(toolsText) }
        sections.add(ActivityDetailsCardBlock("الأدوات واللوازم", toolLines))

        val nonBlankTopics = meetingTopics.filter { it.isNotBlank() }
        if (nonBlankTopics.isNotEmpty()) {
            val topicLines = nonBlankTopics.flatMapIndexed { idx, topic ->
                wrapText("${idx + 1}. $topic", measurePaint, 490f)
            }
            if (topicLines.isNotEmpty()) {
                sections.add(ActivityDetailsCardBlock("محاور ومواضيع الاجتماع التدريبي", topicLines))
            }
        }

        val notesText = activity.notes.ifBlank { "لا توجد ملاحظات إضافية." }
        val notesLines = wrapText(notesText, measurePaint, 490f).ifEmpty { listOf(notesText) }
        sections.add(ActivityDetailsCardBlock("الملاحظات", notesLines))

        val reportText = activity.finalReport.ifBlank { "لم يُسجل تقرير أدبي ختامي للنشاط بعد." }
        val reportLines = wrapText(reportText, measurePaint, 490f).ifEmpty { listOf(reportText) }
        sections.add(ActivityDetailsCardBlock("التقرير الأدبي وخلاصة النشاط", reportLines))

        val maxContentBottom = 782f
        val page1StartY = 244f // 136f + 98f (معلومات النشاط الأساسية) + 10f
        val nextPageStartY = 136f
        val fullPageMaxLines = ((maxContentBottom - nextPageStartY - 32f) / 14f).toInt().coerceAtLeast(1)

        val pages = mutableListOf<List<ActivityDetailsCardBlock>>()
        var currentPageBlocks = mutableListOf<ActivityDetailsCardBlock>()
        var currentY = page1StartY

        fun startNewPage() {
            pages.add(currentPageBlocks)
            currentPageBlocks = mutableListOf()
            currentY = nextPageStartY
        }

        for (section in sections) {
            var remainingLines = section.lines
            var isContinuation = false

            while (remainingLines.isNotEmpty()) {
                val blockTitle = if (isContinuation) "${section.title} (تتمة)" else section.title
                val remainingHeight = maxContentBottom - currentY
                val maxLinesFit = ((remainingHeight - 32f) / 14f).toInt()

                if (maxLinesFit < 1) {
                    startNewPage()
                    continue
                }

                // تجنّب تقسيم بطاقة قصيرة في نهاية الصفحة إذا كانت تتسع بالكامل في الصفحة التالية
                val canFitOnFreshPage = remainingLines.size <= fullPageMaxLines
                val hasContentAbove = pages.isEmpty() || currentPageBlocks.isNotEmpty()
                if (remainingLines.size > maxLinesFit && canFitOnFreshPage && remainingLines.size <= 6 && hasContentAbove) {
                    startNewPage()
                    continue
                }

                if (remainingLines.size <= maxLinesFit) {
                    currentPageBlocks.add(ActivityDetailsCardBlock(blockTitle, remainingLines))
                    currentY += 32f + remainingLines.size * 14f + 10f
                    remainingLines = emptyList()
                } else {
                    val chunk = remainingLines.take(maxLinesFit)
                    currentPageBlocks.add(ActivityDetailsCardBlock(blockTitle, chunk))
                    remainingLines = remainingLines.drop(maxLinesFit)
                    isContinuation = true
                    startNewPage()
                }
            }
        }

        if (pages.isEmpty() || currentPageBlocks.isNotEmpty()) {
            pages.add(currentPageBlocks)
        }

        val totalPages = maxOf(1, pages.size)
        pages.forEachIndexed { index, blocks ->
            val pageNumber = index + 1
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            drawActivityDetailsPage(
                canvas = page.canvas,
                activity = activity,
                blocks = blocks,
                pageNumber = pageNumber,
                totalPages = totalPages,
                cairoTypeface = cairoTypeface,
                boldCairo = boldCairo,
                context = context,
                terms = terms
            )
            pdfDocument.finishPage(page)
        }

        val pdfFile = File(context.cacheDir, "activity_details_${activity.id}.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    private fun drawActivityDetailsPage(
        canvas: Canvas,
        activity: Activity,
        blocks: List<ActivityDetailsCardBlock>,
        pageNumber: Int,
        totalPages: Int,
        cairoTypeface: Typeface?,
        boldCairo: Typeface?,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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
        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 45f, headerPaint)
        canvas.drawText("الكشافة الإسلامية الجزائرية", 555f, 65f, subtitlePaint)
        canvas.drawLine(40f, 85f, 555f, 85f, linePaint)

        // عنوان البطاقة
        val docHeading = if (pageNumber == 1) "بطاقة تفاصيل النشاط الكشفي" else "بطاقة تفاصيل النشاط الكشفي (تتمة)"
        canvas.drawText(docHeading, 297.5f, 110f, docTitlePaint)
        canvas.drawText("«${activity.title}»", 297.5f, 126f, activityTitlePaint)

        var currentY = 136f

        if (pageNumber == 1) {
            // --- القسم 1: معلومات النشاط الأساسية ---
            val dateFmt = SimpleDateFormat("yyyy/MM/dd", com.example.presentation.common.AppLocale.AR_DZ)
            val startDateStr = if (activity.startDate > 0) dateFmt.format(Date(activity.startDate)) else "—"
            val endDateStr = if (activity.endDate > 0) dateFmt.format(Date(activity.endDate)) else "—"
            val dateDisplay = if (activity.isMultiDay()) "$startDateStr إلى $endDateStr" else startDateStr

            val formattedStartTime = if (activity.startTime.isNotBlank()) {
                com.example.presentation.activity.ActivityFormatters.formatTimeArabic(activity.startTime)
            } else ""
            val formattedEndTime = if (activity.endTime.isNotBlank()) {
                com.example.presentation.activity.ActivityFormatters.formatTimeArabic(activity.endTime)
            } else ""
            val timeDisplay = when {
                formattedStartTime.isNotBlank() && formattedEndTime.isNotBlank() -> "$formattedStartTime - $formattedEndTime"
                formattedStartTime.isNotBlank() -> formattedStartTime
                formattedEndTime.isNotBlank() -> formattedEndTime
                else -> "خلال اليوم"
            }

            val locDisplay = activity.location.ifBlank { "المقر الكشفي" }
            val respDisplay = activity.responsibleName.ifBlank { "قيادة ${terms.groupNameDefinite}" }

            val sec1Height = 98f
            canvas.drawRoundRect(40f, currentY, 555f, currentY + sec1Height, 8f, 8f, cardBgPaint)
            canvas.drawRoundRect(40f, currentY, 555f, currentY + sec1Height, 8f, 8f, cardBorderPaint)

            canvas.drawText("معلومات النشاط الأساسية", 540f, currentY + 16f, sectionHeaderPaint)
            canvas.drawLine(40f, currentY + 22f, 555f, currentY + 22f, separatorPaint)

            canvas.drawText("نوع النشاط: ", 540f, currentY + 38f, labelBoldPaint)
            canvas.drawText(activity.activityType.displayLabel(terms), 470f, currentY + 38f, valuePaint)
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

            canvas.drawText("مدة النشاط: ", 540f, currentY + 86f, labelBoldPaint)
            canvas.drawText("${activity.durationDays()} يوم", 475f, currentY + 86f, valuePaint)

            currentY += sec1Height + 10f
        }

        // رسم البطاقات النصية للصفحة الحالية
        blocks.forEach { block ->
            val cardHeight = 32f + block.lines.size * 14f
            canvas.drawRoundRect(40f, currentY, 555f, currentY + cardHeight, 8f, 8f, cardBgPaint)
            canvas.drawRoundRect(40f, currentY, 555f, currentY + cardHeight, 8f, 8f, cardBorderPaint)

            canvas.drawText(block.title, 540f, currentY + 16f, sectionHeaderPaint)
            canvas.drawLine(40f, currentY + 22f, 555f, currentY + 22f, separatorPaint)

            block.lines.forEachIndexed { lineIdx, line ->
                canvas.drawText(line, 535f, currentY + 36f + lineIdx * 14f, valuePaint)
            }

            currentY += cardHeight + 10f
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
        canvas.drawText(
            "صفحة $pageNumber من $totalPages | ${terms.groupName} ${terms.unitNameDefinite} المعتمد",
            40f,
            808f,
            Paint(footerPaint).apply { textAlign = Paint.Align.LEFT }
        )
    }

    private fun drawTrainingMeetingsMatrixLandscape(
        canvas: Canvas,
        meetings: List<MeetingWithAttendance>,
        allMeetings: List<MeetingWithAttendance>,
        pageScouts: List<Scout>,
        pageNumber: Int,
        totalPages: Int,
        cairoTypeface: Typeface,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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
        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 818f, 32f, headerPaint)

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
        canvas.drawText("${pageStr}${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 24f, 576f, pageInfoPaint)
    }

    private fun truncateText(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }

    private fun drawEmptyRegisterMatrixPage(
        canvas: Canvas,
        cairoTypeface: Typeface,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
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
        canvas.drawText("سجل الاجتماعات التدريبية — ${terms.groupName} ${terms.unitNameDefinite}", 421f, 270f, titlePaint)
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
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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
        drawProgressCardPage1(page1.canvas, scout, badgeProgressList, cairoTypeface, context, terms)
        pdfDocument.finishPage(page1)

        // الصفحة الثانية: شارة الشبل الثاني + شارة الشبل الأول + الاعتماد
        val page2Info = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = pdfDocument.startPage(page2Info)
        drawProgressCardPage2(page2.canvas, scout, badgeProgressList, cairoTypeface, context, terms)
        pdfDocument.finishPage(page2)

        val pdfFile = File(context.cacheDir, "progress_card_${scout.id}.pdf")
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return pdfFile
    }

    fun shareProgressCardPdf(
        file: File,
        scoutName: String,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
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
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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

        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال", 555f, 48f, titlePaint)
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
        canvas.drawText("بطاقة التقدم لشارات الكفاءة (مرحلة ${terms.scoutPluralDefinite})", 297f, 120f, docTitlePaint)

        // قسم معلومات الشبل
        canvas.drawRoundRect(40f, 135f, 555f, 215f, 10f, 10f, boxPaint)
        canvas.drawText("بيانات ${terms.scoutSingularDefinite} ومستوى التقدم", 540f, 155f, headerPaint)
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
        drawBadgeBox(canvas, "شارة ${terms.scoutSingularDefinite} المبتدئ", badge2, currentY + 15f, 260f, typeface, boldCairo)

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
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
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
        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite} — فوج محمد رحال (بطاقة التقدم: ${scout.fullName})", 555f, 45f, headerPaint)
        canvas.drawLine(40f, 55f, 555f, 55f, linePaint)

        // رسم شارة 3: الثاني وشارة 4: الأول
        var currentY = 70f
        val badge3 = badgeProgressList.firstOrNull { it.level == BadgeLevel.SECOND }
        currentY = drawBadgeBox(canvas, "شارة ${terms.scoutSingularDefinite} الثاني", badge3, currentY, 260f, typeface, boldCairo)

        val badge4 = badgeProgressList.firstOrNull { it.level == BadgeLevel.FIRST }
        currentY = drawBadgeBox(canvas, "شارة ${terms.scoutSingularDefinite} الأول", badge4, currentY + 15f, 260f, typeface, boldCairo)

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

        canvas.drawText("توقيع قائد ${terms.groupNameDefinite}:", 540f, signBoxTop + 50f, textPaint)
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
        val overallAverage: Double? = null,
        val leaderScoresById: Map<String, Double> = emptyMap()
    )

    private data class PreparedRawPointRow(
        val globalIndex: Int,
        val item: ScoutRawPoints,
        val nameLines: List<String>,
        val rowHeight: Float
    )

    private data class DynamicTableColumn(
        val title: String,
        val width: Float,
        val isLeaderColumn: Boolean = false,
        val leaderId: String? = null
    )

    private data class PreparedTableHeader(
        val column: DynamicTableColumn,
        val lines: List<String>,
        val fontSize: Float
    )

    fun exportRawPointsPdf(
        scoutPointsList: List<ScoutRawPoints>,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms(),
        evaluatedLeaders: List<Leader> = emptyList(),
        leaderNames: Map<String, String> = emptyMap(),
        termLabel: String? = null
    ): File {
        val pdfDocument = PdfDocument()
        val cairoFont = getCairoTypeface(context)
        val cairoBold = getBoldCairoTypeface(cairoFont)

        val pageWidth = 595
        val pageHeight = 842

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        val rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F9F9F9")
            style = Paint.Style.FILL
        }

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9f
            typeface = cairoBold
            textAlign = Paint.Align.RIGHT
        }

        // عرض الجدول الكلي مع هوامش جانبية (15pt لكل جانب = 30pt إجمالي الهوامش)
        val leftMargin = 15f
        val totalTableWidth = pageWidth - leftMargin * 2f // 565f

        // الأعمدة الثابتة فقط (المجموع = 305pt)
        val rankWidth = 30f
        val nameWidth = 120f
        val attendanceWidth = 55f
        val quranWidth = 50f
        val notebookWidth = 50f
        val fixedTotalWidth = rankWidth + nameWidth + attendanceWidth + quranWidth + notebookWidth // 305f

        val remainingLeaderWidth = (totalTableWidth - fixedTotalWidth).coerceAtLeast(60f) // 260f

        val leaderColumns: List<DynamicTableColumn> = if (evaluatedLeaders.isNotEmpty()) {
            val singleLeaderWidth = remainingLeaderWidth / evaluatedLeaders.size.toFloat()
            evaluatedLeaders.map { leader ->
                val displayName = leader.fullName.takeIf { it.isNotBlank() }
                    ?: leaderNames[leader.uid]?.takeIf { it.isNotBlank() }
                    ?: "قائد"
                DynamicTableColumn(
                    title = displayName,
                    width = singleLeaderWidth,
                    isLeaderColumn = true,
                    leaderId = leader.uid
                )
            }
        } else {
            listOf(
                DynamicTableColumn(
                    title = "تقييم القادة",
                    width = remainingLeaderWidth,
                    isLeaderColumn = true,
                    leaderId = null
                )
            )
        }

        // ترتيب الأعمدة RTL من اليمين إلى اليسار (الرقم -> الاسم -> الحضور -> القادة -> القرآن -> الكراس)
        val columns = buildList {
            add(DynamicTableColumn("الرقم", rankWidth))
            add(DynamicTableColumn("اسم ${terms.scoutSingularDefinite}", nameWidth))
            add(DynamicTableColumn("تقييم الحضور", attendanceWidth))
            addAll(leaderColumns)
            add(DynamicTableColumn("حفظ القرآن", quranWidth))
            add(DynamicTableColumn("تقييم الكراس", notebookWidth))
        }

        val preparedHeaders = columns.map { col ->
            val baseFontSize = when {
                !col.isLeaderColumn -> 8f
                col.width >= 55f -> 8f
                col.width >= 40f -> 7.5f
                else -> 6.5f
            }
            val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = baseFontSize
                typeface = cairoBold
                textAlign = Paint.Align.CENTER
            }
            val maxCellTextWidth = (col.width - 4f).coerceAtLeast(10f)
            val lines = wrapText(col.title, measurePaint, maxCellTextWidth).ifEmpty { listOf(col.title) }
            val widestLine = lines.maxOfOrNull { measurePaint.measureText(it) } ?: 0f
            val fittedFontSize = if (widestLine > maxCellTextWidth && widestLine > 0f) {
                (baseFontSize * (maxCellTextWidth / widestLine)).coerceIn(5.5f, baseFontSize)
            } else {
                baseFontSize
            }
            PreparedTableHeader(
                column = col,
                lines = lines,
                fontSize = fittedFontSize
            )
        }

        val headerLineStep = 9.5f
        val maxHeaderLines = preparedHeaders.maxOfOrNull { it.lines.size } ?: 1
        val headerHeight = maxOf(30f, maxHeaderLines * headerLineStep + 10f)

        val nameLineStep = 11.5f
        val preparedRows = scoutPointsList.mapIndexed { idx, item ->
            val nameLines = wrapText(item.scoutName, namePaint, nameWidth - 10f)
                .ifEmpty { listOf(item.scoutName) }
            val nameBlockHeight = nameLines.size * nameLineStep + 10f
            val rowHeight = maxOf(24f, nameBlockHeight)

            PreparedRawPointRow(
                globalIndex = idx + 1,
                item = item,
                nameLines = nameLines,
                rowHeight = rowHeight
            )
        }

        // تقسيم الصفوف ديناميكياً على الصفحات حسب ارتفاع كل صف
        val tableTop = 115f
        val dataStartY = tableTop + headerHeight
        val contentBottomY = 805f

        val pages = mutableListOf<List<PreparedRawPointRow>>()
        var currentPage = mutableListOf<PreparedRawPointRow>()
        var accY = dataStartY
        for (row in preparedRows) {
            if (accY + row.rowHeight > contentBottomY && currentPage.isNotEmpty()) {
                pages.add(currentPage)
                currentPage = mutableListOf()
                accY = dataStartY
            }
            currentPage.add(row)
            accY += row.rowHeight
        }
        if (currentPage.isNotEmpty() || pages.isEmpty()) {
            pages.add(currentPage)
        }
        val totalPages = pages.size

        val nameFm = namePaint.fontMetrics
        val leaderCellWidth = leaderColumns.firstOrNull()?.width ?: remainingLeaderWidth
        val leaderScoreTextSize = when {
            leaderCellWidth >= 48f -> 8.5f
            leaderCellWidth >= 34f -> 8f
            else -> 7f
        }

        pages.forEachIndexed { pageIndex, pageRows ->
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
            canvas.drawText("فوج محمد رحال — ${terms.groupName} ${terms.unitNameDefinite} لـ${terms.scoutPluralDefinite}", pageWidth / 2f, yPos, nationalPaint)
            yPos += 20f

            drawLogo(canvas, context, left = leftMargin, top = 25f, targetWidth = 45f, targetHeight = 45f)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1B5E20")
                textSize = 15f
                typeface = cairoBold
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("سجل تقييم ${terms.scoutPluralDefinite} (نقاط)", pageWidth / 2f, yPos, titlePaint)
            yPos += 14f

            val sdf = SimpleDateFormat("dd/MM/yyyy", com.example.presentation.common.AppLocale.AR_DZ)
            val termPart = if (!termLabel.isNullOrBlank()) "$termLabel | " else ""
            val subInfo = "${termPart}تاريخ التقرير: ${sdf.format(Date())} | إجمالي ${terms.scoutPluralDefinite}: ${scoutPointsList.size}"
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 8.5f
                typeface = cairoFont
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(subInfo, pageWidth / 2f, yPos, subPaint)

            // رسم جدول النقاط الخام (RTL من اليمين لليسار)
            val startX = leftMargin + totalTableWidth

            // ترويسة الجدول
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E8F5E9")
                style = Paint.Style.FILL
            }
            canvas.drawRect(leftMargin, tableTop, leftMargin + totalTableWidth, tableTop + headerHeight, headerBgPaint)
            canvas.drawRect(leftMargin, tableTop, leftMargin + totalTableWidth, tableTop + headerHeight, borderPaint)

            var currentX = startX
            preparedHeaders.forEachIndexed { colIndex, header ->
                val w = header.column.width
                currentX -= w

                val cellHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (header.column.isLeaderColumn) Color.parseColor("#1B5E20") else Color.BLACK
                    textSize = header.fontSize
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                val hFm = cellHeaderPaint.fontMetrics
                val totalHeaderTextH = header.lines.size * headerLineStep
                val hStartY = tableTop + (headerHeight - totalHeaderTextH) / 2f + headerLineStep / 2f - (hFm.ascent + hFm.descent) / 2f
                header.lines.forEachIndexed { lineIdx, line ->
                    canvas.drawText(line, currentX + w / 2f, hStartY + lineIdx * headerLineStep, cellHeaderPaint)
                }
                if (colIndex < preparedHeaders.lastIndex) {
                    canvas.drawLine(currentX, tableTop, currentX, tableTop + headerHeight, borderPaint)
                }
            }

            var currentY = dataStartY

            pageRows.forEachIndexed { idx, row ->
                val item = row.item
                val rowH = row.rowHeight
                if (idx % 2 == 1) {
                    canvas.drawRect(leftMargin, currentY, leftMargin + totalTableWidth, currentY + rowH, rowBgPaint)
                }
                canvas.drawRect(leftMargin, currentY, leftMargin + totalTableWidth, currentY + rowH, borderPaint)

                var xCell = startX

                // 1) الرقم
                xCell -= rankWidth
                val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 9f
                    typeface = cairoFont
                    textAlign = Paint.Align.CENTER
                }
                val cFm = centerPaint.fontMetrics
                val centerTextY = currentY + rowH / 2f - (cFm.ascent + cFm.descent) / 2f
                canvas.drawText(row.globalIndex.toString(), xCell + rankWidth / 2f, centerTextY, centerPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowH, borderPaint)

                // 2) اسم الشبل (ملتف وموسّط عمودياً)
                xCell -= nameWidth
                val nameTotalH = row.nameLines.size * nameLineStep
                val nameStartY = currentY + (rowH - nameTotalH) / 2f + nameLineStep / 2f - (nameFm.ascent + nameFm.descent) / 2f
                row.nameLines.forEachIndexed { lineIdx, line ->
                    canvas.drawText(line, xCell + nameWidth - 6f, nameStartY + lineIdx * nameLineStep, namePaint)
                }
                canvas.drawLine(xCell, currentY, xCell, currentY + rowH, borderPaint)

                // 3) تقييم الحضور (نقاط من 20)
                xCell -= attendanceWidth
                val attendanceText = formatOutOfTwenty(item.attendanceScore)
                val attColor = if (item.attendanceScore != null) Color.parseColor("#2E7D32") else Color.GRAY
                val attPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = attColor
                    textSize = 8.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                val attFm = attPaint.fontMetrics
                canvas.drawText(attendanceText, xCell + attendanceWidth / 2f, currentY + rowH / 2f - (attFm.ascent + attFm.descent) / 2f, attPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowH, borderPaint)

                // 4) الأعمدة الديناميكية للقادة (عمود مستقل لكل قائد قام بالتقييم، أو العمود البديل عند N == 0)
                if (evaluatedLeaders.isNotEmpty()) {
                    leaderColumns.forEach { leaderCol ->
                        val colW = leaderCol.width
                        xCell -= colW
                        val score = leaderCol.leaderId?.let { item.leaderScoresById[it] }
                        val scoreText = if (score != null) {
                            String.format(Locale.US, "%.1f", score.coerceAtLeast(0.0))
                        } else {
                            "-"
                        }
                        val leaderScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (score != null) Color.parseColor("#1B5E20") else Color.GRAY
                            textSize = leaderScoreTextSize
                            typeface = cairoBold
                            textAlign = Paint.Align.CENTER
                        }
                        val lsFm = leaderScorePaint.fontMetrics
                        canvas.drawText(
                            scoreText,
                            xCell + colW / 2f,
                            currentY + rowH / 2f - (lsFm.ascent + lsFm.descent) / 2f,
                            leaderScorePaint
                        )
                        canvas.drawLine(xCell, currentY, xCell, currentY + rowH, borderPaint)
                    }
                } else {
                    val fallbackColW = leaderColumns.first().width
                    xCell -= fallbackColW
                    val fallbackText = formatOutOfTwenty(item.leaderPoints)
                    val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (item.leaderPoints != null) Color.parseColor("#1B5E20") else Color.GRAY
                        textSize = 9f
                        typeface = cairoBold
                        textAlign = Paint.Align.CENTER
                    }
                    val fbFm = fallbackPaint.fontMetrics
                    canvas.drawText(
                        fallbackText,
                        xCell + fallbackColW / 2f,
                        currentY + rowH / 2f - (fbFm.ascent + fbFm.descent) / 2f,
                        fallbackPaint
                    )
                    canvas.drawLine(xCell, currentY, xCell, currentY + rowH, borderPaint)
                }

                // 5) تقييم حفظ القرآن (نقاط من 20)
                xCell -= quranWidth
                val quranText = formatOutOfTwenty(item.quranPoints)
                val quranColor = if (item.quranPoints != null) Color.parseColor("#0D47A1") else Color.GRAY
                val quranPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = quranColor
                    textSize = 8.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                val qFm = quranPaint.fontMetrics
                canvas.drawText(quranText, xCell + quranWidth / 2f, currentY + rowH / 2f - (qFm.ascent + qFm.descent) / 2f, quranPaint)
                canvas.drawLine(xCell, currentY, xCell, currentY + rowH, borderPaint)

                // 6) تقييم الكراس (نقاط من 20 - العمود الأخير أقصى اليسار)
                xCell -= notebookWidth
                val notebookText = formatOutOfTwenty(item.notebookPoints)
                val notebookColor = if (item.notebookPoints != null) Color.parseColor("#E65100") else Color.GRAY
                val notebookPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = notebookColor
                    textSize = 8.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                val nbFm = notebookPaint.fontMetrics
                canvas.drawText(notebookText, xCell + notebookWidth / 2f, currentY + rowH / 2f - (nbFm.ascent + nbFm.descent) / 2f, notebookPaint)

                currentY += rowH
            }

            // أسفل الصفحة
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 8.5f
                typeface = cairoFont
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("صفحة ${pageIndex + 1} من $totalPages — ${terms.groupName} ${terms.unitNameDefinite} لـ${terms.scoutPluralDefinite}", pageWidth / 2f, pageHeight - 20f, footerPaint)

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

    fun shareRawPointsPdf(
        file: File,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "سجل تقييم ${terms.scoutPluralDefinite} (نقاط) — ${terms.groupName} ${terms.unitNameDefinite}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة سجل تقييم ${terms.scoutPluralDefinite} كـ PDF"))
    }

    // =========================================================================
    // تقارير الحزمة العامة (General Reports PDF Exporters)
    // =========================================================================

    data class ActivitySummary(
        val title: String,
        val type: String,
        val date: String,
        val presentCount: Int,
        val absentCount: Int,
        val attendancePercentage: Double
    )

    private fun sharePdfFile(file: File, subject: String, chooserTitle: String, context: Context) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun shareGeneralAttendancePdf(
        file: File,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        sharePdfFile(file, "تقرير الحضور الشامل — ${terms.groupName} ${terms.unitNameDefinite}", "مشاركة تقرير الحضور الشامل كـ PDF", context)
    }

    fun shareActivitiesReportPdf(
        file: File,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        sharePdfFile(file, "تقرير النشاط العام — ${terms.groupName} ${terms.unitNameDefinite}", "مشاركة تقرير النشاط العام كـ PDF", context)
    }

    fun shareProgressReportPdf(
        file: File,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        sharePdfFile(file, "تقرير التقدم الشامل — ${terms.groupName} ${terms.unitNameDefinite}", "مشاركة تقرير التقدم الشامل كـ PDF", context)
    }

    fun shareLeadersEvaluationPdf(
        file: File,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        sharePdfFile(file, "تقرير تقييم القادة — ${terms.groupName} ${terms.unitNameDefinite}", "مشاركة تقرير تقييم القادة كـ PDF", context)
    }

    private fun drawScoutPdfHeader(
        canvas: Canvas,
        pageWidth: Int,
        title: String,
        subInfo: String,
        cairoFont: Typeface?,
        cairoBold: Typeface?,
        context: Context,
        isLandscape: Boolean,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val leftMargin = 24f
        val rightMargin = (pageWidth - 24).toFloat()

        // شعار الفوج
        drawLogo(canvas, context, left = leftMargin, top = 16f, targetWidth = 42f, targetHeight = 42f)

        // ترويسة وطنية وكشفية في الوسط
        var yPos = 22f
        val nationalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 9.5f
            typeface = cairoFont
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("الجمهورية الجزائرية الديمقراطية الشعبية", pageWidth / 2f, yPos, nationalPaint)
        yPos += 13f
        canvas.drawText("الكشافة الإسلامية الجزائرية — المحافظة الولائية لسطيف", pageWidth / 2f, yPos, nationalPaint)
        yPos += 13f
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(46, 125, 50)
            textSize = 10f
            typeface = cairoBold
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("فوج محمد رحال — ${terms.groupName} ${terms.unitNameDefinite} لـ${terms.scoutPluralDefinite}", pageWidth / 2f, yPos, unitPaint)
        yPos += 18f

        // عنوان التقرير البارز
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(27, 94, 32)
            textSize = if (isLandscape) 16f else 15f
            typeface = cairoBold
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, pageWidth / 2f, yPos, titlePaint)
        yPos += 14f

        // معلومات التاريخ والإحصاء
        if (subInfo.isNotBlank()) {
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 8.5f
                typeface = cairoFont
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(subInfo, pageWidth / 2f, yPos, subPaint)
            yPos += 10f
        }

        // خط فاصل ذهبي
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 169, 81)
            strokeWidth = 1.2f
        }
        canvas.drawLine(leftMargin, yPos, rightMargin, yPos, dividerPaint)
    }

    private fun drawScoutPdfFooter(
        canvas: Canvas,
        pageWidth: Int,
        pageHeight: Int,
        currentPage: Int,
        totalPages: Int,
        cairoFont: Typeface?,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 8.5f
            typeface = cairoFont
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "صفحة $currentPage من $totalPages — ${terms.groupName} ${terms.unitNameDefinite} لـ${terms.scoutPluralDefinite} (فوج محمد رحال سطيف)",
            pageWidth / 2f,
            pageHeight - 16f,
            footerPaint
        )
    }

    /**
     * 1) تقرير الحضور الشامل (مصفوفة A4 أفقي)
     */
    fun exportGeneralAttendancePdf(
        scouts: List<Scout>,
        activities: List<Activity>,
        attendanceMap: Map<String, Map<String, String>>,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoFont = getCairoTypeface(context) ?: Typeface.DEFAULT
        val cairoBold = getBoldCairoTypeface(cairoFont) ?: Typeface.DEFAULT_BOLD

        val pageWidth = 842
        val pageHeight = 595
        val leftMargin = 24f
        val rightMargin = 818f
        val totalAvailableWidth = rightMargin - leftMargin

        val activeScouts = scouts.filter { it.isActive }
        val rowsPerPage = 16
        val maxActivitiesPerPage = 6
        val activityChunks = if (activities.isNotEmpty()) activities.chunked(maxActivitiesPerPage) else listOf(emptyList())
        val scoutChunks = if (activeScouts.isNotEmpty()) activeScouts.chunked(rowsPerPage) else listOf(emptyList())
        val totalPages = maxOf(1, activityChunks.size * scoutChunks.size)

        val nameColWidth = 140f
        val totalColWidth = 55f
        val pctColWidth = 55f

        val sdf = SimpleDateFormat("dd/MM/yyyy", com.example.presentation.common.AppLocale.AR_DZ)
        val shortDateFormat = SimpleDateFormat("dd/MM", com.example.presentation.common.AppLocale.AR_DZ)

        var pageNumber = 1
        for (activityChunk in activityChunks) {
            val actCount = activityChunk.size
            val availableActWidth = totalAvailableWidth - nameColWidth - totalColWidth - pctColWidth
            val actColWidth = if (actCount > 0) availableActWidth / actCount else 0f
            val tableWidth = nameColWidth + (actColWidth * actCount) + totalColWidth + pctColWidth
            val tableLeft = leftMargin + (totalAvailableWidth - tableWidth) / 2f
            val tableRight = tableLeft + tableWidth

            for (scoutChunk in scoutChunks) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val subInfo = "تاريخ التقرير: ${sdf.format(Date())} | إجمالي ${terms.scoutPluralDefinite}: ${activeScouts.size} | إجمالي الأنشطة: ${activities.size}"
                drawScoutPdfHeader(canvas, pageWidth, "تقرير الحضور الشامل", subInfo, cairoFont, cairoBold, context, isLandscape = true, terms = terms)

                val tableTop = 86f
                val headerHeight = 36f
                val rowHeight = 24f

                val headerBg = Paint().apply {
                    color = Color.rgb(232, 245, 233)
                    style = Paint.Style.FILL
                }
                val borderPaint = Paint().apply {
                    color = Color.rgb(200, 214, 201)
                    style = Paint.Style.STROKE
                    strokeWidth = 0.8f
                }
                canvas.drawRect(tableLeft, tableTop, tableRight, tableTop + headerHeight, headerBg)
                canvas.drawRect(tableLeft, tableTop, tableRight, tableTop + headerHeight, borderPaint)

                var currentX = tableRight

                // ترويسة اسم الشبل
                val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(27, 94, 32)
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                currentX -= nameColWidth
                canvas.drawText("الاسم واللقب", currentX + nameColWidth / 2f, tableTop + 22f, thPaint)
                canvas.drawLine(currentX, tableTop, currentX, tableTop + headerHeight, borderPaint)

                // ترويسات الأنشطة
                val actTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(27, 94, 32)
                    textSize = 8.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                val actDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 8f
                    typeface = cairoFont
                    textAlign = Paint.Align.CENTER
                }
                for (act in activityChunk) {
                    currentX -= actColWidth
                    val actDateStr = if (act.startDate > 0) shortDateFormat.format(Date(act.startDate)) else "—"
                    val truncatedTitle = truncateText(act.title, actColWidth - 6f, actTitlePaint)
                    canvas.drawText(truncatedTitle, currentX + actColWidth / 2f, tableTop + 15f, actTitlePaint)
                    canvas.drawText(actDateStr, currentX + actColWidth / 2f, tableTop + 28f, actDatePaint)
                    canvas.drawLine(currentX, tableTop, currentX, tableTop + headerHeight, borderPaint)
                }

                // ترويسة المجموع
                currentX -= totalColWidth
                canvas.drawText("المجموع", currentX + totalColWidth / 2f, tableTop + 22f, thPaint)
                canvas.drawLine(currentX, tableTop, currentX, tableTop + headerHeight, borderPaint)

                // ترويسة النسبة
                currentX -= pctColWidth
                canvas.drawText("النسبة", currentX + pctColWidth / 2f, tableTop + 22f, thPaint)

                // صفوف بيانات الأشبال
                var currentY = tableTop + headerHeight
                scoutChunk.forEachIndexed { rIdx, scout ->
                    val isEven = rIdx % 2 == 0
                    val rowBgColor = if (isEven) Color.WHITE else Color.rgb(248, 249, 250)
                    val rowBg = Paint().apply { color = rowBgColor; style = Paint.Style.FILL }
                    canvas.drawRect(tableLeft, currentY, tableRight, currentY + rowHeight, rowBg)
                    canvas.drawRect(tableLeft, currentY, tableRight, currentY + rowHeight, borderPaint)

                    var rowX = tableRight

                    // 1) الاسم
                    rowX -= nameColWidth
                    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 9f
                        typeface = cairoBold
                        textAlign = Paint.Align.RIGHT
                    }
                    val scoutName = truncateText(scout.fullName, nameColWidth - 14f, namePaint)
                    canvas.drawText(scoutName, tableRight - 8f, currentY + 16f, namePaint)
                    canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                    // 2) خلايا الأنشطة
                    val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 8.5f
                        typeface = cairoBold
                        textAlign = Paint.Align.CENTER
                    }
                    for (act in activityChunk) {
                        rowX -= actColWidth
                        val status = attendanceMap[scout.id]?.get(act.id).orEmpty()
                        val (label, textColor) = when {
                            status.contains("present", true) || status.contains("حاضر") -> "حاضر" to Color.rgb(46, 125, 50)
                            status.contains("very_late", true) || status.contains("جداً") -> "متأخر+" to Color.rgb(230, 74, 25)
                            status.contains("late", true) || status.contains("متأخر") -> "متأخر" to Color.rgb(245, 124, 0)
                            status.contains("excused", true) || status.contains("عذر") -> "بعذر" to Color.rgb(251, 140, 0)
                            status.contains("absent", true) || status.contains("غائب") -> "غائب" to Color.rgb(211, 47, 47)
                            else -> "—" to Color.GRAY
                        }
                        statusPaint.color = textColor
                        canvas.drawText(label, rowX + actColWidth / 2f, currentY + 16f, statusPaint)
                        canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)
                    }

                    // حساب الحضور الكلي والنسبة
                    val scoutMap = attendanceMap[scout.id].orEmpty()
                    val totalAttended = activities.count { act ->
                        val st = scoutMap[act.id].orEmpty()
                        st.contains("present", true) || st.contains("late", true) || st.contains("حاضر") || st.contains("متأخر")
                    }
                    val pct = if (activities.isNotEmpty()) (totalAttended.toDouble() * 100.0 / activities.size) else 0.0

                    // 3) المجموع
                    rowX -= totalColWidth
                    val totalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(27, 94, 32)
                        textSize = 9.5f
                        typeface = cairoBold
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("$totalAttended", rowX + totalColWidth / 2f, currentY + 16f, totalTextPaint)
                    canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                    // 4) النسبة
                    rowX -= pctColWidth
                    val pctText = String.format(Locale.US, "%.0f%%", pct)
                    canvas.drawText(pctText, rowX + pctColWidth / 2f, currentY + 16f, totalTextPaint)

                    currentY += rowHeight
                }

                // دليل الرموز في الأسفل
                val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 8f
                    typeface = cairoFont
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(
                    "دليل الرموز: حاضر (أخضر) | متأخر (برتقالي) | غائب بعذر (كهرماني) | غائب بدون عذر (أحمر)",
                    pageWidth / 2f,
                    pageHeight - 34f,
                    legendPaint
                )

                drawScoutPdfFooter(canvas, pageWidth, pageHeight, pageNumber, totalPages, cairoFont, terms = terms)

                pdfDocument.finishPage(page)
                pageNumber++
            }
        }

        val file = File(context.cacheDir, "general_attendance_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return file
    }

    /**
     * 2) تقرير النشاط العام (قائمة جدولية A4 عمودي)
     */
    fun exportActivitiesReportPdf(
        activities: List<ActivitySummary>,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoFont = getCairoTypeface(context) ?: Typeface.DEFAULT
        val cairoBold = getBoldCairoTypeface(cairoFont) ?: Typeface.DEFAULT_BOLD

        val pageWidth = 595
        val pageHeight = 842
        val leftMargin = 25f
        val rightMargin = 570f
        val tableWidth = 545f

        val rowsPerPage = 20
        val activityChunks = if (activities.isNotEmpty()) activities.chunked(rowsPerPage) else listOf(emptyList())
        val totalPages = maxOf(1, activityChunks.size)

        val sdf = SimpleDateFormat("dd/MM/yyyy", com.example.presentation.common.AppLocale.AR_DZ)

        // حسابات الأعمدة (عرض كلي 545f من اليمين إلى اليسار)
        val colNameWidth = 165f
        val colTypeWidth = 80f
        val colDateWidth = 75f
        val colPresWidth = 75f
        val colAbsWidth = 75f
        val colRateWidth = 75f

        activityChunks.forEachIndexed { pageIndex, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val subInfo = "تاريخ التقرير: ${sdf.format(Date())} | إجمالي الأنشطة المنجزة: ${activities.size}"
            drawScoutPdfHeader(canvas, pageWidth, "تقرير النشاط العام", subInfo, cairoFont, cairoBold, context, isLandscape = false, terms = terms)

            var tableTop = 86f

            // في الصفحة الأولى، رسم بطاقة ملخص إحصائي أنيقة
            if (pageIndex == 0 && activities.isNotEmpty()) {
                val totalAtt = activities.sumOf { it.presentCount }
                val avgRate = activities.map { it.attendancePercentage }.average()

                val summaryRect = RectF(leftMargin, tableTop, rightMargin, tableTop + 36f)
                val summaryBg = Paint().apply {
                    color = Color.rgb(241, 248, 233)
                    style = Paint.Style.FILL
                }
                val summaryBorder = Paint().apply {
                    color = Color.rgb(200, 230, 201)
                    style = Paint.Style.STROKE
                    strokeWidth = 0.8f
                }
                canvas.drawRoundRect(summaryRect, 6f, 6f, summaryBg)
                canvas.drawRoundRect(summaryRect, 6f, 6f, summaryBorder)

                val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(27, 94, 32)
                    textSize = 9f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                val statValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }

                val thirdW = tableWidth / 3f
                // بطاقة 1: عدد الأنشطة
                canvas.drawText("إجمالي الأنشطة: ${activities.size}", leftMargin + thirdW * 2.5f, tableTop + 22f, statValPaint)
                // بطاقة 2: مجموع الحضور
                canvas.drawText("مجموع الحاضرين: $totalAtt", leftMargin + thirdW * 1.5f, tableTop + 22f, statValPaint)
                // بطاقة 3: متوسط النسبة
                val avgFormatted = String.format(Locale.US, "%.1f%%", avgRate)
                canvas.drawText("متوسط نسبة الحضور: $avgFormatted", leftMargin + thirdW * 0.5f, tableTop + 22f, statPaint)

                tableTop += 46f
            }

            val headerHeight = 28f
            val rowHeight = 24f

            val headerBg = Paint().apply {
                color = Color.rgb(232, 245, 233)
                style = Paint.Style.FILL
            }
            val borderPaint = Paint().apply {
                color = Color.rgb(200, 214, 201)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            canvas.drawRect(leftMargin, tableTop, rightMargin, tableTop + headerHeight, headerBg)
            canvas.drawRect(leftMargin, tableTop, rightMargin, tableTop + headerHeight, borderPaint)

            var curX = rightMargin

            val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(27, 94, 32)
                textSize = 9f
                typeface = cairoBold
                textAlign = Paint.Align.CENTER
            }

            // 1) اسم النشاط
            curX -= colNameWidth
            canvas.drawText("اسم النشاط", curX + colNameWidth / 2f, tableTop + 18f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 2) نوع النشاط
            curX -= colTypeWidth
            canvas.drawText("نوع النشاط", curX + colTypeWidth / 2f, tableTop + 18f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 3) التاريخ
            curX -= colDateWidth
            canvas.drawText("التاريخ", curX + colDateWidth / 2f, tableTop + 18f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 4) عدد الحاضرين
            curX -= colPresWidth
            canvas.drawText("الحاضرين", curX + colPresWidth / 2f, tableTop + 18f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 5) عدد الغائبين
            curX -= colAbsWidth
            canvas.drawText("الغائبين", curX + colAbsWidth / 2f, tableTop + 18f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 6) نسبة الحضور
            curX -= colRateWidth
            canvas.drawText("نسبة الحضور", curX + colRateWidth / 2f, tableTop + 18f, thPaint)

            // صفوف الأنشطة
            var currentY = tableTop + headerHeight
            chunk.forEachIndexed { rIdx, item ->
                val isEven = rIdx % 2 == 0
                val rowBgColor = if (isEven) Color.WHITE else Color.rgb(248, 249, 250)
                val rowBg = Paint().apply { color = rowBgColor; style = Paint.Style.FILL }
                canvas.drawRect(leftMargin, currentY, rightMargin, currentY + rowHeight, rowBg)
                canvas.drawRect(leftMargin, currentY, rightMargin, currentY + rowHeight, borderPaint)

                var rowX = rightMargin

                // 1) اسم النشاط
                rowX -= colNameWidth
                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 9f
                    typeface = cairoBold
                    textAlign = Paint.Align.RIGHT
                }
                val truncatedTitle = truncateText(item.title, colNameWidth - 14f, titlePaint)
                canvas.drawText(truncatedTitle, rightMargin - 8f, currentY + 16f, titlePaint)
                canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                // 2) نوع النشاط
                rowX -= colTypeWidth
                val typePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 8.5f
                    typeface = cairoFont
                    textAlign = Paint.Align.CENTER
                }
                val truncatedType = truncateText(item.type, colTypeWidth - 8f, typePaint)
                canvas.drawText(truncatedType, rowX + colTypeWidth / 2f, currentY + 16f, typePaint)
                canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                // 3) التاريخ
                rowX -= colDateWidth
                val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 8.5f
                    typeface = cairoFont
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(item.date, rowX + colDateWidth / 2f, currentY + 16f, datePaint)
                canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                // 4) عدد الحاضرين
                rowX -= colPresWidth
                val presPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(46, 125, 50)
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("${item.presentCount}", rowX + colPresWidth / 2f, currentY + 16f, presPaint)
                canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                // 5) عدد الغائبين
                rowX -= colAbsWidth
                val absPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(211, 47, 47)
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("${item.absentCount}", rowX + colAbsWidth / 2f, currentY + 16f, absPaint)
                canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                // 6) نسبة الحضور
                rowX -= colRateWidth
                val ratePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(27, 94, 32)
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                val rateStr = String.format(Locale.US, "%.0f%%", item.attendancePercentage)
                canvas.drawText(rateStr, rowX + colRateWidth / 2f, currentY + 16f, ratePaint)

                currentY += rowHeight
            }

            drawScoutPdfFooter(canvas, pageWidth, pageHeight, pageIndex + 1, totalPages, cairoFont, terms = terms)

            pdfDocument.finishPage(page)
        }

        val file = File(context.cacheDir, "activities_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return file
    }

    /**
     * 3) تقرير التقدم الشامل (مصفوفة شارات الكفاءة والهواية A4 أفقي)
     */
    fun exportProgressReportPdf(
        scouts: List<Scout>,
        badgeMap: Map<String, List<String>>,
        hobbyCountMap: Map<String, Int>,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoFont = getCairoTypeface(context) ?: Typeface.DEFAULT
        val cairoBold = getBoldCairoTypeface(cairoFont) ?: Typeface.DEFAULT_BOLD

        val pageWidth = 842
        val pageHeight = 595
        val leftMargin = 24f
        val rightMargin = 818f
        val tableWidth = rightMargin - leftMargin // 794f

        val activeScouts = scouts.filter { it.isActive }
        val rowsPerPage = 16
        val scoutChunks = if (activeScouts.isNotEmpty()) activeScouts.chunked(rowsPerPage) else listOf(emptyList())
        val totalPages = maxOf(1, scoutChunks.size)

        // توزيع الأعمدة (المجموع 794f من اليمين إلى اليسار)
        val colNameWidth = 174f
        val colAcceptWidth = 120f
        val colBeginWidth = 120f
        val colSecondWidth = 120f
        val colFirstWidth = 120f
        val colHobbyWidth = 140f

        val sdf = SimpleDateFormat("dd/MM/yyyy", com.example.presentation.common.AppLocale.AR_DZ)

        scoutChunks.forEachIndexed { pageIndex, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val totalHobbies = activeScouts.sumOf { hobbyCountMap[it.id] ?: 0 }
            val subInfo = "تاريخ التقرير: ${sdf.format(Date())} | إجمالي ${terms.scoutPluralDefinite}: ${activeScouts.size} | إجمالي شارات الهواية الممنوحة: $totalHobbies"
            drawScoutPdfHeader(canvas, pageWidth, "تقرير التقدم الشامل", subInfo, cairoFont, cairoBold, context, isLandscape = true, terms = terms)

            val tableTop = 86f
            val headerHeight = 32f
            val rowHeight = 24f

            val headerBg = Paint().apply {
                color = Color.rgb(232, 245, 233)
                style = Paint.Style.FILL
            }
            val borderPaint = Paint().apply {
                color = Color.rgb(200, 214, 201)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            canvas.drawRect(leftMargin, tableTop, rightMargin, tableTop + headerHeight, headerBg)
            canvas.drawRect(leftMargin, tableTop, rightMargin, tableTop + headerHeight, borderPaint)

            var curX = rightMargin

            val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(27, 94, 32)
                textSize = 9.5f
                typeface = cairoBold
                textAlign = Paint.Align.CENTER
            }

            // 1) اسم الشبل
            curX -= colNameWidth
            canvas.drawText("الاسم واللقب", curX + colNameWidth / 2f, tableTop + 20f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 2) شارة القبول
            curX -= colAcceptWidth
            canvas.drawText("شارة القبول", curX + colAcceptWidth / 2f, tableTop + 20f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 3) شارة المبتدئ
            curX -= colBeginWidth
            canvas.drawText("شارة المبتدئ", curX + colBeginWidth / 2f, tableTop + 20f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 4) شارة الثاني
            curX -= colSecondWidth
            canvas.drawText("شارة الثاني", curX + colSecondWidth / 2f, tableTop + 20f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 5) شارة الأول
            curX -= colFirstWidth
            canvas.drawText("شارة الأول", curX + colFirstWidth / 2f, tableTop + 20f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 6) شارات الهواية
            curX -= colHobbyWidth
            canvas.drawText("شارات الهواية (العدد)", curX + colHobbyWidth / 2f, tableTop + 20f, thPaint)

            // صفوف الأشبال
            var currentY = tableTop + headerHeight
            chunk.forEachIndexed { rIdx, scout ->
                val isEven = rIdx % 2 == 0
                val rowBgColor = if (isEven) Color.WHITE else Color.rgb(248, 249, 250)
                val rowBg = Paint().apply { color = rowBgColor; style = Paint.Style.FILL }
                canvas.drawRect(leftMargin, currentY, rightMargin, currentY + rowHeight, rowBg)
                canvas.drawRect(leftMargin, currentY, rightMargin, currentY + rowHeight, borderPaint)

                var rowX = rightMargin

                // 1) اسم الشبل
                rowX -= colNameWidth
                val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.RIGHT
                }
                val scoutName = truncateText(scout.fullName, colNameWidth - 14f, namePaint)
                canvas.drawText(scoutName, rightMargin - 8f, currentY + 16f, namePaint)
                canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                val scoutBadges = badgeMap[scout.id].orEmpty()

                fun drawBadgeCell(colW: Float, isGranted: Boolean) {
                    rowX -= colW
                    if (isGranted) {
                        val badgePillPaint = Paint().apply {
                            color = Color.rgb(232, 245, 233)
                            style = Paint.Style.FILL
                        }
                        val pillRect = RectF(rowX + colW / 2f - 28f, currentY + 4f, rowX + colW / 2f + 28f, currentY + rowHeight - 4f)
                        canvas.drawRoundRect(pillRect, 6f, 6f, badgePillPaint)

                        val grantedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.rgb(46, 125, 50)
                            textSize = 9f
                            typeface = cairoBold
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText("ممنوحة ✓", rowX + colW / 2f, currentY + 16f, grantedPaint)
                    } else {
                        val absentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.rgb(180, 180, 180)
                            textSize = 9.5f
                            typeface = cairoFont
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText("—", rowX + colW / 2f, currentY + 16f, absentPaint)
                    }
                    canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)
                }

                // 2) شارة القبول
                val hasAcceptance = scoutBadges.any { it.contains("ACCEPTANCE", true) || it.contains("قبول") }
                drawBadgeCell(colAcceptWidth, hasAcceptance)

                // 3) شارة المبتدئ
                val hasBeginner = scoutBadges.any { it.contains("BEGINNER", true) || it.contains("مبتدئ") }
                drawBadgeCell(colBeginWidth, hasBeginner)

                // 4) شارة الثاني
                val hasSecond = scoutBadges.any { it.contains("SECOND", true) || it.contains("ثاني") }
                drawBadgeCell(colSecondWidth, hasSecond)

                // 5) شارة الأول
                val hasFirst = scoutBadges.any { it.contains("FIRST", true) || it.contains("أول") }
                drawBadgeCell(colFirstWidth, hasFirst)

                // 6) شارات الهواية
                rowX -= colHobbyWidth
                val hobbyCount = hobbyCountMap[scout.id] ?: 0
                val hobbyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (hobbyCount > 0) Color.rgb(27, 94, 32) else Color.rgb(150, 150, 150)
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                val hobbyText = if (hobbyCount > 0) "$hobbyCount شارات" else "0"
                canvas.drawText(hobbyText, rowX + colHobbyWidth / 2f, currentY + 16f, hobbyPaint)

                currentY += rowHeight
            }

            drawScoutPdfFooter(canvas, pageWidth, pageHeight, pageIndex + 1, totalPages, cairoFont, terms = terms)

            pdfDocument.finishPage(page)
        }

        val file = File(context.cacheDir, "progress_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return file
    }

    /**
     * 4) تقرير تقييم القادة (مصفوفة A4 أفقي)
     */
    fun exportLeadersEvaluationPdf(
        scoutNames: List<String>,
        leaderNames: List<String>,
        matrixData: Map<String, Map<String, String>>,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoFont = getCairoTypeface(context) ?: Typeface.DEFAULT
        val cairoBold = getBoldCairoTypeface(cairoFont) ?: Typeface.DEFAULT_BOLD

        val pageWidth = 842
        val pageHeight = 595
        val leftMargin = 24f
        val rightMargin = 818f
        val totalAvailableWidth = rightMargin - leftMargin // 794f

        val rowsPerPage = 16
        val maxLeadersPerPage = 5
        val leaderChunks = if (leaderNames.isNotEmpty()) leaderNames.chunked(maxLeadersPerPage) else listOf(emptyList())
        val scoutChunks = if (scoutNames.isNotEmpty()) scoutNames.chunked(rowsPerPage) else listOf(emptyList())
        val totalPages = maxOf(1, leaderChunks.size * scoutChunks.size)

        val colNameWidth = 154f
        val colOverallWidth = 90f

        val sdf = SimpleDateFormat("dd/MM/yyyy", com.example.presentation.common.AppLocale.AR_DZ)

        var pageNumber = 1
        for (leaderChunk in leaderChunks) {
            val leaderCount = leaderChunk.size
            val availableLeaderWidth = totalAvailableWidth - colNameWidth - colOverallWidth
            val leaderColWidth = if (leaderCount > 0) availableLeaderWidth / leaderCount else 0f
            val tableWidth = colNameWidth + (leaderColWidth * leaderCount) + colOverallWidth
            val tableLeft = leftMargin + (totalAvailableWidth - tableWidth) / 2f
            val tableRight = tableLeft + tableWidth

            for (scoutChunk in scoutChunks) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val subInfo = "تاريخ التقرير: ${sdf.format(Date())} | إجمالي ${terms.scoutPluralDefinite}: ${scoutNames.size} | إجمالي القادة المقيّمين: ${leaderNames.size}"
                drawScoutPdfHeader(canvas, pageWidth, "تقرير تقييم القادة", subInfo, cairoFont, cairoBold, context, isLandscape = true, terms = terms)

                val tableTop = 86f
                val headerHeight = 32f
                val rowHeight = 24f

                val headerBg = Paint().apply {
                    color = Color.rgb(232, 245, 233)
                    style = Paint.Style.FILL
                }
                val borderPaint = Paint().apply {
                    color = Color.rgb(200, 214, 201)
                    style = Paint.Style.STROKE
                    strokeWidth = 0.8f
                }
                canvas.drawRect(tableLeft, tableTop, tableRight, tableTop + headerHeight, headerBg)
                canvas.drawRect(tableLeft, tableTop, tableRight, tableTop + headerHeight, borderPaint)

                var currentX = tableRight

                val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(27, 94, 32)
                    textSize = 9.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }

                // 1) اسم الشبل
                currentX -= colNameWidth
                canvas.drawText("اسم ${terms.scoutSingularDefinite}", currentX + colNameWidth / 2f, tableTop + 20f, thPaint)
                canvas.drawLine(currentX, tableTop, currentX, tableTop + headerHeight, borderPaint)

                // 2) أعمدة القادة
                val leaderThPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(27, 94, 32)
                    textSize = 9f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                for (leader in leaderChunk) {
                    currentX -= leaderColWidth
                    val truncatedLeader = truncateText(leader, leaderColWidth - 8f, leaderThPaint)
                    canvas.drawText(truncatedLeader, currentX + leaderColWidth / 2f, tableTop + 20f, leaderThPaint)
                    canvas.drawLine(currentX, tableTop, currentX, tableTop + headerHeight, borderPaint)
                }

                // 3) المعدل العام للقادة
                currentX -= colOverallWidth
                canvas.drawText("المعدل العام", currentX + colOverallWidth / 2f, tableTop + 20f, thPaint)

                // صفوف الأشبال
                var currentY = tableTop + headerHeight
                scoutChunk.forEachIndexed { rIdx, scoutName ->
                    val isEven = rIdx % 2 == 0
                    val rowBgColor = if (isEven) Color.WHITE else Color.rgb(248, 249, 250)
                    val rowBg = Paint().apply { color = rowBgColor; style = Paint.Style.FILL }
                    canvas.drawRect(tableLeft, currentY, tableRight, currentY + rowHeight, rowBg)
                    canvas.drawRect(tableLeft, currentY, tableRight, currentY + rowHeight, borderPaint)

                    var rowX = tableRight

                    // 1) اسم الشبل
                    rowX -= colNameWidth
                    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 9.5f
                        typeface = cairoBold
                        textAlign = Paint.Align.RIGHT
                    }
                    val truncatedName = truncateText(scoutName, colNameWidth - 14f, namePaint)
                    canvas.drawText(truncatedName, tableRight - 8f, currentY + 16f, namePaint)
                    canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                    val scoutScores = matrixData[scoutName].orEmpty()

                    // 2) درجات القادة
                    val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 9f
                        typeface = cairoBold
                        textAlign = Paint.Align.CENTER
                    }
                    for (leader in leaderChunk) {
                        rowX -= leaderColWidth
                        val score = scoutScores[leader] ?: "—"
                        scorePaint.color = if (score != "—") Color.rgb(33, 33, 33) else Color.rgb(180, 180, 180)
                        canvas.drawText(score, rowX + leaderColWidth / 2f, currentY + 16f, scorePaint)
                        canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)
                    }

                    // 3) المعدل العام
                    rowX -= colOverallWidth
                    val overall = scoutScores["المعدل العام"] ?: scoutScores["overall"] ?: "—"
                    val overallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (overall != "—") Color.rgb(27, 94, 32) else Color.rgb(180, 180, 180)
                        textSize = 9.5f
                        typeface = cairoBold
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(overall, rowX + colOverallWidth / 2f, currentY + 16f, overallPaint)

                    currentY += rowHeight
                }

                drawScoutPdfFooter(canvas, pageWidth, pageHeight, pageNumber, totalPages, cairoFont, terms = terms)

                pdfDocument.finishPage(page)
                pageNumber++
            }
        }

        val file = File(context.cacheDir, "leaders_evaluation_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return file
    }

    fun shareAuditLogPdf(
        file: File,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        sharePdfFile(file, "سجل العمليات — ${terms.groupName} ${terms.unitNameDefinite}", "مشاركة سجل العمليات كـ PDF", context)
    }

    /**
     * 5) تقرير سجل العمليات والتدقيق (A4 عمودي)
     * جدول: التاريخ والوقت، القائد، العملية، البيان والهدف
     */
    fun exportAuditLogPdf(
        logs: List<ActivityLog>,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoFont = getCairoTypeface(context) ?: Typeface.DEFAULT
        val cairoBold = getBoldCairoTypeface(cairoFont) ?: Typeface.DEFAULT_BOLD

        val pageWidth = 595
        val pageHeight = 842
        val leftMargin = 25f
        val rightMargin = 570f
        val tableWidth = 545f

        val rowsPerPage = 22
        val logChunks = if (logs.isNotEmpty()) logs.chunked(rowsPerPage) else listOf(emptyList())
        val totalPages = maxOf(1, logChunks.size)

        val sdf = SimpleDateFormat("dd/MM/yyyy", com.example.presentation.common.AppLocale.AR_DZ)
        val timeFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", com.example.presentation.common.AppLocale.AR_DZ)

        // توزيع الأعمدة (المجموع 545f من اليمين إلى اليسار):
        // 1) البيان والتفاصيل (215f)
        // 2) العملية (80f)
        // 3) القائد (120f)
        // 4) التاريخ والوقت (130f)
        val colDetailsWidth = 215f
        val colActionWidth = 80f
        val colLeaderWidth = 120f
        val colDateWidth = 130f

        logChunks.forEachIndexed { pageIndex, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val subInfo = "تاريخ التقرير: ${sdf.format(Date())} | إجمالي العمليات المسجلة: ${logs.size}"
            drawScoutPdfHeader(canvas, pageWidth, "سجل العمليات والتدقيق", subInfo, cairoFont, cairoBold, context, isLandscape = false, terms = terms)

            val tableTop = 86f
            val headerHeight = 28f
            val rowHeight = 24f

            val headerBg = Paint().apply {
                color = Color.rgb(232, 245, 233)
                style = Paint.Style.FILL
            }
            val borderPaint = Paint().apply {
                color = Color.rgb(200, 214, 201)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            canvas.drawRect(leftMargin, tableTop, rightMargin, tableTop + headerHeight, headerBg)
            canvas.drawRect(leftMargin, tableTop, rightMargin, tableTop + headerHeight, borderPaint)

            var curX = rightMargin

            val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(27, 94, 32)
                textSize = 9f
                typeface = cairoBold
                textAlign = Paint.Align.CENTER
            }

            // 1) التفاصيل والهدف
            curX -= colDetailsWidth
            canvas.drawText("البيان / الهدف", curX + colDetailsWidth / 2f, tableTop + 18f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 2) نوع العملية
            curX -= colActionWidth
            canvas.drawText("العملية", curX + colActionWidth / 2f, tableTop + 18f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 3) القائد المنفذ
            curX -= colLeaderWidth
            canvas.drawText("القائد", curX + colLeaderWidth / 2f, tableTop + 18f, thPaint)
            canvas.drawLine(curX, tableTop, curX, tableTop + headerHeight, borderPaint)

            // 4) التاريخ والوقت
            curX -= colDateWidth
            canvas.drawText("التاريخ والوقت", curX + colDateWidth / 2f, tableTop + 18f, thPaint)

            // صفوف العمليات
            var currentY = tableTop + headerHeight
            chunk.forEachIndexed { rIdx, log ->
                val isEven = rIdx % 2 == 0
                val rowBgColor = if (isEven) Color.WHITE else Color.rgb(248, 249, 250)
                val rowBg = Paint().apply { color = rowBgColor; style = Paint.Style.FILL }
                canvas.drawRect(leftMargin, currentY, rightMargin, currentY + rowHeight, rowBg)
                canvas.drawRect(leftMargin, currentY, rightMargin, currentY + rowHeight, borderPaint)

                var rowX = rightMargin

                // 1) التفاصيل والهدف
                rowX -= colDetailsWidth
                val detailsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 8.5f
                    typeface = cairoFont
                    textAlign = Paint.Align.RIGHT
                }
                val targetText = if (log.targetName.isNotBlank()) log.targetName else log.targetType
                val truncatedDetails = truncateText(targetText, colDetailsWidth - 14f, detailsPaint)
                canvas.drawText(truncatedDetails, rightMargin - 8f, currentY + 16f, detailsPaint)
                canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                // 2) العملية
                rowX -= colActionWidth
                val (actionArabic, actionColor) = when (log.action.lowercase()) {
                    "add", "create", "grant" -> "إضافة" to Color.rgb(46, 125, 50)
                    "update", "edit", "modify", "archive" -> "تعديل" to Color.rgb(245, 124, 0)
                    "delete", "remove", "revoke", "cancel" -> "حذف" to Color.rgb(211, 47, 47)
                    "mark_done" -> "إتمام" to Color.rgb(46, 125, 50)
                    else -> log.action to Color.DKGRAY
                }
                val actionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = actionColor
                    textSize = 9f
                    typeface = cairoBold
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(actionArabic, rowX + colActionWidth / 2f, currentY + 16f, actionPaint)
                canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                // 3) القائد
                rowX -= colLeaderWidth
                val leaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(33, 33, 33)
                    textSize = 8.5f
                    typeface = cairoBold
                    textAlign = Paint.Align.RIGHT
                }
                val leaderName = log.userName.ifBlank { "قائد كشفي" }
                val truncatedLeader = truncateText(leaderName, colLeaderWidth - 12f, leaderPaint)
                canvas.drawText(truncatedLeader, rowX + colLeaderWidth - 6f, currentY + 16f, leaderPaint)
                canvas.drawLine(rowX, currentY, rowX, currentY + rowHeight, borderPaint)

                // 4) التاريخ والوقت
                rowX -= colDateWidth
                val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 8f
                    typeface = cairoFont
                    textAlign = Paint.Align.CENTER
                }
                val formattedTime = if (log.timestamp > 0L) timeFormat.format(Date(log.timestamp)) else "—"
                canvas.drawText(formattedTime, rowX + colDateWidth / 2f, currentY + 16f, datePaint)

                currentY += rowHeight
            }

            drawScoutPdfFooter(canvas, pageWidth, pageHeight, pageIndex + 1, totalPages, cairoFont, terms = terms)

            pdfDocument.finishPage(page)
        }

        val file = File(context.cacheDir, "audit_log_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return file
    }

    private sealed class ActivityFinancePdfBlock(val height: Float) {
        data class SummaryBox(
            val totalIncome: Int,
            val subsIncome: Int,
            val extraIncome: Int,
            val totalExpenses: Int,
            val netBalance: Int
        ) : ActivityFinancePdfBlock(92f)

        data class SectionTitle(val title: String) : ActivityFinancePdfBlock(26f)
        data object PaymentsTableHeader : ActivityFinancePdfBlock(24f)
        data class PaymentsTableRow(
            val index: Int,
            val nameLines: List<String>,
            val totalPaid: Int,
            val breakdownLines: List<String>,
            val rowHeight: Float
        ) : ActivityFinancePdfBlock(rowHeight)

        data class PaymentsTableTotal(
            val totalPaid: Int,
            val breakdownLines: List<String>,
            val rowHeight: Float
        ) : ActivityFinancePdfBlock(rowHeight)

        data object ExpensesTableHeader : ActivityFinancePdfBlock(24f)
        data class ExpensesTableRow(
            val index: Int,
            val descLines: List<String>,
            val categoryLabel: String,
            val amount: Int,
            val rowHeight: Float
        ) : ActivityFinancePdfBlock(rowHeight)

        data class ExpensesTableTotal(val totalAmount: Int) : ActivityFinancePdfBlock(24f)
        data class EmptyState(val message: String) : ActivityFinancePdfBlock(32f)
        data class Gap(val space: Float) : ActivityFinancePdfBlock(space)
    }

    fun shareActivityFinancePdf(
        file: File,
        activityTitle: String,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        sharePdfFile(
            file = file,
            subject = "التقرير المالي للنشاط: $activityTitle — ${terms.groupName} ${terms.unitNameDefinite}",
            chooserTitle = "مشاركة التقرير المالي للنشاط كـ PDF",
            context = context
        )
    }

    private fun formatPaymentBreakdown(subPaid: Int, debtPaid: Int, advancePaid: Int): String {
        val parts = mutableListOf<String>()
        if (subPaid > 0) parts.add("اشتراك: $subPaid")
        if (debtPaid > 0) parts.add("دين: $debtPaid")
        if (advancePaid > 0) parts.add("رصيد: $advancePaid")
        return if (parts.isEmpty()) "—" else parts.joinToString(" | ")
    }

    /**
     * التقرير المالي المفصل للنشاط الكشفي (A4 عمودي)
     */
    fun exportActivityFinancePdf(
        activity: Activity,
        scoutPayments: List<ScoutPaymentDetail>,
        expenses: List<FinanceTransaction>,
        netBalance: Int,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoFont = getCairoTypeface(context) ?: Typeface.DEFAULT
        val cairoBold = getBoldCairoTypeface(cairoFont) ?: Typeface.DEFAULT_BOLD

        val pageWidth = 595
        val pageHeight = 842
        val leftMargin = 35f
        val rightMargin = 560f
        val contentWidth = rightMargin - leftMargin // 525f
        val contentTopY = 104f
        val contentBottomY = 790f

        val validPayments = scoutPayments.filter { it.totalPaid > 0 }
        val expenseTxs = expenses.filter { it.type == FinanceTransactionType.EXPENSE }

        val subsIncome = validPayments.sumOf { it.subPaid }
        val debtIncome = validPayments.sumOf { it.debtPaid }
        val advanceIncome = validPayments.sumOf { it.advancePaid }
        val extraIncome = debtIncome + advanceIncome
        val totalIncome = validPayments.sumOf { it.totalPaid }
        val totalExpenses = expenseTxs.sumOf { it.amount }

        val primaryGreen = Color.rgb(46, 125, 50)
        val darkGreen = Color.rgb(27, 94, 32)
        val expenseRed = Color.rgb(198, 40, 40)
        val netBlue = Color.rgb(21, 101, 192)
        val goldColor = Color.rgb(200, 169, 81)

        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkGreen
            textSize = 11.5f
            typeface = cairoBold
            textAlign = Paint.Align.RIGHT
        }
        val thBgPaint = Paint().apply {
            color = Color.rgb(232, 245, 233)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(200, 214, 201)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val thTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkGreen
            textSize = 9.5f
            typeface = cairoBold
            textAlign = Paint.Align.CENTER
        }
        val rowTextRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 33, 33)
            textSize = 9f
            typeface = cairoFont
            textAlign = Paint.Align.RIGHT
        }
        val rowTextCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 33, 33)
            textSize = 9f
            typeface = cairoFont
            textAlign = Paint.Align.CENTER
        }
        val totalBgPaint = Paint().apply {
            color = Color.rgb(245, 247, 245)
            style = Paint.Style.FILL
        }

        // عرض أعمدة جدول المدفوعات المدمج (المجموع 525f من اليمين إلى اليسار):
        // # (32f) | اسم الشبل (165f) | إجمالي المدفوع (98f) | تفاصيل الدفع (230f)
        val pColNum = 32f
        val pColName = 165f
        val pColTotal = 98f
        val pColBreakdown = 230f

        // عرض أعمدة جدول المصاريف (المجموع 525f من اليمين إلى اليسار):
        // # (32f) | الوصف (248f) | التصنيف (135f) | المبلغ (110f)
        val eColNum = 32f
        val eColDesc = 248f
        val eColCat = 135f
        val eColAmount = 110f

        val lineHeight = 13f

        // تجميع العناصر مع الالتفاف والتقسيم الذكي للصفحات
        val blocks = mutableListOf<ActivityFinancePdfBlock>()
        blocks.add(
            ActivityFinancePdfBlock.SummaryBox(
                totalIncome = totalIncome,
                subsIncome = subsIncome,
                extraIncome = extraIncome,
                totalExpenses = totalExpenses,
                netBalance = netBalance
            )
        )
        blocks.add(ActivityFinancePdfBlock.Gap(14f))

        // جدول 1: الأشبال المشاركون بالدفع (مدمج مع الديون والرصيد)
        blocks.add(ActivityFinancePdfBlock.SectionTitle("1. ${terms.scoutPluralDefinite} المشاركون بالدفع (${validPayments.size})"))
        if (validPayments.isEmpty()) {
            blocks.add(ActivityFinancePdfBlock.EmptyState("لا توجد مدفوعات مسجلة لهذا النشاط"))
        } else {
            blocks.add(ActivityFinancePdfBlock.PaymentsTableHeader)
            validPayments.forEachIndexed { idx, detail ->
                val nameLines = wrapText(detail.scoutName.ifBlank { terms.scoutSingular }, rowTextRightPaint, pColName - 14f)
                    .ifEmpty { listOf(terms.scoutSingular) }
                val breakdownText = formatPaymentBreakdown(detail.subPaid, detail.debtPaid, detail.advancePaid)
                val breakdownLines = wrapText(breakdownText, rowTextRightPaint, pColBreakdown - 14f)
                    .ifEmpty { listOf(breakdownText) }
                val maxLines = maxOf(1, nameLines.size, breakdownLines.size)
                val rowHeight = maxOf(22f, 10f + maxLines * lineHeight)

                blocks.add(
                    ActivityFinancePdfBlock.PaymentsTableRow(
                        index = idx + 1,
                        nameLines = nameLines,
                        totalPaid = detail.totalPaid,
                        breakdownLines = breakdownLines,
                        rowHeight = rowHeight
                    )
                )
            }
            val totalBreakdownText = formatPaymentBreakdown(subsIncome, debtIncome, advanceIncome)
            val totalBreakdownLines = wrapText(totalBreakdownText, rowTextRightPaint, pColBreakdown - 14f)
                .ifEmpty { listOf(totalBreakdownText) }
            val totalRowHeight = maxOf(24f, 10f + totalBreakdownLines.size * lineHeight)
            blocks.add(
                ActivityFinancePdfBlock.PaymentsTableTotal(
                    totalPaid = totalIncome,
                    breakdownLines = totalBreakdownLines,
                    rowHeight = totalRowHeight
                )
            )
        }
        blocks.add(ActivityFinancePdfBlock.Gap(14f))

        // جدول 2: مصاريف النشاط
        blocks.add(ActivityFinancePdfBlock.SectionTitle("2. مصاريف النشاط (${expenseTxs.size})"))
        if (expenseTxs.isEmpty()) {
            blocks.add(ActivityFinancePdfBlock.EmptyState("لا توجد مصاريف"))
        } else {
            blocks.add(ActivityFinancePdfBlock.ExpensesTableHeader)
            expenseTxs.forEachIndexed { idx, tx ->
                val descText = tx.description.ifBlank { "مصروف نشاط" }
                val descLines = wrapText(descText, rowTextRightPaint, eColDesc - 14f)
                    .ifEmpty { listOf(descText) }
                val rowHeight = maxOf(22f, 10f + descLines.size * lineHeight)
                blocks.add(
                    ActivityFinancePdfBlock.ExpensesTableRow(
                        index = idx + 1,
                        descLines = descLines,
                        categoryLabel = tx.category?.arabicLabel ?: "أخرى",
                        amount = tx.amount,
                        rowHeight = rowHeight
                    )
                )
            }
            blocks.add(ActivityFinancePdfBlock.ExpensesTableTotal(totalAmount = totalExpenses))
        }

        // توزيع العناصر على الصفحات
        val pages = mutableListOf<List<ActivityFinancePdfBlock>>()
        var currentPage = mutableListOf<ActivityFinancePdfBlock>()
        var currentY = contentTopY

        fun flushPage() {
            if (currentPage.isNotEmpty()) {
                pages.add(currentPage)
                currentPage = mutableListOf()
                currentY = contentTopY
            }
        }

        var idx = 0
        while (idx < blocks.size) {
            val block = blocks[idx]
            if (block is ActivityFinancePdfBlock.Gap && currentPage.isEmpty()) {
                idx++
                continue
            }

            if (block is ActivityFinancePdfBlock.SectionTitle && idx + 1 < blocks.size) {
                val nextBlock = blocks[idx + 1]
                val nextNextHeight = if (nextBlock is ActivityFinancePdfBlock.PaymentsTableHeader ||
                    nextBlock is ActivityFinancePdfBlock.ExpensesTableHeader
                ) {
                    blocks.getOrNull(idx + 2)?.height ?: 0f
                } else {
                    0f
                }
                if (currentY + block.height + nextBlock.height + nextNextHeight > contentBottomY && currentPage.isNotEmpty()) {
                    flushPage()
                    continue
                }
            }

            if (currentY + block.height > contentBottomY && currentPage.isNotEmpty()) {
                flushPage()
                when (block) {
                    is ActivityFinancePdfBlock.PaymentsTableRow, is ActivityFinancePdfBlock.PaymentsTableTotal -> {
                        val header = ActivityFinancePdfBlock.PaymentsTableHeader
                        currentPage.add(header)
                        currentY += header.height
                    }
                    is ActivityFinancePdfBlock.ExpensesTableRow, is ActivityFinancePdfBlock.ExpensesTableTotal -> {
                        val header = ActivityFinancePdfBlock.ExpensesTableHeader
                        currentPage.add(header)
                        currentY += header.height
                    }
                    else -> {}
                }
            }

            currentPage.add(block)
            currentY += block.height
            idx++
        }
        flushPage()
        if (pages.isEmpty()) {
            pages.add(emptyList())
        }

        val totalPages = pages.size
        val shortDateFormatter = SimpleDateFormat("d MMMM yyyy", com.example.presentation.common.AppLocale.AR_DZ)
        val activityDateStr = if (activity.isMultiDay()) {
            "من ${shortDateFormatter.format(Date(activity.startDate))} إلى ${dateFormatter.format(Date(activity.endDate))}"
        } else {
            dateFormatter.format(Date(activity.startDate))
        }

        val titleMeasurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f
            typeface = cairoBold
        }
        val headerTitle = truncateText("التقرير المالي للنشاط: ${activity.title}", 430f, titleMeasurePaint)

        pages.forEachIndexed { pageIndex, pageBlocks ->
            val pageNumber = pageIndex + 1
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            drawScoutPdfHeader(
                canvas = canvas,
                pageWidth = pageWidth,
                title = headerTitle,
                subInfo = "تاريخ النشاط: $activityDateStr",
                cairoFont = cairoFont,
                cairoBold = cairoBold,
                context = context,
                isLandscape = false,
                terms = terms
            )

            var y = contentTopY
            for (block in pageBlocks) {
                when (block) {
                    is ActivityFinancePdfBlock.SummaryBox -> {
                        val boxHeight = 82f
                        val gap = 10f
                        val cardW = (contentWidth - gap * 2f) / 3f

                        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 1.2f
                        }
                        val cardLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.rgb(85, 85, 85)
                            textSize = 9.5f
                            typeface = cairoBold
                            textAlign = Paint.Align.CENTER
                        }
                        val cardValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = 13f
                            typeface = cairoBold
                            textAlign = Paint.Align.CENTER
                        }
                        val cardSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.rgb(105, 105, 105)
                            textSize = 7.5f
                            typeface = cairoFont
                            textAlign = Paint.Align.CENTER
                        }

                        // البطاقة اليمنى: إجمالي الواردات (أخضر)
                        val incRight = rightMargin
                        val incLeft = incRight - cardW
                        cardBg.color = Color.rgb(232, 245, 233)
                        cardBorder.color = Color.rgb(165, 214, 167)
                        canvas.drawRoundRect(incLeft, y, incRight, y + boxHeight, 10f, 10f, cardBg)
                        canvas.drawRoundRect(incLeft, y, incRight, y + boxHeight, 10f, 10f, cardBorder)
                        canvas.drawText("إجمالي الواردات", incLeft + cardW / 2f, y + 22f, cardLabelPaint)
                        cardValuePaint.color = primaryGreen
                        canvas.drawText("${block.totalIncome} دج", incLeft + cardW / 2f, y + 46f, cardValuePaint)
                        canvas.drawText(
                            "اشتراكات: ${block.subsIncome} | أخرى: ${block.extraIncome}",
                            incLeft + cardW / 2f,
                            y + 66f,
                            cardSubPaint
                        )

                        // البطاقة الوسطى: إجمالي المصاريف (أحمر)
                        val expRight = incLeft - gap
                        val expLeft = expRight - cardW
                        cardBg.color = Color.rgb(255, 235, 238)
                        cardBorder.color = Color.rgb(239, 154, 154)
                        canvas.drawRoundRect(expLeft, y, expRight, y + boxHeight, 10f, 10f, cardBg)
                        canvas.drawRoundRect(expLeft, y, expRight, y + boxHeight, 10f, 10f, cardBorder)
                        canvas.drawText("إجمالي المصاريف", expLeft + cardW / 2f, y + 22f, cardLabelPaint)
                        cardValuePaint.color = expenseRed
                        canvas.drawText("${block.totalExpenses} دج", expLeft + cardW / 2f, y + 46f, cardValuePaint)
                        canvas.drawText("مصاريف النشاط", expLeft + cardW / 2f, y + 66f, cardSubPaint)

                        // البطاقة اليسرى: الصافي (أزرق / ذهبي)
                        val netRight = expLeft - gap
                        val netLeft = leftMargin
                        cardBg.color = if (block.netBalance >= 0) Color.rgb(235, 243, 254) else Color.rgb(255, 243, 224)
                        cardBorder.color = goldColor
                        canvas.drawRoundRect(netLeft, y, netRight, y + boxHeight, 10f, 10f, cardBg)
                        canvas.drawRoundRect(netLeft, y, netRight, y + boxHeight, 10f, 10f, cardBorder)
                        canvas.drawText("الصافي", netLeft + cardW / 2f, y + 22f, cardLabelPaint)
                        cardValuePaint.color = if (block.netBalance >= 0) netBlue else expenseRed
                        canvas.drawText("${block.netBalance} دج", netLeft + cardW / 2f, y + 46f, cardValuePaint)
                        canvas.drawText(
                            if (block.netBalance >= 0) "فائض النشاط" else "عجز النشاط",
                            netLeft + cardW / 2f,
                            y + 66f,
                            cardSubPaint
                        )
                    }

                    is ActivityFinancePdfBlock.SectionTitle -> {
                        canvas.drawText(block.title, rightMargin, y + 18f, sectionPaint)
                    }

                    is ActivityFinancePdfBlock.EmptyState -> {
                        val emptyBg = Paint().apply {
                            color = Color.rgb(250, 250, 250)
                            style = Paint.Style.FILL
                        }
                        canvas.drawRoundRect(leftMargin, y, rightMargin, y + 28f, 6f, 6f, emptyBg)
                        canvas.drawRoundRect(leftMargin, y, rightMargin, y + 28f, 6f, 6f, borderPaint)
                        val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.GRAY
                            textSize = 9.5f
                            typeface = cairoFont
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText(block.message, (leftMargin + rightMargin) / 2f, y + 18f, emptyPaint)
                    }

                    is ActivityFinancePdfBlock.PaymentsTableHeader -> {
                        val h = block.height
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, thBgPaint)
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, borderPaint)

                        var cx = rightMargin
                        cx -= pColNum
                        canvas.drawText("#", cx + pColNum / 2f, y + 16f, thTextPaint)
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= pColName
                        thTextPaint.textAlign = Paint.Align.RIGHT
                        canvas.drawText("اسم ${terms.scoutSingularDefinite}", cx + pColName - 8f, y + 16f, thTextPaint)
                        thTextPaint.textAlign = Paint.Align.CENTER
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= pColTotal
                        canvas.drawText("إجمالي المدفوع", cx + pColTotal / 2f, y + 16f, thTextPaint)
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= pColBreakdown
                        thTextPaint.textAlign = Paint.Align.RIGHT
                        canvas.drawText("تفاصيل الدفع", cx + pColBreakdown - 8f, y + 16f, thTextPaint)
                        thTextPaint.textAlign = Paint.Align.CENTER
                    }

                    is ActivityFinancePdfBlock.PaymentsTableRow -> {
                        val h = block.rowHeight
                        val rowBg = Paint().apply {
                            color = if (block.index % 2 == 1) Color.WHITE else Color.rgb(249, 250, 249)
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, rowBg)
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, borderPaint)

                        var cx = rightMargin
                        cx -= pColNum
                        canvas.drawText("${block.index}", cx + pColNum / 2f, y + h / 2f + 3f, rowTextCenterPaint)
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= pColName
                        val nameStartY = y + (h - (block.nameLines.size - 1) * lineHeight) / 2f + 3f
                        block.nameLines.forEachIndexed { lineIdx, line ->
                            canvas.drawText(line, cx + pColName - 8f, nameStartY + lineIdx * lineHeight, rowTextRightPaint)
                        }
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= pColTotal
                        val paidPaint = Paint(rowTextCenterPaint).apply {
                            color = primaryGreen
                            typeface = cairoBold
                        }
                        canvas.drawText("${block.totalPaid} دج", cx + pColTotal / 2f, y + h / 2f + 3f, paidPaint)
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= pColBreakdown
                        val breakdownPaint = Paint(rowTextRightPaint).apply {
                            color = Color.rgb(75, 75, 75)
                        }
                        val breakdownStartY = y + (h - (block.breakdownLines.size - 1) * lineHeight) / 2f + 3f
                        block.breakdownLines.forEachIndexed { lineIdx, line ->
                            canvas.drawText(
                                line,
                                cx + pColBreakdown - 8f,
                                breakdownStartY + lineIdx * lineHeight,
                                breakdownPaint
                            )
                        }
                    }

                    is ActivityFinancePdfBlock.PaymentsTableTotal -> {
                        val h = block.rowHeight
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, totalBgPaint)
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, borderPaint)

                        val labelColLeft = rightMargin - (pColNum + pColName)
                        val totalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = darkGreen
                            textSize = 9.5f
                            typeface = cairoBold
                            textAlign = Paint.Align.RIGHT
                        }
                        canvas.drawText("الإجمالي", rightMargin - 8f, y + h / 2f + 3f, totalLabelPaint)
                        canvas.drawLine(labelColLeft, y, labelColLeft, y + h, borderPaint)

                        var cx = labelColLeft
                        cx -= pColTotal
                        val paidTotalPaint = Paint(rowTextCenterPaint).apply {
                            color = primaryGreen
                            textSize = 9.5f
                            typeface = cairoBold
                        }
                        canvas.drawText("${block.totalPaid} دج", cx + pColTotal / 2f, y + h / 2f + 3f, paidTotalPaint)
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= pColBreakdown
                        val totalBreakdownPaint = Paint(rowTextRightPaint).apply {
                            color = darkGreen
                            textSize = 8.5f
                            typeface = cairoBold
                        }
                        val breakdownStartY = y + (h - (block.breakdownLines.size - 1) * lineHeight) / 2f + 3f
                        block.breakdownLines.forEachIndexed { lineIdx, line ->
                            canvas.drawText(
                                line,
                                cx + pColBreakdown - 8f,
                                breakdownStartY + lineIdx * lineHeight,
                                totalBreakdownPaint
                            )
                        }
                    }

                    is ActivityFinancePdfBlock.ExpensesTableHeader -> {
                        val h = block.height
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, thBgPaint)
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, borderPaint)

                        var cx = rightMargin
                        cx -= eColNum
                        canvas.drawText("#", cx + eColNum / 2f, y + 16f, thTextPaint)
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= eColDesc
                        thTextPaint.textAlign = Paint.Align.RIGHT
                        canvas.drawText("الوصف", cx + eColDesc - 8f, y + 16f, thTextPaint)
                        thTextPaint.textAlign = Paint.Align.CENTER
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= eColCat
                        canvas.drawText("التصنيف", cx + eColCat / 2f, y + 16f, thTextPaint)
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= eColAmount
                        canvas.drawText("المبلغ", cx + eColAmount / 2f, y + 16f, thTextPaint)
                    }

                    is ActivityFinancePdfBlock.ExpensesTableRow -> {
                        val h = block.rowHeight
                        val rowBg = Paint().apply {
                            color = if (block.index % 2 == 1) Color.WHITE else Color.rgb(249, 250, 249)
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, rowBg)
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, borderPaint)

                        var cx = rightMargin
                        cx -= eColNum
                        canvas.drawText("${block.index}", cx + eColNum / 2f, y + h / 2f + 3f, rowTextCenterPaint)
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= eColDesc
                        val descStartY = y + (h - (block.descLines.size - 1) * lineHeight) / 2f + 3f
                        block.descLines.forEachIndexed { lineIdx, line ->
                            canvas.drawText(line, cx + eColDesc - 8f, descStartY + lineIdx * lineHeight, rowTextRightPaint)
                        }
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= eColCat
                        canvas.drawText(
                            truncateText(block.categoryLabel, eColCat - 12f, rowTextCenterPaint),
                            cx + eColCat / 2f,
                            y + h / 2f + 3f,
                            rowTextCenterPaint
                        )
                        canvas.drawLine(cx, y, cx, y + h, borderPaint)

                        cx -= eColAmount
                        val amtPaint = Paint(rowTextCenterPaint).apply {
                            color = expenseRed
                            typeface = cairoBold
                        }
                        canvas.drawText("${block.amount} دج", cx + eColAmount / 2f, y + h / 2f + 3f, amtPaint)
                    }

                    is ActivityFinancePdfBlock.ExpensesTableTotal -> {
                        val h = block.height
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, totalBgPaint)
                        canvas.drawRect(leftMargin, y, rightMargin, y + h, borderPaint)

                        val labelColLeft = rightMargin - (eColNum + eColDesc + eColCat)
                        val totalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = darkGreen
                            textSize = 9.5f
                            typeface = cairoBold
                            textAlign = Paint.Align.RIGHT
                        }
                        canvas.drawText("الإجمالي", rightMargin - 8f, y + 16f, totalLabelPaint)
                        canvas.drawLine(labelColLeft, y, labelColLeft, y + h, borderPaint)

                        val amtPaint = Paint(rowTextCenterPaint).apply {
                            color = expenseRed
                            textSize = 9.5f
                            typeface = cairoBold
                        }
                        canvas.drawText("${block.totalAmount} دج", leftMargin + eColAmount / 2f, y + 16f, amtPaint)
                    }

                    is ActivityFinancePdfBlock.Gap -> {}
                }
                y += block.height
            }

            drawScoutPdfFooter(
                canvas = canvas,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                currentPage = pageNumber,
                totalPages = totalPages,
                cairoFont = cairoFont,
                terms = terms
            )

            pdfDocument.finishPage(page)
        }

        val file = File(context.cacheDir, "activity_finance_${activity.id}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return file
    }

    fun shareAttendanceFormPdf(
        file: File,
        activityTitle: String,
        context: Context,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ) {
        sharePdfFile(
            file = file,
            subject = "استمارة تسجيل الحضور: $activityTitle — ${terms.groupName} ${terms.unitNameDefinite}",
            chooserTitle = "طباعة / مشاركة استمارة الحضور كـ PDF",
            context = context
        )
    }

    /**
     * استمارة تسجيل الحضور القابلة للطباعة (صفحة واحدة فقط - A4 عمودي)
     */
    fun exportAttendanceFormPdf(
        activityTitle: String,
        scouts: List<Scout>,
        debtsMap: Map<String, Int>,
        context: Context,
        leaderNames: String = "",
        activityDateMillis: Long? = null,
        terms: CustomTerms = currentTerms ?: CustomTerms()
    ): File {
        val pdfDocument = PdfDocument()
        val cairoFont = getCairoTypeface(context) ?: Typeface.DEFAULT
        val cairoBold = getBoldCairoTypeface(cairoFont) ?: Typeface.DEFAULT_BOLD

        // صفحة واحدة A4 عمودي (Portrait: 595 x 842) مع هوامش جانبية 20pt
        val pageWidth = 595
        val pageHeight = 842
        val tableLeft = 20f
        val tableRight = 575f

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // 1. الترويسة الدقيقة (TASK 1)
        // الجانب الأيمن (محاذاة لليمين x = 565)
        val rightTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = cairoBold
            textAlign = Paint.Align.RIGHT
        }
        val rightSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            typeface = cairoFont
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("الكشافة الإسلامية الجزائرية", 565f, 35f, rightTitlePaint)
        canvas.drawText("فوج محمد رحال عموشة", 565f, 50f, rightSubPaint)
        canvas.drawText("${terms.groupName} ${terms.unitNameDefinite}", 565f, 65f, rightSubPaint)

        // المنتصف (محاذاة للوسط x = 297.5)
        val maxLogoSize = 50f
        val logoBitmap = getLogoBitmap(context)
        if (logoBitmap != null) {
            val origW = logoBitmap.width.toFloat()
            val origH = logoBitmap.height.toFloat()
            val aspectRatio = if (origH > 0f) origW / origH else 1f
            val (drawW, drawH) = if (aspectRatio >= 1f) {
                maxLogoSize to (maxLogoSize / aspectRatio)
            } else {
                (maxLogoSize * aspectRatio) to maxLogoSize
            }
            val logoX = (pageWidth - drawW) / 2f
            val logoY = 30f
            val logoPaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }
            canvas.drawBitmap(logoBitmap, null, RectF(logoX, logoY, logoX + drawW, logoY + drawH), logoPaint)
        }

        val centerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f
            typeface = cairoBold
            textAlign = Paint.Align.CENTER
        }
        val centerActivityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(27, 94, 32)
            textSize = 11f
            typeface = cairoBold
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("استمارة تسجيل الحضور", 297.5f, 95f, centerTitlePaint)
        canvas.drawText(truncateText("النشاط: $activityTitle", 535f, centerActivityPaint), 297.5f, 115f, centerActivityPaint)

        // الجانب الأيسر (محاذاة RTL لليمين عند x = 160)
        val leftHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            typeface = cairoBold
            textAlign = Paint.Align.RIGHT
        }
        val leftLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            typeface = cairoFont
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("القادة المسؤولون:", 160f, 35f, leftHeaderPaint)
        canvas.drawText("\u200F1. ......................", 160f, 55f, leftLinePaint)
        canvas.drawText("\u200F2. ......................", 160f, 70f, leftLinePaint)
        canvas.drawText("\u200F3. ......................", 160f, 85f, leftLinePaint)
        canvas.drawText("\u200F4. ......................", 160f, 100f, leftLinePaint)

        // 2. أبعاد الجدول بدءاً من startY = 130 (عرض الجدول = 555pt من x = 20 إلى x = 575)
        val startY = 130f
        val availableHeight = pageHeight - startY - 40f // 672f
        val headerHeight = 40f
        val availableDataHeight = (availableHeight - headerHeight).coerceAtLeast(24f)
        val rowHeight = (availableDataHeight / (maxOf(1, scouts.size) + 1).toFloat()).coerceAtMost(24f)

        // عرض الأعمدة الخمسة بالضبط (المجموع = 555pt من اليمين إلى اليسار)
        val colSerialWidth = 35f
        val colNameWidth = 135f
        val colDebtWidth = 45f
        val colStatusWidth = 235f // 5 × 47f
        val colAmountWidth = 105f
        val subStatusWidth = colStatusWidth / 5f // 47f

        val colSerialLeft = tableRight - colSerialWidth       // 540f
        val colNameLeft = colSerialLeft - colNameWidth        // 405f
        val colDebtLeft = colNameLeft - colDebtWidth          // 360f
        val colStatusLeft = colDebtLeft - colStatusWidth      // 125f
        val colAmountLeft = colStatusLeft - colAmountWidth    // 20f (= tableLeft)

        val gridPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val headerBgPaint = Paint().apply {
            color = Color.rgb(232, 245, 233)
            style = Paint.Style.FILL
        }
        val evenRowBgPaint = Paint().apply {
            color = Color.rgb(248, 249, 250)
            style = Paint.Style.FILL
        }
        val checkboxFillPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        // 3. رسم صف الترويسة
        val headerBottom = startY + headerHeight
        val midHeaderY = startY + 15f
        canvas.drawRect(tableLeft, startY, tableRight, headerBottom, headerBgPaint)

        val thCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9f
            typeface = cairoBold
            textAlign = Paint.Align.CENTER
        }
        val thRightPaint = Paint(thCenterPaint).apply {
            textAlign = Paint.Align.RIGHT
        }
        val thFm = thCenterPaint.fontMetrics
        val headerFullTextY = startY + headerHeight / 2f - (thFm.ascent + thFm.descent) / 2f

        canvas.drawText("الرقم", tableRight - 6f, headerFullTextY, thRightPaint)
        canvas.drawText("اسم ${terms.scoutSingularDefinite}", colSerialLeft - 6f, headerFullTextY, thRightPaint)

        val debtHeaderPaint = Paint(thCenterPaint).apply {
            textSize = 8f
        }
        val debtHeaderLines = wrapText("الدين السابق", debtHeaderPaint, colDebtWidth - 4f).ifEmpty { listOf("الدين السابق") }
        val debtLineStep = 10.5f
        val debtHeaderFm = debtHeaderPaint.fontMetrics
        val debtTotalH = debtHeaderLines.size * debtLineStep
        val debtStartY = startY + (headerHeight - debtTotalH) / 2f + debtLineStep / 2f - (debtHeaderFm.ascent + debtHeaderFm.descent) / 2f
        debtHeaderLines.forEachIndexed { lineIdx, line ->
            canvas.drawText(line, colDebtLeft + colDebtWidth / 2f, debtStartY + lineIdx * debtLineStep, debtHeaderPaint)
        }

        canvas.drawText("المبلغ", colAmountLeft + colAmountWidth / 2f, headerFullTextY, thCenterPaint)

        val topTierTextY = startY + (midHeaderY - startY) / 2f - (thFm.ascent + thFm.descent) / 2f
        canvas.drawText("حالة الحضور", colStatusLeft + colStatusWidth / 2f, topTierTextY, thCenterPaint)

        val statuses = listOf("حاضر", "متأخر", "متأخر جداً", "غائب بعذر", "غائب بدون عذر")
        val subThPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 7f
            typeface = cairoBold
            textAlign = Paint.Align.CENTER
        }
        val subFm = subThPaint.fontMetrics
        val subLineStep = 9.5f
        val bottomTierHeight = headerBottom - midHeaderY

        canvas.drawLine(colStatusLeft, midHeaderY, colDebtLeft, midHeaderY, gridPaint)

        statuses.forEachIndexed { idx, label ->
            val subRight = colDebtLeft - idx * subStatusWidth
            val subLeft = subRight - subStatusWidth
            val subCenterX = subLeft + subStatusWidth / 2f
            val lines = wrapText(label, subThPaint, subStatusWidth - 4f).ifEmpty { listOf(label) }
            val totalTextHeight = lines.size * subLineStep
            val startBaselineY = midHeaderY + (bottomTierHeight - totalTextHeight) / 2f + subLineStep / 2f - (subFm.ascent + subFm.descent) / 2f
            lines.forEachIndexed { lineIdx, line ->
                canvas.drawText(line, subCenterX, startBaselineY + lineIdx * subLineStep, subThPaint)
            }
            if (idx > 0) {
                canvas.drawLine(subRight, midHeaderY, subRight, headerBottom, gridPaint)
            }
        }

        // حدود الترويسة
        canvas.drawRect(tableLeft, startY, tableRight, headerBottom, gridPaint)
        canvas.drawLine(colSerialLeft, startY, colSerialLeft, headerBottom, gridPaint)
        canvas.drawLine(colNameLeft, startY, colNameLeft, headerBottom, gridPaint)
        canvas.drawLine(colDebtLeft, startY, colDebtLeft, headerBottom, gridPaint)
        canvas.drawLine(colStatusLeft, startY, colStatusLeft, headerBottom, gridPaint)

        // 4. رسم صفوف الأشبال
        val dataTextSize = (rowHeight * 0.50f).coerceIn(6.2f, 9.5f)
        val serialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = dataTextSize
            typeface = cairoFont
            textAlign = Paint.Align.RIGHT
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = dataTextSize
            typeface = cairoBold
            textAlign = Paint.Align.RIGHT
        }
        val debtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = dataTextSize
            typeface = cairoFont
            textAlign = Paint.Align.CENTER
        }
        val rowFm = namePaint.fontMetrics
        val boxSize = (rowHeight * 0.62f).coerceIn(6.5f, 12f)
        val boxHalf = boxSize / 2f

        val scoutRowsCount = if (scouts.isEmpty()) 1 else scouts.size
        if (scouts.isEmpty()) {
            val rowTop = headerBottom
            val rowBottom = rowTop + rowHeight
            canvas.drawRect(tableLeft, rowTop, tableRight, rowBottom, gridPaint)
            val emptyY = rowTop + rowHeight / 2f - (rowFm.ascent + rowFm.descent) / 2f
            canvas.drawText("لا يوجد ${terms.scoutPlural} نشطون مسجلون حالياً", pageWidth / 2f, emptyY, debtPaint)
        } else {
            scouts.forEachIndexed { index, scout ->
                val rowTop = headerBottom + index * rowHeight
                val rowBottom = rowTop + rowHeight
                val centerY = rowTop + rowHeight / 2f
                val textY = centerY - (rowFm.ascent + rowFm.descent) / 2f

                if (index % 2 == 1) {
                    canvas.drawRect(tableLeft, rowTop, tableRight, rowBottom, evenRowBgPaint)
                }

                // 1) الرقم
                canvas.drawText("${index + 1}", tableRight - 6f, textY, serialPaint)

                // 2) اسم الشبل
                val fullName = scout.fullName.ifBlank { "${scout.name} ${scout.surname}".trim() }.ifBlank { terms.scoutSingular }
                val safeName = truncateText(fullName, colNameWidth - 10f, namePaint)
                canvas.drawText(safeName, colSerialLeft - 6f, textY, namePaint)

                // 3) الدين السابق
                val prevDebt = debtsMap[scout.id] ?: 0
                val debtStr = if (prevDebt > 0) "$prevDebt" else "-"
                debtPaint.typeface = if (prevDebt > 0) cairoBold else cairoFont
                canvas.drawText(debtStr, colDebtLeft + colDebtWidth / 2f, textY, debtPaint)

                // 4) حالة الحضور: 5 مربعات اختيار فارغة جنباً إلى جنب
                for (statusIdx in 0 until 5) {
                    val subRight = colDebtLeft - statusIdx * subStatusWidth
                    val subLeft = subRight - subStatusWidth
                    val boxCenterX = subLeft + subStatusWidth / 2f
                    val rectLeft = boxCenterX - boxHalf
                    val rectTop = centerY - boxHalf
                    val rectRight = boxCenterX + boxHalf
                    val rectBottom = centerY + boxHalf
                    canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, checkboxFillPaint)
                    canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, gridPaint)
                    if (statusIdx > 0) {
                        canvas.drawLine(subRight, rowTop, subRight, rowBottom, gridPaint)
                    }
                }

                // 5) المبلغ: خلية فارغة تماماً بدون أي مستطيل داخلي للكتابة اليدوية

                // حدود الصف وخطوط الأعمدة
                canvas.drawRect(tableLeft, rowTop, tableRight, rowBottom, gridPaint)
                canvas.drawLine(colSerialLeft, rowTop, colSerialLeft, rowBottom, gridPaint)
                canvas.drawLine(colNameLeft, rowTop, colNameLeft, rowBottom, gridPaint)
                canvas.drawLine(colDebtLeft, rowTop, colDebtLeft, rowBottom, gridPaint)
                canvas.drawLine(colStatusLeft, rowTop, colStatusLeft, rowBottom, gridPaint)
            }
        }

        // 5. صف الإجمالي النهائي: دمج الأعمدة الـ 4 الأولى وترك عمود المبلغ فارغاً تماماً
        val totalRowTop = headerBottom + scoutRowsCount * rowHeight
        val totalRowBottom = totalRowTop + rowHeight
        canvas.drawRect(colStatusLeft, totalRowTop, tableRight, totalRowBottom, headerBgPaint)

        val totalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = minOf(12f, (rowHeight * 0.65f).coerceAtLeast(8f))
            typeface = cairoBold
            textAlign = Paint.Align.RIGHT
        }
        val totalFm = totalLabelPaint.fontMetrics
        val totalTextY = totalRowTop + rowHeight / 2f - (totalFm.ascent + totalFm.descent) / 2f
        canvas.drawText("إجمالي المبلغ المستلم:", tableRight - 10f, totalTextY, totalLabelPaint)

        // حدود صف الإجمالي والفاصل بين الخلية المدمجة وعمود المبلغ (بدون أي مستطيل داخلي في عمود المبلغ)
        canvas.drawRect(tableLeft, totalRowTop, tableRight, totalRowBottom, gridPaint)
        canvas.drawLine(colStatusLeft, totalRowTop, colStatusLeft, totalRowBottom, gridPaint)

        // تذييل خفيف أسفل الهامش
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 8f
            typeface = cairoFont
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "صفحة 1 من 1 — ${terms.groupName} ${terms.unitNameDefinite}",
            pageWidth / 2f,
            pageHeight - 12f,
            footerPaint
        )

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "attendance_form_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        return file
    }
}



