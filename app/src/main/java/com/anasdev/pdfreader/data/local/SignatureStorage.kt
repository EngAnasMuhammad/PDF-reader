package com.anasdev.pdfreader.data.local

import android.content.Context
import com.anasdev.pdfreader.domain.model.NormalizedPoint
import com.anasdev.pdfreader.domain.model.SavedSignature
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignatureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val signatureFile: File
        get() = File(context.filesDir, FILE_NAME)

    suspend fun loadSavedSignature(): SavedSignature? = withContext(Dispatchers.IO) {
        try {
            val file = signatureFile
            if (!file.exists() || !file.isFile) return@withContext null
            val content = file.readText()
            if (content.isBlank()) return@withContext null

            val json = JSONObject(content)
            val aspectRatio = json.getDouble("aspectRatio").toFloat().coerceIn(0.4f, 6.0f)
            val colorArgb = json.getInt("colorArgb")
            val strokesArray = json.getJSONArray("strokes")

            val strokes = ArrayList<List<NormalizedPoint>>(strokesArray.length())
            for (i in 0 until strokesArray.length()) {
                val strokePointsArray = strokesArray.getJSONArray(i)
                val stroke = ArrayList<NormalizedPoint>(strokePointsArray.length())
                for (j in 0 until strokePointsArray.length()) {
                    val ptObj = strokePointsArray.getJSONObject(j)
                    val x = ptObj.getDouble("x").toFloat().coerceIn(0f, 1f)
                    val y = ptObj.getDouble("y").toFloat().coerceIn(0f, 1f)
                    stroke.add(NormalizedPoint(x, y))
                }
                strokes.add(stroke)
            }

            SavedSignature(
                strokes = strokes,
                aspectRatio = aspectRatio,
                colorArgb = colorArgb
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveSignature(signature: SavedSignature) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("aspectRatio", signature.aspectRatio.toDouble())
                put("colorArgb", signature.colorArgb)
                val strokesArray = JSONArray()
                for (stroke in signature.strokes) {
                    val strokePoints = JSONArray()
                    for (pt in stroke) {
                        val ptObj = JSONObject().apply {
                            put("x", pt.clampedX.toDouble())
                            put("y", pt.clampedY.toDouble())
                        }
                        strokePoints.put(ptObj)
                    }
                    strokesArray.put(strokePoints)
                }
                put("strokes", strokesArray)
            }

            val file = signatureFile
            val tempFile = File(context.filesDir, "$FILE_NAME.tmp")
            tempFile.writeText(json.toString())
            if (tempFile.renameTo(file).not()) {
                // Fallback direct write if atomic rename fails on some OEM devices
                file.writeText(json.toString())
                tempFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearSavedSignature() = withContext(Dispatchers.IO) {
        try {
            val file = signatureFile
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val FILE_NAME = "saved_signature.json"
    }
}
