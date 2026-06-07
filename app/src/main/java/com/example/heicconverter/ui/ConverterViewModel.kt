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
import com.example.heicconverter.model.EntrySource
import com.example.heicconverter.model.PreviewUiState
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
            progress = it.progress.copy(completed = 0, total = images.size, currentLabel = images.firstOrNull()?.displayName),
            results = emptyList(),
            previewState = null,
          )
        }

        runCatching {
          repository.convert(
            items = images,
            settings = _uiState.value.settings,
            onProgress = { completed, total, currentLabel ->
              _uiState.update { state -> state.copy(progress = state.progress.copy(completed = completed, total = total, currentLabel = currentLabel)) }
            },
          )
        }
          .onSuccess { results ->
            val summary = summarize(results)
            val successCount = results.count { it is ConversionItemResult.Success }
            val failureCount = results.count { it is ConversionItemResult.Failure }
            _uiState.update {
              it.copy(
                screen = AppScreen.RESULTS,
                results = results,
                recentSummary = summary ?: it.recentSummary,
                progress = it.progress.copy(completed = results.size, total = results.size, currentLabel = "已完成"),
                pendingMessage =
                  UiMessage(
                    text =
                      when {
                        summary == null -> "本轮没有生成可分享的 HEIC 文件。"
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
              pendingMessage = UiMessage(text = "已保存到系统相册。"),
            )
          }
        }
        .onFailure { postMessage(it.message ?: "保存失败") }
    }
  }

  fun saveAll() {
    val successItems = _uiState.value.successResults.filter { it.output != null }
    if (successItems.isEmpty()) {
      postMessage("没有可保存的结果。")
      return
    }
    viewModelScope.launch {
      var savedCount = 0
      var failedCount = 0
      successItems.forEach { result ->
        runCatching { repository.saveToGallery(result) }
          .onSuccess { savedUri ->
            savedCount += 1
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
      postMessage(
        if (failedCount == 0) {
          "已保存 $savedCount / ${successItems.size} 个文件到相册。"
        } else {
          "已保存 $savedCount / ${successItems.size} 个文件，${failedCount} 个保存失败。"
        },
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
        progress = it.progress.copy(completed = 0, total = 0, currentLabel = null),
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
