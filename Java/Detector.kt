package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener
) {

    private var interpreter: Interpreter? = null
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    private lateinit var reusableTensorImage: TensorImage
    private lateinit var reusableOutputBuffer: TensorBuffer

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    fun setup() {
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options().apply {
            numThreads = 4
            setUseXNNPACK(false)
        }
        interpreter = Interpreter(model, options)

        val inputShape = interpreter?.getInputTensor(0)?.shape() ?: return
        val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: return

        tensorWidth = inputShape[1]
        tensorHeight = inputShape[2]
        numChannel = outputShape[1]
        numElements = outputShape[2]

        reusableTensorImage = TensorImage(INPUT_IMAGE_TYPE)
        reusableOutputBuffer = TensorBuffer.createFixedSize(
            intArrayOf(1, numChannel, numElements),
            OUTPUT_IMAGE_TYPE
        )

        loadLabels()
    }

    private fun loadLabels() {
        try {
            val inputStream: InputStream = context.assets.open(labelPath)
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    labels.add(line)
                    line = reader.readLine()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun clear() {
        interpreter?.close()
        interpreter = null
    }

    fun detect(frame: Bitmap) {
        interpreter ?: return
        if (tensorWidth == 0 || tensorHeight == 0 || numChannel == 0 || numElements == 0) return

        val startTime = SystemClock.uptimeMillis()


        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, true)

        reusableTensorImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(reusableTensorImage)
        interpreter?.run(processedImage.buffer, reusableOutputBuffer.buffer)

        val bestBoxes = bestBox(reusableOutputBuffer.floatArray)
        val inferenceTime = SystemClock.uptimeMillis() - startTime

        if (bestBoxes.isNullOrEmpty()) {
            detectorListener.onEmptyDetect()
        } else {
            detectorListener.onDetect(bestBoxes, inferenceTime)
        }
    }

    private fun bestBox(array: FloatArray): List<BoundingBox>? {
        val boundingBoxes = mutableListOf<BoundingBox>()
        val maxDetections = 100 // Limit number of boxes before NMS

        for (c in 0 until numElements) {
            var maxConf = -1.0f
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + numElements * j
            while (j < numChannel) {
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                val clsName = labels.getOrNull(maxIdx) ?: "Unknown"
                val cx = array[c]
                val cy = array[c + numElements]
                val w = array[c + numElements * 2]
                val h = array[c + numElements * 3]
                val x1 = cx - (w / 2F)
                val y1 = cy - (h / 2F)
                val x2 = cx + (w / 2F)
                val y2 = cy + (h / 2F)

                if (x1 >= 0F && y1 >= 0F && x2 <= 1F && y2 <= 1F) {
                    boundingBoxes.add(
                        BoundingBox(
                            x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                            cx = cx, cy = cy, w = w, h = h,
                            cnf = maxConf, cls = maxIdx, clsName = clsName
                        )
                    )
                }
            }
        }

        return if (boundingBoxes.isEmpty()) null else applyNMS(boundingBoxes.take(maxDetections))
    }

    private fun applyNMS(boxes: List<BoundingBox>): List<BoundingBox> {
        val selectedBoxes = mutableListOf<BoundingBox>()
        val sortedBoxes = boxes.sortedByDescending { it.cnf }

        val suppressed = BooleanArray(sortedBoxes.size) { false }

        for (i in sortedBoxes.indices) {
            if (suppressed[i]) continue
            val box1 = sortedBoxes[i]
            selectedBoxes.add(box1)

            for (j in i + 1 until sortedBoxes.size) {
                if (suppressed[j]) continue
                val box2 = sortedBoxes[j]
                if (calculateIoU(box1, box2) >= IOU_THRESHOLD) {
                    suppressed[j] = true
                }
            }
        }

        return selectedBoxes
    }


    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)

        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h

        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32 // Reuse this for output
        private const val CONFIDENCE_THRESHOLD = 0.30F
        private const val IOU_THRESHOLD = 0.15F
    }
}


