package com.example.heicconverter.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Panorama
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.heicconverter.model.AppScreen
import com.example.heicconverter.model.ConversionItemResult
import com.example.heicconverter.model.ConverterUiState
import com.example.heicconverter.model.EncoderCapability
import com.example.heicconverter.model.EntrySource
import com.example.heicconverter.model.MetadataStatus
import com.example.heicconverter.model.PreviewUiState
import com.example.heicconverter.model.ReplacementStatus
import com.example.heicconverter.util.formatBytes
import com.example.heicconverter.util.formatCompressionDelta
import com.example.heicconverter.util.formatCompressionRatio
import com.example.heicconverter.util.formatDimensions
import com.example.heicconverter.util.formatReplacementStatus
import com.example.heicconverter.util.formatSourceLabel

@Composable
fun HeicConverterRoute(viewModel: ConverterViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  val singlePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      uri?.let { viewModel.importUris(listOf(it), EntrySource.LOCAL_PICKER) }
    }
  val multiPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
      if (uris.isNotEmpty()) viewModel.importUris(uris, EntrySource.LOCAL_PICKER)
    }

  val pendingMessage = uiState.pendingMessage
  LaunchedEffect(pendingMessage?.id) {
    pendingMessage?.let {
      snackbarHostState.showSnackbar(it.text)
      viewModel.clearMessage(it)
    }
  }

  HeicConverterScreen(
    uiState = uiState,
    snackbarHostState = snackbarHostState,
    onPickSingle = { singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    onPickMultiple = { multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    onStartConversion = viewModel::startConversion,
    onCancelConversion = viewModel::cancelConversion,
    onQualityChange = viewModel::setQuality,
    onPreserveMetadataChange = viewModel::setPreserveMetadata,
    onReplaceOriginalsChange = viewModel::setReplaceOriginals,
    onSkipPanoramaLikeChange = viewModel::setSkipPanoramaLike,
    onSkipReplaceWhenLargerChange = viewModel::setSkipReplaceWhenLarger,
    onKeepLargerHeicCopyChange = viewModel::setKeepLargerHeicCopy,
    onOpenPreview = viewModel::openPreview,
    onOpenResultPreview = viewModel::openPreview,
    onClosePreview = viewModel::closePreview,
    onBack = {
      when (uiState.screen) {
        AppScreen.CONFIGURE -> viewModel.backToHome()
        AppScreen.RESULTS -> viewModel.backToConfigure()
        AppScreen.PREVIEW -> viewModel.closePreview()
        else -> viewModel.backToHome()
      }
    },
    onBackHome = viewModel::backToHome,
    onSaveAll = viewModel::saveAll,
    onSaveSmaller = viewModel::saveSmallerResults,
    onSaveOne = viewModel::saveOne,
    onShareOne = { result -> result.output?.shareUri?.let { shareFiles(context, listOf(it), true) } },
    onShareAll = {
      val uris = uiState.successResults.mapNotNull { it.output?.shareUri }
      shareFiles(context, uris, uris.size <= 1)
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeicConverterScreen(
  uiState: ConverterUiState,
  snackbarHostState: SnackbarHostState,
  onPickSingle: () -> Unit,
  onPickMultiple: () -> Unit,
  onStartConversion: () -> Unit,
  onCancelConversion: () -> Unit,
  onQualityChange: (Float) -> Unit,
  onPreserveMetadataChange: (Boolean) -> Unit,
  onReplaceOriginalsChange: (Boolean) -> Unit,
  onSkipPanoramaLikeChange: (Boolean) -> Unit,
  onSkipReplaceWhenLargerChange: (Boolean) -> Unit,
  onKeepLargerHeicCopyChange: (Boolean) -> Unit,
  onOpenPreview: (com.example.heicconverter.model.InputImage) -> Unit,
  onOpenResultPreview: (ConversionItemResult.Success) -> Unit,
  onClosePreview: () -> Unit,
  onBack: () -> Unit,
  onBackHome: () -> Unit,
  onSaveAll: () -> Unit,
  onSaveSmaller: () -> Unit,
  onSaveOne: (ConversionItemResult.Success) -> Unit,
  onShareOne: (ConversionItemResult.Success) -> Unit,
  onShareAll: () -> Unit,
) {
  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar =
      if (uiState.screen == AppScreen.HOME) {
        {}
      } else {
        {
          CenterAlignedTopAppBar(
            title = {
              Text(
                when (uiState.screen) {
                  AppScreen.HOME -> "HEIC 压缩"
                  AppScreen.CONFIGURE -> "转换设置"
                  AppScreen.CONVERTING -> "正在转换"
                  AppScreen.RESULTS -> "转换结果"
                  AppScreen.PREVIEW -> "预览对比"
                }
              )
            },
            navigationIcon = {
              IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            },
          )
        }
      },
  ) { innerPadding ->
    AnimatedContent(
      targetState = uiState.screen,
      transitionSpec = { fadeIn() togetherWith fadeOut() },
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      label = "app-screen",
    ) { screen ->
      when (screen) {
        AppScreen.HOME -> HomeScreen(uiState, onPickSingle, onPickMultiple)
        AppScreen.CONFIGURE ->
          ConfigureScreen(
            uiState = uiState,
            onQualityChange = onQualityChange,
            onPreserveMetadataChange = onPreserveMetadataChange,
            onReplaceOriginalsChange = onReplaceOriginalsChange,
            onSkipPanoramaLikeChange = onSkipPanoramaLikeChange,
            onSkipReplaceWhenLargerChange = onSkipReplaceWhenLargerChange,
            onKeepLargerHeicCopyChange = onKeepLargerHeicCopyChange,
            onStartConversion = onStartConversion,
            onOpenPreview = onOpenPreview,
          )
        AppScreen.CONVERTING -> ConvertingScreen(uiState, onCancelConversion)
        AppScreen.RESULTS -> ResultsScreen(uiState, onShareAll, onSaveAll, onSaveSmaller, onBackHome, onShareOne, onSaveOne, onOpenResultPreview)
        AppScreen.PREVIEW -> PreviewScreen(uiState.previewState, uiState.settings, onClosePreview)
      }
    }
  }
}

@Composable
private fun HomeScreen(uiState: ConverterUiState, onPickSingle: () -> Unit, onPickMultiple: () -> Unit) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    item {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
              Brush.linearGradient(
                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
              )
            )
            .padding(horizontal = 24.dp, vertical = 28.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text(
            "HEIC 压缩",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
          )
          Text("把照片压成 HEIC", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onPrimary)
          Text(
            "支持系统分享导入、无限量批量选择、拖拽对比预览，以及按条件替换原图。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
          )
        }
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = onPickSingle, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(22.dp)) {
          Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
          Spacer(Modifier.width(12.dp))
          Text("选择一张图片")
        }
        Button(onClick = onPickMultiple, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(22.dp)) {
          Icon(Icons.Rounded.Collections, contentDescription = null)
          Spacer(Modifier.width(12.dp))
          Text("批量转换（不限张数）")
        }
      }
    }

    item {
      HomeSupportPanel(uiState)
    }
  }
}

