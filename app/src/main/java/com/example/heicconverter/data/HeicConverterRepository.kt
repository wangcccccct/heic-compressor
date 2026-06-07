package com.example.heicconverter.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaCodecList
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.heifwriter.HeifWriter
import com.example.heicconverter.model.ConversionItemResult
import com.example.heicconverter.model.ConversionSettings
import com.example.heicconverter.model.EncoderCapability
import com.example.heicconverter.model.EntrySource
import com.example.heicconverter.model.InputImage
import com.example.heicconverter.model.MetadataStatus
import com.example.heicconverter.model.OutputImage
import com.example.heicconverter.model.PreviewComparison
import com.example.heicconverter.model.ReplacementStatus
import com.example.heicconverter.util.outputNameFor
import java.io.File
import java.io.IOException
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class HeicConverterRepository(private val context: Context) {
  private val contentResolver: ContentResolver = context.contentResolver
  private val outputDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
  private val previewDirectory = File(context.cacheDir, "previews").apply { mkdirs() }
  private val supportedMimeTypes = setOf("image/jpeg", "image/jpg", "image/png", "image/webp", "image/bmp")
  private val heifMimeCandidates = listOf("image/vnd.android.heic", "image/heic", "image/heif")

  suspend fun detectEncoderCapability(): EncoderCapability =
    withContext(Dispatchers.Default) {
      val supportsHeif =
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { codecInfo ->
          codecInfo.isEncoder && codecInfo.supportedTypes.any { type -> heifMimeCandidates.any { candidate -> candidate.equals(type, ignoreCase = true) } }
        }

      if (supportsHeif) {
        EncoderCapability.Supported
      } else {
        EncoderCapability.Unsupported("当前系统没有公开可用的 HEIC 编码器。")
      }
    }

  suspend fun resolveInputs(uris: List<Uri>, source: EntrySource): List<InputImage> =
    withContext(Dispatchers.IO) {
      uris.distinctBy { it.toString() }.map { uri ->
        val mimeType = contentResolver.getType(uri).orEmpty()
        val metadata = queryInputMetadata(uri)
        val displayName = metadata.displayName ?: "shared-${System.currentTimeMillis()}.jpg"
        val isAlreadyHeic = mimeType.contains("heic", ignoreCase = true) || mimeType.contains("heif", ignoreCase = true)
        val isSupported = mimeType.lowercase() in supportedMimeTypes
        val panoramaHint = detectPanoramaHint(uri, metadata.width, metadata.height)
        val reason =
          when {
            isAlreadyHeic -> "这张图已经是 HEIC/HEIF。"
            mimeType.isBlank() -> "无法识别图片类型。"
            !isSupported -> "暂不支持 $mimeType。首版支持 JPG / PNG / WebP / BMP。"
            else -> null
          }
        InputImage(
          id = uri.toString(),
          uri = uri,
          displayName = displayName,
          mimeType = mimeType.ifBlank { "unknown" },
          sizeBytes = metadata.size,
          width = metadata.width,
          height = metadata.height,
          source = source,
          isSupported = reason == null,
          isPanoramaLike = panoramaHint != null,
          panoramaHint = panoramaHint,
          supportMessage = reason,
        )
      }
    }

  suspend fun generatePreview(item: InputImage, settings: ConversionSettings): PreviewComparison =
    withContext(Dispatchers.IO) {
      val previewFile = File(previewDirectory, uniqueOutputName(item.displayName, directory = previewDirectory, prefix = "preview_"))
      encodeHeic(item, settings, previewFile)
      val outputBytes = previewFile.length()
      PreviewComparison(
        input = item,
        outputFile = previewFile,
        outputBytes = outputBytes,
        replacementWouldBeSkipped = shouldSkipReplacementBecauseLarger(item.sizeBytes, outputBytes, settings),
      )
    }

  suspend fun convert(
    items: List<InputImage>,
    settings: ConversionSettings,
    onProgress: suspend (completed: Int, total: Int, currentLabel: String) -> Unit,
  ): List<ConversionItemResult> = withContext(Dispatchers.IO) {
    val results = mutableListOf<ConversionItemResult>()
    items.forEachIndexed { index, item ->
      ensureActive()
      onProgress(index, items.size, item.displayName)
      results +=
        if (!item.isSupported) {
          ConversionItemResult.Failure(item, item.supportMessage ?: "不支持的输入")
        } else if (settings.skipPanoramaLike && item.isPanoramaLike) {
          ConversionItemResult.Failure(item, item.panoramaHint ?: "检测到疑似全景/360 图片，已按设置跳过。")
        } else {
          runCatching { convertSingle(item, settings) }
            .getOrElse { throwable ->
              ConversionItemResult.Failure(item, throwable.message ?: "转换失败")
            }
        }
    }
    onProgress(items.size, items.size, "已完成")
    results
  }

  suspend fun saveToGallery(result: ConversionItemResult.Success): Uri = withContext(Dispatchers.IO) {
    val output = result.output ?: throw IOException("当前结果没有可保存的 HEIC 文件")
    val values =
      ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, output.file.name)
        put(MediaStore.MediaColumns.MIME_TYPE, output.mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HEIC Converter")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
      }

    val targetUri =
      contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("无法创建系统相册目标文件")

    contentResolver.openOutputStream(targetUri)?.use { stream ->
      output.file.inputStream().use { input -> input.copyTo(stream) }
    } ?: throw IOException("无法写入系统相册")

    values.clear()
    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
    contentResolver.update(targetUri, values, null, null)
    targetUri
  }

  private fun convertSingle(item: InputImage, settings: ConversionSettings): ConversionItemResult.Success {
    val outputFile = File(outputDirectory, uniqueOutputName(item.displayName, directory = outputDirectory))
    val metadataStatus = encodeHeic(item, settings, outputFile)
    val outputBytes = outputFile.length()
    var output: OutputImage? =
      OutputImage(
        file = outputFile,
        shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile),
      )
    var replacementStatus = ReplacementStatus.NOT_REQUESTED
    var replacementUri: Uri? = null
    var replacementMessage: String? = null

    if (settings.replaceOriginals) {
      if (shouldSkipReplacementBecauseLarger(item.sizeBytes, outputBytes, settings)) {
        replacementStatus = ReplacementStatus.SKIPPED_LARGER
        replacementMessage = "按设置未替换：生成的 HEIC 没有比原图更小。"
        if (!settings.keepLargerHeicCopy) {
          outputFile.delete()
          output = null
        }
      } else {
        val replacement = createReplacement(item, outputFile)
        replacementStatus = replacement.first
        replacementUri = replacement.second
        replacementMessage = replacement.third
      }
    }

    return ConversionItemResult.Success(
      input = item,
      output = output,
      originalBytes = item.sizeBytes,
      outputBytes = outputBytes,
      metadataStatus = metadataStatus,
      replacementStatus = replacementStatus,
      replacementUri = replacementUri,
      replacementMessage = replacementMessage,
    )
  }

  private fun createReplacement(item: InputImage, outputFile: File): Triple<ReplacementStatus, Uri?, String?> {
    val metadata = queryInputMetadata(item.uri)
    val values =
      ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, outputNameFor(item.displayName))
        put(MediaStore.MediaColumns.MIME_TYPE, "image/heic")
        put(MediaStore.MediaColumns.RELATIVE_PATH, metadata.relativePath ?: Environment.DIRECTORY_PICTURES + "/HEIC Converter")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
        metadata.dateTaken?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
      }

    val targetUri =
      contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return Triple(ReplacementStatus.CREATED_COPY_ONLY, null, "无法创建替换文件，已保留 HEIC 副本。")

    contentResolver.openOutputStream(targetUri)?.use { stream ->
      outputFile.inputStream().use { input -> input.copyTo(stream) }
    } ?: return Triple(ReplacementStatus.CREATED_COPY_ONLY, targetUri, "无法写入替换文件，已保留 HEIC 副本。")

    values.clear()
    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
    contentResolver.update(targetUri, values, null, null)

    return try {
      val deleted = contentResolver.delete(item.uri, null, null)
      if (deleted > 0) {
        Triple(ReplacementStatus.REPLACED, targetUri, "已创建 HEIC 并删除原图。")
      } else {
        Triple(ReplacementStatus.CREATED_COPY_ONLY, targetUri, "已创建 HEIC，但系统没有删除原图。")
      }
    } catch (_: SecurityException) {
      Triple(ReplacementStatus.CREATED_COPY_ONLY, targetUri, "已创建 HEIC，但系统权限不足，未删除原图。")
    }
  }

  private fun encodeHeic(item: InputImage, settings: ConversionSettings, outputFile: File): MetadataStatus {
    val bitmap = decodeBitmap(item.uri)
    try {
      val writer =
        HeifWriter.Builder(outputFile.absolutePath, bitmap.width, bitmap.height, HeifWriter.INPUT_MODE_BITMAP)
          .setQuality(settings.quality)
          .setMaxImages(1)
          .setPrimaryIndex(0)
          .build()
      try {
        writer.start()
        writer.addBitmap(bitmap)
        writer.stop(8_000)
      } finally {
        writer.close()
      }
    } finally {
      bitmap.recycle()
    }

    return if (settings.preserveMetadata) copyExif(item.uri, outputFile) else MetadataStatus.SKIPPED
  }

  private fun shouldSkipReplacementBecauseLarger(originalBytes: Long?, outputBytes: Long, settings: ConversionSettings): Boolean {
    return settings.replaceOriginals && settings.skipReplaceWhenLarger && originalBytes != null && outputBytes >= originalBytes
  }

  private fun copyExif(sourceUri: Uri, targetFile: File): MetadataStatus {
    return try {
      contentResolver.openInputStream(sourceUri)?.use { inputStream ->
        val sourceExif = ExifInterface(inputStream)
        val targetExif = ExifInterface(targetFile.absolutePath)
        exifKeys.forEach { key -> sourceExif.getAttribute(key)?.let { targetExif.setAttribute(key, it) } }
        targetExif.saveAttributes()
      }
      MetadataStatus.COPIED
    } catch (_: Throwable) {
      MetadataStatus.PARTIAL
    }
  }

  private fun decodeBitmap(uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      decoder.isMutableRequired = false
      val maxDimension = maxOf(info.size.width, info.size.height)
      if (maxDimension > 4096) {
        decoder.setTargetSampleSize(ceil(maxDimension / 4096.0).toInt())
      }
    }
  }

  private fun queryInputMetadata(uri: Uri): SourceMetadata {
    val projection =
      arrayOf(
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT,
        MediaStore.MediaColumns.RELATIVE_PATH,
        MediaStore.Images.Media.DATE_TAKEN,
      )
    return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
      if (!cursor.moveToFirst()) return@use SourceMetadata()
      SourceMetadata(
        displayName = cursor.getStringOrNull(0),
        size = cursor.getLongOrNull(1),
        width = cursor.getIntOrNull(2),
        height = cursor.getIntOrNull(3),
        relativePath = cursor.getStringOrNull(4),
        dateTaken = cursor.getLongOrNull(5),
      )
    } ?: SourceMetadata().copy(width = readBounds(uri).first, height = readBounds(uri).second)
  }

  private fun readBounds(uri: Uri): Pair<Int?, Int?> {
    return try {
      contentResolver.openInputStream(uri)?.use { input ->
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, options)
        options.outWidth.takeIf { it > 0 } to options.outHeight.takeIf { it > 0 }
      } ?: (null to null)
    } catch (_: Throwable) {
      null to null
    }
  }

  private fun detectPanoramaHint(uri: Uri, width: Int?, height: Int?): String? {
    val ratioHint =
      if (width != null && height != null && height > 0 && width.toFloat() / height.toFloat() >= 2f) {
        "检测到超宽比例，疑似全景图。"
      } else {
        null
      }

    val xmpHint =
      try {
        contentResolver.openInputStream(uri)?.use { input ->
          val sample = input.readBytes().decodeToString()
          when {
            sample.contains("GPano", ignoreCase = true) -> "检测到 GPano 元数据，疑似全景/360 图。"
            sample.contains("equirectangular", ignoreCase = true) -> "检测到 equirectangular 标记，疑似 360 图。"
            else -> null
          }
        }
      } catch (_: Throwable) {
        null
      }

    return xmpHint ?: ratioHint
  }

  private fun uniqueOutputName(displayName: String, directory: File, prefix: String = ""): String {
    val desired = prefix + outputNameFor(displayName)
    val base = desired.substringBeforeLast('.')
    val ext = desired.substringAfterLast('.', "heic")
    var candidate = desired
    var index = 1
    while (File(directory, candidate).exists()) {
      candidate = "${base}_$index.$ext"
      index += 1
    }
    return candidate
  }

  private fun android.database.Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

  private fun android.database.Cursor.getLongOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

  private fun android.database.Cursor.getIntOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)

  private data class SourceMetadata(
    val displayName: String? = null,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val relativePath: String? = null,
    val dateTaken: Long? = null,
  )

  companion object {
    private val exifKeys =
      listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_IMAGE_LENGTH,
      )
  }
}
