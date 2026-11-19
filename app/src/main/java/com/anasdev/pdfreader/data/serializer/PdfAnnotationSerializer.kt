package com.anasdev.pdfreader.data.serializer

import com.anasdev.pdfreader.data.local.PdfAnnotationEntity
import com.anasdev.pdfreader.domain.model.FreehandStroke
import com.anasdev.pdfreader.domain.model.NormalizedPoint
import com.anasdev.pdfreader.domain.model.NormalizedRect
import com.anasdev.pdfreader.domain.model.PdfAnnotation
import com.anasdev.pdfreader.domain.model.Signature
import com.anasdev.pdfreader.domain.model.TextBox
import com.anasdev.pdfreader.domain.model.TextHighlight
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfAnnotationSerializer @Inject constructor() {

    fun serialize(fileUri: String, annotation: PdfAnnotation): PdfAnnotationEntity {
        val (type, payloadJson) = when (annotation) {
            is FreehandStroke -> {
                val json = JSONObject().apply {
                    put("colorArgb", annotation.colorArgb)
                    put("strokeWidthRatio", annotation.strokeWidthRatio.toDouble())
                    put("isHighlighter", annotation.isHighlighter)
                    val pointsArray = JSONArray()
                    for (point in annotation.points) {
                        val pointObj = JSONObject().apply {
                            put("x", point.clampedX.toDouble())
                            put("y", point.clampedY.toDouble())
                        }
                        pointsArray.put(pointObj)
                    }
                    put("points", pointsArray)
                }
                TYPE_FREEHAND_STROKE to json.toString()
            }
            is TextBox -> {
                val json = JSONObject().apply {
                    put("xNorm", annotation.xNorm.toDouble())
                    put("yNorm", annotation.yNorm.toDouble())
                    put("widthNorm", annotation.widthNorm.toDouble())
                    put("text", annotation.text)
                    put("fontSizeRatio", annotation.fontSizeRatio.toDouble())
                    put("colorArgb", annotation.colorArgb)
                }
                TYPE_TEXT_BOX to json.toString()
            }
            is TextHighlight -> {
                val json = JSONObject().apply {
                    put("colorArgb", annotation.colorArgb)
                    val rectsArray = JSONArray()
                    for (rect in annotation.rects) {
                        val rectObj = JSONObject().apply {
                            put("left", rect.left.coerceIn(0f, 1f).toDouble())
                            put("top", rect.top.coerceIn(0f, 1f).toDouble())
                            put("right", rect.right.coerceIn(0f, 1f).toDouble())
                            put("bottom", rect.bottom.coerceIn(0f, 1f).toDouble())
                        }
                        rectsArray.put(rectObj)
                    }
                    put("rects", rectsArray)
                }
                TYPE_TEXT_HIGHLIGHT to json.toString()
            }
            is Signature -> {
                val json = JSONObject().apply {
                    put("xNorm", annotation.xNorm.toDouble())
                    put("yNorm", annotation.yNorm.toDouble())
                    put("widthNorm", annotation.widthNorm.toDouble())
                    put("aspectRatio", annotation.aspectRatio.toDouble())
                    put("strokeWidthRatio", annotation.strokeWidthRatio.toDouble())
                    put("colorArgb", annotation.colorArgb)

                    val strokesArray = JSONArray()
                    for (stroke in annotation.strokes) {
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
                TYPE_SIGNATURE to json.toString()
            }
        }

        return PdfAnnotationEntity(
            id = annotation.id,
            fileUri = fileUri,
            pageIndex = annotation.pageIndex,
            type = type,
            payloadJson = payloadJson,
            createdAt = annotation.createdAt
        )
    }

    fun deserialize(entity: PdfAnnotationEntity): PdfAnnotation? {
        return try {
            when (entity.type) {
                TYPE_FREEHAND_STROKE -> {
                    val json = JSONObject(entity.payloadJson)
                    val colorArgb = json.getInt("colorArgb")
                    val strokeWidthRatio = json.getDouble("strokeWidthRatio").toFloat()
                    val isHighlighter = json.optBoolean("isHighlighter", false)
                    val pointsArray = json.getJSONArray("points")
                    val points = ArrayList<NormalizedPoint>(pointsArray.length())
                    for (i in 0 until pointsArray.length()) {
                        val pointObj = pointsArray.getJSONObject(i)
                        val x = pointObj.getDouble("x").toFloat().coerceIn(0f, 1f)
                        val y = pointObj.getDouble("y").toFloat().coerceIn(0f, 1f)
                        points.add(NormalizedPoint(x, y))
                    }
                    FreehandStroke(
                        id = entity.id,
                        pageIndex = entity.pageIndex,
                        createdAt = entity.createdAt,
                        points = points,
                        colorArgb = colorArgb,
                        strokeWidthRatio = strokeWidthRatio,
                        isHighlighter = isHighlighter
                    )
                }
                TYPE_TEXT_BOX -> {
                    val json = JSONObject(entity.payloadJson)
                    TextBox(
                        id = entity.id,
                        pageIndex = entity.pageIndex,
                        createdAt = entity.createdAt,
                        xNorm = json.getDouble("xNorm").toFloat().coerceIn(0f, 1f),
                        yNorm = json.getDouble("yNorm").toFloat().coerceIn(0f, 1f),
                        widthNorm = json.getDouble("widthNorm").toFloat().coerceIn(0.12f, 0.95f),
                        text = json.getString("text"),
                        fontSizeRatio = json.getDouble("fontSizeRatio").toFloat().coerceIn(0.015f, 0.08f),
                        colorArgb = json.getInt("colorArgb")
                    )
                }
                TYPE_TEXT_HIGHLIGHT -> {
                    val json = JSONObject(entity.payloadJson)
                    val colorArgb = json.getInt("colorArgb")
                    val rectsArray = json.getJSONArray("rects")
                    val rects = ArrayList<NormalizedRect>(rectsArray.length())
                    for (i in 0 until rectsArray.length()) {
                        val rectObj = rectsArray.getJSONObject(i)
                        val left = rectObj.getDouble("left").toFloat().coerceIn(0f, 1f)
                        val top = rectObj.getDouble("top").toFloat().coerceIn(0f, 1f)
                        val right = rectObj.getDouble("right").toFloat().coerceIn(0f, 1f)
                        val bottom = rectObj.getDouble("bottom").toFloat().coerceIn(0f, 1f)
                        rects.add(NormalizedRect(left, top, right, bottom))
                    }
                    TextHighlight(
                        id = entity.id,
                        pageIndex = entity.pageIndex,
                        createdAt = entity.createdAt,
                        rects = rects,
                        colorArgb = colorArgb
                    )
                }
                TYPE_SIGNATURE -> {
                    val json = JSONObject(entity.payloadJson)
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
                    Signature(
                        id = entity.id,
                        pageIndex = entity.pageIndex,
                        createdAt = entity.createdAt,
                        xNorm = json.getDouble("xNorm").toFloat().coerceIn(0f, 1f),
                        yNorm = json.getDouble("yNorm").toFloat().coerceIn(0f, 1f),
                        widthNorm = json.getDouble("widthNorm").toFloat().coerceIn(0.10f, 0.90f),
                        aspectRatio = json.getDouble("aspectRatio").toFloat().coerceIn(0.4f, 6.0f),
                        strokes = strokes,
                        strokeWidthRatio = json.optDouble("strokeWidthRatio", 0.004).toFloat(),
                        colorArgb = json.getInt("colorArgb")
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        const val TYPE_FREEHAND_STROKE = "FREEHAND_STROKE"
        const val TYPE_TEXT_BOX = "TEXT_BOX"
        const val TYPE_TEXT_HIGHLIGHT = "TEXT_HIGHLIGHT"
        const val TYPE_SIGNATURE = "SIGNATURE"
    }
}