@Composable
private fun HomeSupportPanel(uiState: ConverterUiState) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("使用建议", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("实拍照片通常最有收益；截图、全景和 360 图片更适合先预览后再决定是否替换原图。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      SupportRow(
        title = "系统分享直达",
        body = "在相册、文件管理器或聊天 App 里点“分享”，直接选本 App。",
        leading = Icons.Rounded.Share,
      )
      SupportRow(
        title = "支持的输入格式",
        body = "当前支持 JPG / PNG / WebP / BMP；已是 HEIC/HEIF 的图片会自动跳过。",
        leading = Icons.Rounded.ImageSearch,
      )
      uiState.recentSummary?.let { summary ->
        SupportRow(
          title = "最近一次结果",
          body = "成功 ${summary.successCount}，失败 ${summary.failureCount}，净节省 ${formatBytes(summary.savedBytes)}。",
          leading = Icons.Rounded.AutoAwesome,
        )
      }
    }
  }
}

@Composable
private fun SupportRow(title: String, body: String, leading: androidx.compose.ui.graphics.vector.ImageVector) {
  Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    FilledIconButton(onClick = {}, modifier = Modifier.size(44.dp), shape = CircleShape) {
      Icon(leading, contentDescription = null)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfigureScreen(
  uiState: ConverterUiState,
  onQualityChange: (Float) -> Unit,
  onPreserveMetadataChange: (Boolean) -> Unit,
  onReplaceOriginalsChange: (Boolean) -> Unit,
  onSkipPanoramaLikeChange: (Boolean) -> Unit,
  onSkipReplaceWhenLargerChange: (Boolean) -> Unit,
  onKeepLargerHeicCopyChange: (Boolean) -> Unit,
  onStartConversion: () -> Unit,
  onOpenPreview: (com.example.heicconverter.model.InputImage) -> Unit,
) {
  val canConvertAny =
    uiState.selectedImages.any { input ->
      input.isSupported && !(uiState.settings.skipPanoramaLike && input.isPanoramaLike)
    }
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("已选内容", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = true, onClick = {}, label = { Text(formatSourceLabel(uiState.selectedImages.size)) })
          FilterChip(
            selected = true,
            onClick = {},
            label = {
              Text(
                when (uiState.selectedImages.firstOrNull()?.source) {
                  EntrySource.EXTERNAL_SHARE_MULTI, EntrySource.EXTERNAL_SHARE_SINGLE -> "来自系统分享"
                  else -> "来自本地选择"
                }
              )
            },
          )
          when (val capability = uiState.encoderCapability) {
            EncoderCapability.Checking -> FilterChip(selected = true, onClick = {}, label = { Text("正在检测编码器") })
            is EncoderCapability.Supported -> FilterChip(selected = true, onClick = {}, label = { Text("设备支持 HEIC 编码") })
            is EncoderCapability.Unsupported -> FilterChip(selected = true, onClick = {}, label = { Text("编码器不可用") })
          }
        }
      }
    }

    if (uiState.selectedImages.isNotEmpty()) {
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(uiState.selectedImages, key = { it.id }) { image ->
            Surface(
              modifier = Modifier.width(146.dp).clickable { onOpenPreview(image) },
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              shape = RoundedCornerShape(24.dp),
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(
                  model = image.uri,
                  contentDescription = image.displayName,
                  modifier = Modifier.size(124.dp).clip(RoundedCornerShape(20.dp)),
                  contentScale = ContentScale.Crop,
                )
                Text(image.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                Text(formatDimensions(image.width, image.height), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(image.supportMessage ?: formatBytes(image.sizeBytes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (image.isPanoramaLike) {
                  ElevatedFilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text("疑似全景/360") },
                    leadingIcon = { Icon(Icons.Rounded.Panorama, contentDescription = null) },
                  )
                }
                Text(
                  if (image.isSupported) "点按生成预览" else "不支持转换",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (image.isSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
              }
            }
          }
        }
      }
    }

    item {
      Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(30.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
          Text("压缩质量", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
          Text("100 代表最高质量，但仍然是重新编码。", color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("${uiState.settings.quality}", style = MaterialTheme.typography.displaySmall)
          Slider(
            value = uiState.settings.quality.toFloat(),
            onValueChange = onQualityChange,
            valueRange = 50f..100f,
          )
        }
      }
    }

    item {
      Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(30.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("行为选项", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
          SettingSwitchRow(
            title = "保留基础 EXIF",
            body = "尽量保留拍摄时间、地点、机型、曝光等元数据。",
            checked = uiState.settings.preserveMetadata,
            onCheckedChange = onPreserveMetadataChange,
          )
          SettingSwitchRow(
            title = "替换原图（实验性）",
            body = "成功后尝试在同相册位置创建 HEIC，并删除原图。系统权限不足时会只保留新 HEIC。",
            checked = uiState.settings.replaceOriginals,
            onCheckedChange = onReplaceOriginalsChange,
          )
          SettingSwitchRow(
            title = "疑似全景 / 360 图自动跳过",
            body = "避免转换后丢失全景或 360 识别能力。",
            checked = uiState.settings.skipPanoramaLike,
            onCheckedChange = onSkipPanoramaLikeChange,
          )
          SettingSwitchRow(
            title = "输出大于原图时不替换",
            body = "只在“替换原图”开启时生效。更大的 HEIC 默认不回写替换。",
            checked = uiState.settings.skipReplaceWhenLarger,
            onCheckedChange = onSkipReplaceWhenLargerChange,
            enabled = uiState.settings.replaceOriginals,
          )
          SettingSwitchRow(
            title = "输出更大时保留 HEIC 副本",
            body = "关闭后，遇到更大的 HEIC 会直接丢弃结果文件。",
            checked = uiState.settings.keepLargerHeicCopy,
            onCheckedChange = onKeepLargerHeicCopyChange,
            enabled = uiState.settings.replaceOriginals && uiState.settings.skipReplaceWhenLarger,
          )
        }
      }
    }

    item {
      val capability = uiState.encoderCapability
      val disabled = capability !is EncoderCapability.Supported || !canConvertAny
      when (capability) {
        EncoderCapability.Checking -> Text("正在检测设备 HEIC 编码能力，完成后即可开始。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        is EncoderCapability.Unsupported -> Text(capability.message, color = MaterialTheme.colorScheme.error)
        EncoderCapability.Supported -> Unit
      }
      Button(onClick = onStartConversion, enabled = !disabled, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(24.dp)) {
        Icon(Icons.Rounded.Bolt, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(if (capability is EncoderCapability.Checking) "检测编码器中…" else "开始转换")
      }
    }
  }
}

@Composable
private fun SettingSwitchRow(
  title: String,
  body: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  enabled: Boolean = true,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
      Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
  }
}

@Composable
private fun ConvertingScreen(uiState: ConverterUiState, onCancelConversion: () -> Unit) {
  val progressValue = if (uiState.progress.total == 0) 0f else uiState.progress.completed / uiState.progress.total.toFloat()
  Column(
    modifier = Modifier.fillMaxSize().padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(30.dp)) {
      Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("正在压缩为 HEIC", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("当前：${uiState.progress.currentLabel ?: "准备中"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { progressValue.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
        Text("${uiState.progress.completed} / ${uiState.progress.total}", style = MaterialTheme.typography.titleLarge)
        Text(
          "成功 ${uiState.progress.successCount} · 跳过/失败 ${uiState.progress.failureCount} · 剩余 ${(uiState.progress.total - uiState.progress.completed).coerceAtLeast(0)}",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    OutlinedButton(onClick = onCancelConversion, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(22.dp)) {
      Text("取消本轮任务")
    }
  }
}

@Composable
private fun ResultsScreen(
  uiState: ConverterUiState,
  onShareAll: () -> Unit,
  onSaveAll: () -> Unit,
  onSaveSmaller: () -> Unit,
  onBackHome: () -> Unit,
  onShareOne: (ConversionItemResult.Success) -> Unit,
  onSaveOne: (ConversionItemResult.Success) -> Unit,
  onOpenPreview: (ConversionItemResult.Success) -> Unit,
) {
  val shareableCount = uiState.successResults.count { it.output != null }
  val smallerCount = uiState.successResults.count { it.output != null && it.originalBytes != null && it.outputBytes < it.originalBytes }
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    uiState.recentSummary?.let { summary ->
      item {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(32.dp))
              .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.primaryContainer)))
              .padding(24.dp),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("本轮结果", style = MaterialTheme.typography.titleLarge)
            Text(formatBytes(summary.savedBytes), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text("成功 ${summary.successCount} · 失败 ${summary.failureCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }

    item {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (shareableCount > 0) {
          if (smallerCount > 0) {
            FilledTonalButton(onClick = onSaveSmaller, shape = RoundedCornerShape(20.dp)) {
              Icon(Icons.Rounded.SaveAlt, contentDescription = null)
              Spacer(Modifier.width(10.dp))
              Text("只保存更小结果（$smallerCount）")
            }
          }
          FilledTonalButton(onClick = onSaveAll, shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Rounded.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("全部保存")
          }
          Button(onClick = onShareAll, shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Rounded.IosShare, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(if (shareableCount > 1) "批量分享" else "分享结果")
          }
        }
        OutlinedButton(onClick = onBackHome, shape = RoundedCornerShape(20.dp)) { Text("再来一轮") }
      }
    }

    items(uiState.results, key = { it.input.id + it::class.simpleName }) { result ->
      when (result) {
        is ConversionItemResult.Success -> SuccessRow(result, onShareOne, onSaveOne, onOpenPreview)
        is ConversionItemResult.Failure -> FailureRow(result)
      }
    }
  }
}

@Composable
private fun SuccessRow(
  result: ConversionItemResult.Success,
  onShareOne: (ConversionItemResult.Success) -> Unit,
  onSaveOne: (ConversionItemResult.Success) -> Unit,
  onOpenPreview: (ConversionItemResult.Success) -> Unit,
) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(28.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AsyncImage(
          model = result.input.uri,
          contentDescription = result.input.displayName,
          modifier = Modifier.size(86.dp).clip(RoundedCornerShape(22.dp)),
          contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(result.input.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text("原始 ${formatBytes(result.originalBytes)} → 输出 ${formatBytes(result.outputBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("${formatCompressionDelta(result.originalBytes, result.outputBytes)} · ${formatCompressionRatio(result.originalBytes, result.outputBytes)}")
          Text(
            when (result.metadataStatus) {
              MetadataStatus.COPIED -> "已尝试保留 EXIF"
              MetadataStatus.SKIPPED -> "未保留 EXIF"
              MetadataStatus.PARTIAL -> "部分元数据未保留"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          formatReplacementStatus(result.replacementStatus, result.replacementMessage)?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          }
        }
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(onClick = { onOpenPreview(result) }, shape = RoundedCornerShape(18.dp)) {
          Icon(Icons.Rounded.Compare, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("预览对比")
        }
        if (result.output != null) {
          FilledTonalButton(onClick = { onSaveOne(result) }, shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Rounded.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (result.savedUri == null) "保存到相册" else "已保存")
          }
          Button(onClick = { onShareOne(result) }, shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Rounded.IosShare, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("分享结果")
          }
        }
      }
    }
  }
}

@Composable
private fun FailureRow(result: ConversionItemResult.Failure) {
  Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), shape = RoundedCornerShape(28.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(result.input.displayName, style = MaterialTheme.typography.titleMedium)
      Text(result.reason, color = MaterialTheme.colorScheme.onErrorContainer)
    }
  }
}

