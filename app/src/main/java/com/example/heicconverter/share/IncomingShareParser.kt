package com.example.heicconverter.share

import android.content.Intent
import android.net.Uri
import com.example.heicconverter.model.EntrySource

object IncomingShareParser {
  fun parse(intent: Intent?): Pair<EntrySource, List<Uri>>? {
    if (intent == null) return null
    val uris = buildList {
      when (intent.action) {
        Intent.ACTION_SEND -> {
          intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
          intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
              clip.getItemAt(index).uri?.let(::add)
            }
          }
        }
        Intent.ACTION_SEND_MULTIPLE -> {
          intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.forEach(::add)
          intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
              clip.getItemAt(index).uri?.let(::add)
            }
          }
        }
      }
    }
      .distinctBy { it.toString() }

    if (uris.isEmpty()) return null

    val source =
      if (intent.action == Intent.ACTION_SEND_MULTIPLE || uris.size > 1) {
        EntrySource.EXTERNAL_SHARE_MULTI
      } else {
        EntrySource.EXTERNAL_SHARE_SINGLE
      }
    return source to uris
  }
}
