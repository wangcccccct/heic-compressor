package com.example.heicconverter.util

import com.example.heicconverter.model.ConversionItemResult
import com.example.heicconverter.model.ReplacementStatus
import com.example.heicconverter.model.SessionSummary
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.absoluteValue

private val decimalFormat = DecimalFormat("0.#")

fun formatBytes(bytes: Long?): String {
  if (bytes == null || bytes < 0) return "未知"
  if (bytes < 1024) return "$bytes B"
  val units = listOf("KB", "MB", "GB")
  var value = bytes.toDouble()
  var unitIndex = -1
  while (value >= 1024 && unitIndex < units.lastIndex) {
    value /= 1024.0
    unitIndex += 1
  }
  return "${decimalFormat.format(value)} ${units[unitIndex]}"
}

fun formatSourceLabel(count: Int): String = if (count <= 1) "1 张图片" else "$count 张图片"

fun outputNameFor(displayName: String): String {
  val base = displayName.substringBeforeLast('.').ifBlank { "image" }
  val normalized =
    base
      .replace(Regex("[^A-Za-z0-9\\u4E00-\\u9FA5._-]"), "_")
      .trim('_')
      .ifBlank { "image" }
  return "${normalized}.heic"
}

fun summarize(results: List<ConversionItemResult>): SessionSummary? {
  val successItems = results.filterIsInstance<ConversionItemResult.Success>()
  if (successItems.isEmpty()) return null
  val original = successItems.sumOf { it.originalBytes ?: 0L }
  val output = successItems.sumOf { it.outputBytes }
  return SessionSummary(
    successCount = successItems.size,
    failureCount = results.count { it is ConversionItemResult.Failure },
    originalBytes = original,
    outputBytes = output,
  )
}

fun formatCompressionDelta(originalBytes: Long?, outputBytes: Long): String {
  if (originalBytes == null || originalBytes <= 0) return formatBytes(outputBytes)
  val delta = outputBytes - originalBytes
  val sign = if (delta <= 0) "节省" else "增加"
  return "$sign ${formatBytes(delta.absoluteValue)}"
}

fun formatCompressionRatio(originalBytes: Long?, outputBytes: Long): String {
  if (originalBytes == null || originalBytes <= 0) return "未知"
  val ratio = 1 - outputBytes.toDouble() / originalBytes.toDouble()
  return String.format(Locale.US, "%+.0f%%", ratio * 100)
}

fun formatDimensions(width: Int?, height: Int?): String {
  if (width == null || height == null) return "尺寸未知"
  return "${width} × ${height}"
}

fun formatReplacementStatus(status: ReplacementStatus, message: String?): String? {
  return message ?: when (status) {
    ReplacementStatus.NOT_REQUESTED -> null
    ReplacementStatus.REPLACED -> "已替换原图。"
    ReplacementStatus.CREATED_COPY_ONLY -> "已生成 HEIC，但原图仍保留。"
    ReplacementStatus.PENDING_TRASH -> "已生成 HEIC，等待系统确认将原图移入回收站。"
    ReplacementStatus.TRASHED -> "已生成 HEIC，原图已移入系统回收站。"
    ReplacementStatus.SKIPPED_LARGER -> "按设置未替换原图。"
  }
}
