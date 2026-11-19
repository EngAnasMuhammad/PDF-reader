package com.anasdev.pdfreader.domain.tools

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MergePdfsHelper {

    /**
     * Sanitizes raw user input into a valid, safe merged PDF filename.
     * Replaces illegal file characters ([\\/:*?"<>|]) with underscores,
     * strips surrounding whitespace, ensures .pdf extension,
     * and falls back to a clean timestamped name (Merged_yyyyMMdd_HHmm.pdf) if blank.
     */
    fun sanitizeFileName(rawName: String): String {
        val sanitized = rawName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val baseName = sanitized
            .replace(Regex("(?i)\\.pdf$"), "")
            .trim()

        val finalBase = baseName.ifBlank {
            generateDefaultFileName()
        }

        return "$finalBase.pdf"
    }

    /**
     * Generates a clean default timestamped filename base without extension.
     */
    fun generateDefaultFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "Merged_$timeStamp"
    }

    /**
     * Validates whether the given list of PDF URIs satisfies the minimum requirement for merging.
     */
    fun validatePdfCount(count: Int): Boolean {
        return count >= 2
    }
}
