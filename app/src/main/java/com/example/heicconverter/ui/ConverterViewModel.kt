package com.example.heicconverter.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heicconverter.data.HeicConverterRepository
import com.example.heicconverter.model.AppScreen
import com.example.heicconverter.model.ConversionItemResult
import com.example.heicconverter.model.ConverterUiState
import com.example.heicconverter.model.EncoderCapability
import com.example.heicconverter.model.EntrySource
import com.example.heicconverter.model.PreviewUiState
import com.example.heicconverter.model.ReplacementStatus
import com.example.heicconverter.model.TrashRequest
import com.example.heicconverter.model.UiMessage
import com.example.heicconverter.share.IncomingShareParser
import com.example.heicconverter.util.summarize
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConverterViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = HeicConverterRepository(application)
  private val _uiState = MutableStateFlow(ConverterUiState())
  val uiState: StateFlow<ConverterUiState> = _uiState

  private var conversionJob: Job? = null
  private var previewJob: Job? = null
  private var lastHandledShareKey: String? = null
  private var activeTrashInputIds: Set<String> = emptySet()

  init {
    viewModelScope.launch {
      _uiState.update { it.copy(encoderCapability = repository.detectEncoderCapability()) }
    }
  }

  fun consumeIncomingIntent(intent: Intent?) {
    val parsed = IncomingShareParser.parse(intent) ?: return
    val dedupeKey = parsed.first.name + ":" + parsed.second.joinToString("|") { it.toString() }
    if (dedupeKey == lastHandledShareKey) return
    lastHandledShareKey = dedupeKey
    importUris(parsed.second, parsed.first)
  }

  fun importUris(uris: List<Uri>, source: EntrySource) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      val inputs = repository.resolveInputs(uris, source)
      _uiState.update {
        it.copy(
          screen = AppScreen.CONFIGURE,
          selectedImages = inputs,
          results = emptyList(),
          previewState = null,
          progress = it.progress.copy(completed = 0, total = 0, currentLabel = null),
          pendingMessage =
            UiMessage(
              text =
                if (source == EntrySource.LOCAL_PICKER) {
                  "已导入 ${inputs.size} 张图片。"
                } else {
                  "已接收系统分享的 ${inputs.size} 张图片。"
                },
            ),
        )
      }
    }
  }

  fun setQuality(value: Float) {
    _uiState.update { state -> state.copy(settings = state.settings.copy(quality = value.toInt().coerceIn(50, 100))) }
  }

  fun setPreserveMetadata(enabled: Boolean) {
    _uiState.update { state -> state.copy(settings = state.settings.copy(preserveMetadata = enabled)) }
  }

  fun setReplaceOriginals(enabled: Boolean) {
    _uiState.update { state -> state.copy(settings = state.settings.copy(replaceOriginals = enabled)) }
  }

  fun setSkipPanoramaLike(enabled: Boolean) {
    _uiState.update { state -> state.copy(settings = state.settings.copy(skipPanoramaLike = enabled)) }
  }

  fun setSkipReplaceWhenLarger(enabled: Boolean) {
    _uiState.update { state -> state.copy(settings = state.settings.copy(skipReplaceWhenLarger = enabled)) }
  }

  fun setKeepLargerHeicCopy(enabled: Boolean) {
    _uiState.update { state -> state.copy(settings = state.settings.copy(keepLargerHeicCopy = enabled)) }
  }

  fun openPreview(input: com.example.heicconverter.model.InputImage) {
    if (!input.isSupported) {
      postMessage(input.supportMessage ?: "这张图片暂不支持转换。")
      return
    }
    when (val capability = _uiState.value.encoderCapability) {
      EncoderCapability.Checking -> {
        postMessage("正在检测设备 HEIC 编码能力，请稍后再预览。")
        return
      }
      is EncoderCapability.Unsupported -> {
        postMessage(capability.message)
        return
      }
      EncoderCapability.Supported -> Unit
    }

    previewJob?.cancel()
    val sourceScreen = _uiState.value.screen
    _uiState.update {
      it.copy(
        screen = AppScreen.PREVIEW,
        previewState = PreviewUiState(sourceScreen = sourceScreen, input = input, isLoading = true),
      )
    }
    previewJob =
      viewModelScope.launch {
        runCatching { repository.generatePreview(input, _uiState.value.settings) }
          .onSuccess { comparison ->
            _uiState.update { state ->
              state.copy(
                previewState =
                  state.previewState?.copy(
                    isLoading = false,
                    comparison = comparison,
                    errorMessage = null,
                  ),
              )
            }
          }
          .onFailure { throwable ->
            _uiState.update { state ->
              state.copy(
                previewState =
                  state.previewState?.copy(
                    isLoading = false,
                    comparison = null,
                    errorMessage = throwable.message ?: "生成预览失败",
                  ),
              )
            }
          }
      }
  }

  fun openPreview(result: ConversionItemResult.Success) {
    val output = result.output ?: run {
      postMessage("这个结果没有保留可预览的 HEIC 文件。")
      return
    }
    _uiState.update {
      it.copy(
        screen = AppScreen.PREVIEW,
        previewState =
          PreviewUiState(
            sourceScreen = it.screen,
            input = result.input,
            isLoading = false,
            comparison =
              com.example.heicconverter.model.PreviewComparison(
                input = result.input,
                outputFile = output.file,
                outputBytes = result.outputBytes,
                replacementWouldBeSkipped = result.replacementStatus == com.example.heicconverter.model.ReplacementStatus.SKIPPED_LARGER,
              ),
          ),
      )
    }
  }

  fun closePreview() {
    val previous = _uiState.value.previewState?.sourceScreen ?: AppScreen.CONFIGURE
    previewJob?.cancel()
    _uiState.update { it.copy(screen = previous, previewState = null) }
  }

  fun startConversion() {
    when (val capability = _uiState.value.encoderCapability) {
      EncoderCapability.Checking -> {
        postMessage("正在检测设备 HEIC 编码能力，请稍后再开始。")
        return
      }
      is EncoderCapability.Unsupported -> {
        postMessage(capability.message)
        return
      }
      EncoderCapability.Supported -> Unit
    }

    val images = _uiState.value.selectedImages
    if (images.isEmpty()) {
      postMessage("先选几张图片再开始。")
      return
    }

    conversionJob?.cancel()
    conversionJob =
      viewModelScope.launch {
        _uiState.update {
          it.copy(
            screen = AppScreen.CONVERTING,
            progress =
              it.progress.copy(
                completed = 0,
                total = images.size,
                currentLabel = images.firstOrNull()?.displayName,
                successCount = 0,
                failureCount = 0,
              ),
            results = emptyList(),
            previewState = null,
          )
        }

        runCatching {
          repository.convert(
            items = images,
            settings = _uiState.value.settings,
            onProgress = { completed, total, currentLabel, successCount, failureCount ->
              _uiState.update { state ->
                state.copy(
                  progress =
                    state.progress.copy(
                      completed = completed,
                      total = total,
                      currentLabel = currentLabel,
                      successCount = successCount,
                      failureCount = failureCount,
                    ),
                )
              }
            },
          )
        }
          .onSuccess { results ->
            val summary = summarize(results)
            val successCount = results.count { it is ConversionItemResult.Success }
            val failureCount = results.count { it is ConversionItemResult.Failure }
            _uiState.update {
              val waitingForSaveCount =
                results
                  .filterIsInstance<ConversionItemResult.Success>()
                  .count { result -> result.replacementStatus == ReplacementStatus.PENDING_TRASH }
              it.copy(
                screen = AppScreen.RESULTS,
                results = results,
                recentSummary = summary ?: it.recentSummary,
                progress =
                  it.progress.copy(
                    completed = results.size,
                    total = results.size,
                    currentLabel = "已完成",
                    successCount = successCount,
                    failureCount = failureCount,
                  ),
                pendingMessage =
                  UiMessage(
                    text =
                      when {
                        summary == null -> "本轮没有生成可分享的 HEIC 文件。"
                        waitingForSaveCount > 0 -> "转换完成，成功 ${successCount} 张；保存结果后再请求系统回收站。"
                        failureCount == 0 -> "转换完成，成功 ${successCount} 张。"
                        else -> "转换完成：成功 ${successCount}，跳过/失败 ${failureCount}。"
                      },
                  ),
              )
            }
          }
          .onFailure {
            _uiState.update { state -> state.copy(screen = AppScreen.CONFIGURE) }
            postMessage(it.message ?: "转换中断")
          }
      }
  }

  fun cancelConversion() {
    conversionJob?.cancel()
    _uiState.update { state -> state.copy(screen = AppScreen.CONFIGURE, progress = state.progress.copy(currentLabel = null)) }
    postMessage("已取消本轮转换。")
  }

  fun saveOne(result: ConversionItemResult.Success) {
    if (result.savedUri != null) {
      postMessage("这个结果已经保存过了。")
      return
    }
    viewModelScope.launch {
      runCatching { repository.saveToGallery(result) }
        .onSuccess { savedUri ->
          _uiState.update { state ->
            state.copy(
              results =
                state.results.map {
                  if (it is ConversionItemResult.Success && it.input.id == result.input.id) {
                    it.copy(savedUri = savedUri)
                  } else {
                    it
                  }
                },
            )
          }
          queueTrashRequestAfterSave(listOf(result), baseMessage = "已保存到系统相册。")
        }
        .onFailure { postMessage(it.message ?: "保存失败") }
    }
  }

  fun saveAll() {
    saveResults(_uiState.value.successResults.filter { it.output != null && it.savedUri == null }, emptyMessage = "没有可保存的结果。")
  }

  fun clearTrashRequest(request: TrashRequest) {
    activeTrashInputIds = request.inputIds.toSet()
    _uiState.update { state -> if (state.pendingTrashRequest?.id == request.id) state.copy(pendingTrashRequest = null) else state }
  }

  fun onTrashRequestResult(approved: Boolean, message: String? = null) {
    val affectedInputIds = activeTrashInputIds
    activeTrashInputIds = emptySet()
    _uiState.update { state ->
      val updatedResults =
        if (approved) {
          state.results.map { result ->
            if (result is ConversionItemResult.Success && result.input.id in affectedInputIds && result.replacementStatus == ReplacementStatus.PENDING_TRASH) {
              result.copy(replacementStatus = ReplacementStatus.TRASHED, replacementMessage = "已通过系统回收站移走原图。")
            } else {
              result
            }
          }
        } else {
          state.results
        }
      state.copy(
        results = updatedResults,
        pendingMessage =
          UiMessage(
            text =
              message ?:
              if (approved) {
                "系统已确认，原图已移入回收站。"
              } else {
                "系统回收站请求未完成，原图仍保留。"
              },
          ),
      )
    }
  }

  fun saveSmallerResults() {
    val smallerResults =
      _uiState.value.successResults.filter {
        it.output != null && it.savedUri == null && it.originalBytes != null && it.outputBytes < it.originalBytes
      }
    saveResults(smallerResults, emptyMessage = "没有比原图更小的可保存结果。")
  }

  private fun saveResults(successItems: List<ConversionItemResult.Success>, emptyMessage: String) {
    if (successItems.isEmpty()) {
      postMessage(emptyMessage)
      return
    }
    viewModelScope.launch {
      var savedCount = 0
      var failedCount = 0
      val savedItems = mutableListOf<ConversionItemResult.Success>()
      successItems.forEach { result ->
        runCatching { repository.saveToGallery(result) }
          .onSuccess { savedUri ->
            savedCount += 1
            savedItems += result
            _uiState.update { state ->
              state.copy(
                results =
                  state.results.map {
                    if (it is ConversionItemResult.Success && it.input.id == result.input.id) {
                      it.copy(savedUri = savedUri)
                    } else {
                      it
                    }
                  },
              )
            }
          }
          .onFailure {
            failedCount += 1
          }
      }
      val message =
        if (failedCount == 0) {
          "已保存 $savedCount / ${successItems.size} 个文件到相册。"
        } else {
          "已保存 $savedCount / ${successItems.size} 个文件，${failedCount} 个保存失败。"
        }
      queueTrashRequestAfterSave(savedItems, baseMessage = message)
    }
  }

  private fun queueTrashRequestAfterSave(savedItems: List<ConversionItemResult.Success>, baseMessage: String) {
    val replaceCandidates = savedItems.filter { it.replacementStatus == ReplacementStatus.PENDING_TRASH }
    if (replaceCandidates.isEmpty()) {
      postMessage(baseMessage)
      return
    }

    val trashUris = replaceCandidates.mapNotNull { it.input.trashUri }.distinctBy { it.toString() }
    if (trashUris.isEmpty()) {
      postMessage("$baseMessage 但无法定位系统相册原图，原图仍保留。")
      return
    }

    _uiState.update {
      it.copy(
        pendingTrashRequest =
          TrashRequest(
            uris = trashUris,
            inputIds = replaceCandidates.map { result -> result.input.id },
          ),
        pendingMessage = UiMessage(text = "$baseMessage 请确认系统回收站请求。"),
      )
    }
  }

  fun backToHome() {
    previewJob?.cancel()
    _uiState.update {
      it.copy(
        screen = AppScreen.HOME,
        selectedImages = emptyList(),
        results = emptyList(),
        previewState = null,
        progress = it.progress.copy(completed = 0, total = 0, currentLabel = null, successCount = 0, failureCount = 0),
      )
    }
  }

  fun backToConfigure() {
    _uiState.update { it.copy(screen = AppScreen.CONFIGURE) }
  }

  fun clearMessage(message: UiMessage) {
    _uiState.update { state -> if (state.pendingMessage?.id == message.id) state.copy(pendingMessage = null) else state }
  }

  private fun postMessage(text: String) {
    _uiState.update { it.copy(pendingMessage = UiMessage(text = text)) }
  }
}
