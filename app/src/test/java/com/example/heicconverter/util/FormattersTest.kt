package com.example.heicconverter.util

import com.example.heicconverter.model.ConversionItemResult
import com.example.heicconverter.model.EntrySource
import com.example.heicconverter.model.InputImage
import com.example.heicconverter.model.MetadataStatus
import com.example.heicconverter.model.OutputImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test

class FormattersTest {
  @Test
  fun formatBytes_formatsMegabytes() {
    assertEquals("1 MB", formatBytes(1024 * 1024))
  }

  @Test
  fun outputNameFor_normalizesExtension() {
    assertEquals("Vacation_2026.heic", outputNameFor("Vacation 2026.JPG"))
  }

  @Ignore("JVM unit test lacks a real Android Uri implementation")
  @Test
  fun summarize_returnsSuccessCounts() {
    val input =
      InputImage(
        id = "1",
        uri = android.net.Uri.EMPTY,
        displayName = "photo.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 2048,
        source = EntrySource.LOCAL_PICKER,
        isSupported = true,
      )
    val success =
      ConversionItemResult.Success(
        input = input,
        output = OutputImage(File("/tmp/a.heic"), android.net.Uri.EMPTY),
        originalBytes = 2048,
        outputBytes = 1024,
        metadataStatus = MetadataStatus.COPIED,
      )
    val summary = summarize(listOf(success))
    assertNotNull(summary)
    assertEquals(1, summary?.successCount)
    assertEquals(1024, summary?.savedBytes)
  }
}