@Composable
private fun PreviewScreen(previewState: PreviewUiState?, settings: com.example.heicconverter.model.ConversionSettings, onClosePreview: () -> Unit) {
  if (previewState == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("没有可预览的内容")
    }
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(30.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(previewState.input.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
          Text(
            "原图 ${formatBytes(previewState.input.sizeBytes)} · ${formatDimensions(previewState.input.width, previewState.input.height)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text("双指缩放 / 拖动画面；拖动中线对比原图与 HEIC。", color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (previewState.input.isPanoramaLike) {
            Text(previewState.input.panoramaHint ?: "疑似全景/360 图", color = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }

    when {
      previewState.isLoading -> {
        item {
          Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(30.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("正在生成 HEIC 预览…", style = MaterialTheme.typography.titleMedium)
              LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
          }
        }
      }
      previewState.errorMessage != null -> {
        item {
          Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(30.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text(previewState.errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
              OutlinedButton(onClick = onClosePreview) { Text("返回") }
            }
          }
        }
      }
      previewState.comparison != null -> {
        val comparison = previewState.comparison
        item {
          Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(30.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("HEIC 预览 ${formatBytes(comparison.outputBytes)}", style = MaterialTheme.typography.titleLarge)
              Text(
                "${formatCompressionDelta(previewState.input.sizeBytes, comparison.outputBytes)} · ${formatCompressionRatio(previewState.input.sizeBytes, comparison.outputBytes)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (settings.replaceOriginals && comparison.replacementWouldBeSkipped) {
                Text("按当前设置，这张图转换后不会替换原图。", color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
        item {
          ComparePreview(
            original = previewState.input.uri,
            processed = comparison.outputFile,
            aspectRatio = aspectRatioFor(previewState.input.width, previewState.input.height),
          )
        }
      }
    }
  }
}

@Composable
private fun ComparePreview(original: Uri, processed: Any, aspectRatio: Float) {
  var dividerFraction by remember { mutableFloatStateOf(0.5f) }
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  var containerSize by remember { mutableStateOf(IntSize.Zero) }

  Surface(shape = RoundedCornerShape(30.dp), color = Color.Black.copy(alpha = 0.88f)) {
    BoxWithConstraints(
      modifier =
        Modifier
          .fillMaxWidth()
          .aspectRatio(aspectRatio)
          .onSizeChanged { containerSize = it }
          .pointerInput(containerSize) {
            detectTransformGestures { _, pan, zoom, _ ->
              val newScale = (scale * zoom).coerceIn(1f, 4f)
              scale = newScale
              offset = clampOffset(offset + pan, containerSize, newScale)
            }
          },
    ) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
          model = original,
          contentDescription = "原图",
          modifier = Modifier.matchParentSize().graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
          },
          contentScale = ContentScale.Fit,
        )
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(dividerFraction).clip(RoundedCornerShape(30.dp))) {
          AsyncImage(
            model = processed,
            contentDescription = "HEIC 预览",
            modifier = Modifier.matchParentSize().graphicsLayer {
              scaleX = scale
              scaleY = scale
              translationX = offset.x
              translationY = offset.y
            },
            contentScale = ContentScale.Fit,
          )
        }
        Box(
          modifier =
            Modifier
              .fillMaxHeight()
              .width(28.dp)
              .align(Alignment.CenterStart)
              .graphicsLayer { translationX = (containerSize.width * dividerFraction) - 14.dp.toPx() }
              .pointerInput(containerSize, dividerFraction) {
                detectDragGestures { change, dragAmount ->
                  change.consume()
                  val width = containerSize.width.toFloat().coerceAtLeast(1f)
                  dividerFraction = (dividerFraction + dragAmount.x / width).coerceIn(0.1f, 0.9f)
                }
              },
          contentAlignment = Alignment.Center,
        ) {
          Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.85f)))
          Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Icon(Icons.Rounded.Compare, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(8.dp).size(18.dp))
          }
        }
      }
    }
  }
}

private fun clampOffset(offset: Offset, size: IntSize, scale: Float): Offset {
  if (size == IntSize.Zero) return Offset.Zero
  val maxX = (size.width * (scale - 1f)) / 2f
  val maxY = (size.height * (scale - 1f)) / 2f
  return Offset(
    x = offset.x.coerceIn(-maxX, maxX),
    y = offset.y.coerceIn(-maxY, maxY),
  )
}

private fun aspectRatioFor(width: Int?, height: Int?): Float {
  return if (width != null && height != null && width > 0 && height > 0) {
    (width.toFloat() / height.toFloat()).coerceIn(0.5f, 2.4f)
  } else {
    1f
  }
}

private fun shareFiles(context: Context, uris: List<Uri>, single: Boolean) {
  if (uris.isEmpty()) return
  val intent =
    if (single || uris.size == 1) {
      Intent(Intent.ACTION_SEND).apply {
        type = "image/heic"
        putExtra(Intent.EXTRA_STREAM, uris.first())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    } else {
      Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/heic"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    }
  context.startActivity(Intent.createChooser(intent, "分享 HEIC 文件"))
}
