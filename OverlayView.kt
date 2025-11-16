package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.speech.tts.TextToSpeech
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.*

class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private var results: List<BoundingBox> = emptyList()
    private var highlightBox: BoundingBox? = null
    private var showLabels = true
    private var onBoundingBoxClickListener: ((BoundingBox) -> Unit)? = null
    private lateinit var textToSpeech: TextToSpeech

    // Predefined list of distinct colors for bounding boxes
    private val colors = listOf(
        Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN,
        Color.MAGENTA, Color.LTGRAY, Color.DKGRAY, Color.BLACK, Color.WHITE
    )

    init {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = textToSpeech.setLanguage(Locale.getDefault())
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Handle language not supported or missing data
                }
            }
        }
    }

    fun updateTextColor(boundingBox: BoundingBox, color: Int) {
        boundingBox.textColor = color
        invalidate() // Refresh the view
    }

    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    fun clearResults() {
        setResults(emptyList())
    }

    fun getBoundingBoxes(): List<BoundingBox> {
        return results
    }

    fun highlightBoundingBox(box: BoundingBox) {
        highlightBox = box
        invalidate()
    }

    fun setShowLabels(show: Boolean) {
        showLabels = show
        invalidate()
    }

    fun drawBoundingBoxesOnly(canvas: Canvas) {
        for (box in results) {
            val paint = Paint().apply {
                color = if (box == highlightBox) Color.RED else Color.TRANSPARENT
                style = Paint.Style.STROKE
                strokeWidth = if (box == highlightBox) 0f else 0f
            }

            // Draw bounding box
            canvas.drawRect(box.x1 * width, box.y1 * height, box.x2 * width, box.y2 * height, paint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val imageWidth = width.toFloat()
        val imageHeight = height.toFloat()

        for ((index, box) in results.withIndex()) {
            val paint = Paint().apply {
                color = if (box == highlightBox) Color.RED else colors[index % colors.size] // Assign unique color
                style = Paint.Style.STROKE
                strokeWidth = if (box == highlightBox) 5f else 2f
            }

            val left = box.x1 * imageWidth
            val top = box.y1 * imageHeight
            val right = box.x2 * imageWidth
            val bottom = box.y2 * imageHeight

            canvas.drawRect(left, top, right, bottom, paint)

            if (showLabels && box.clsName.isNotEmpty()) {
                val textPaint = Paint().apply {
                    color = box.textColor
                    textSize = 30f
                    isAntiAlias = true
                }
                canvas.drawText(box.clsName, left, top - 10f, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val touchedBoundingBox = handleTouch(event.x, event.y)
            touchedBoundingBox?.let {
                onBoundingBoxClickListener?.invoke(it)
                it.textColor = Color.WHITE // Reset text color when re-answered
                speakLabel(it.clsName)
                invalidate() // Refresh UI
            }
        }
        return true
    }

    fun handleTouch(x: Float, y: Float): BoundingBox? {
        for (box in results) {
            if (x >= box.x1 * width && x <= box.x2 * width && y >= box.y1 * height && y <= box.y2 * height) {
                return box
            }
        }
        return null
    }

    fun setOnBoundingBoxClickListener(listener: (BoundingBox) -> Unit) {
        onBoundingBoxClickListener = listener
    }

    private fun speakLabel(label: String) {
        if (label.isNotEmpty()) {
            textToSpeech.speak(label, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}
