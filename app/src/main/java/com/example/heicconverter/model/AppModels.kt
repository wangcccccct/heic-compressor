package com.example.heicconverter.model

import android.net.Uri
import java.io.File

enum class AppScreen {
  HOME,
  CONFIGURE,
  CONVERTING,
  RESULTS,
  PREVIEW,
}

enum class EntrySource {
  LOCAL_PICKER,
  EXTERNAL_SHARE_SINGLE,
  EXTERNAL_SHARE_MULTI,
}

data class ConversionSettings(
  val quality: Int = 80,
  val preserveMetadata: Boolean = true,
  val replaceOriginals: Boolean = false,
  val skipPanoramaLike: Boolean = false,
  val skipReplaceWhenLarger: Boolean = true,
  val keepLargerHeicCopy: Boolean = false,
)

data class InputImage(
  val id: String,
  val uri: Uri,
  val trashUri: Uri? = null,
  val displayName: String,
  val mimeType: String,
  val sizeBytes: Long?,
  val width: Int? = null,
  val height: Int? = null,
  val source: EntrySource,
  val isSupported: Boolean,
  val isPanoramaLike: Boolean = false,
  val panoramaHint: String? = null,
  val supportMessage: String? = null,
)

data class ConversionProgress(
  val completed: Int = 0,
  val total: Int = 0,
  val currentLabel: String? = null,
  val successCount: Int = 0,
  val failureCount: Int = 0,
)

sealed interface EncoderCapability {
  data object Checking : EncoderCapability

  data object Supported : EncoderCapability

  data class Unsupported(val message: String) : EncoderCapability
}

enum class MetadataStatus {
  COPIED,
  SKIPPED,
  PARTIAL,
}

enum class ReplacementStatus {
  NOT_REQUESTED,
  REPLACED,
  CREATED_COPY_ONLY,
  PENDING_TRASH,
  TRASHED,
  SKIPPED_LARGER,
}

data class OutputImage(
  val file: File,
  val shareUri: Uri,
  val mimeType: String = "image/heic",
)

sealed interface ConversionItemResult {
  val input: InputImage

  data class Success(
    override val input: InputImage,
    val output: OutputImage?,
    val originalBytes: Long?,
    val outputBytes: Long,
    val metadataStatus: MetadataStatus,
    val replacementStatus: ReplacementStatus = ReplacementStatus.NOT_REQUESTED,
    val replacementUri: Uri? = null,
    val replacementMessage: String? = null,
    val savedUri: Uri? = null,
  ) : ConversionItemResult

  data class Failure(
    override val input: InputImage,
    val reason: String,
  ) : ConversionItemResult
}

data class SessionSummary(
  val successCount: Int,
  val failureCount: Int,
  val originalBytes: Long,
  val outputBytes: Long,
) {
  val savedBytes: Long = originalBytes - outputBytes
}

data class UiMessage(
  val id: Long = System.currentTimeMillis(),
  val text: String,
)

data class TrashRequest(
  val id: Long = System.currentTimeMillis(),
  val uris: List<Uri>,
  val inputIds: List<String>,
)

data class PreviewComparison(
  val input: InputImage,
  val outputFile: File,
  val outputBytes: Long,
  val replacementWouldBeSkipped: Boolean,
)

data class PreviewUiState(
  val sourceScreen: AppScreen,
  val input: InputImage,
  val isLoading: Boolean = true,
  val comparison: PreviewComparison? = null,
  val errorMessage: String? = null,
)

data class ConverterUiState(
  val screen: AppScreen = AppScreen.HOME,
  val encoderCapability: EncoderCapability = EncoderCapability.Checking,
  val selectedImages: List<InputImage> = emptyList(),
  val settings: ConversionSettings = ConversionSettings(),
  val progress: ConversionProgress = ConversionProgress(),
  val results: List<ConversionItemResult> = emptyList(),
  val previewState: PreviewUiState? = null,
  val recentSummary: SessionSummary? = null,
  val pendingMessage: UiMessage? = null,
  val pendingTrashRequest: TrashRequest? = null,
) {
  val successResults: List<ConversionItemResult.Success>
    get() = results.filterIsInstance<ConversionItemResult.Success>()

  val failureResults: List<ConversionItemResult.Failure>
    get() = results.filterIsInstance<ConversionItemResult.Failure>()
}
