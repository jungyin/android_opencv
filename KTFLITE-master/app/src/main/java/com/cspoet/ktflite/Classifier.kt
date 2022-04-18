package com.cspoet.ktflite

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Float
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.collections.ArrayList


class Classifier(
        var interpreter: Interpreter? = null,
        var inputSize: Int = 0,
        var labelList: List<String> = emptyList()
) : IClassifier {

    companion object {
        private val MAX_RESULTS = 3
        private val BATCH_SIZE = 1
        private val PIXEL_SIZE = 3
        private val THRESHOLD = 0.1f

        @Throws(IOException::class)
        fun create(assetManager: AssetManager,
                   modelPath: String,
                   labelPath: String,
                   inputSize: Int): Classifier {

            val classifier = Classifier()
            classifier.interpreter = Interpreter(classifier.loadModelFile(assetManager, modelPath))
            classifier.labelList = classifier.loadLabelList(assetManager, labelPath)

            classifier.inputSize = inputSize

            return classifier
        }
    }


    fun ceshiDemo() {
        val k = ByteBuffer.allocateDirect((4)).let {
            it.order(ByteOrder.nativeOrder())
            it.asFloatBuffer()
        }
        k.put(3F)
        val k1 = FloatBuffer.allocate(1)
        interpreter!!.run(k, k1)
        for (i in 0 until k1.array().size) {
            Log.e("hehe", k1.array()[i].toString())
        }
    }

    override fun recognizeImage(bitmap: Mat): List<IClassifier.Recognition> {
        val byteBuffer = convertBitmapToByteBuffer(bitmap)
        val result = Array(1) { FloatArray(labelList.size) }

        val k1 = FloatBuffer.allocate(2)
        interpreter!!.run(byteBuffer, k1)

        return getSortedResult(k1)
    }

    override fun close() {
        interpreter!!.close()
        interpreter = null
    }

    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @Throws(IOException::class)
    private fun loadLabelList(assetManager: AssetManager, labelPath: String): List<String> {
        val labelList = ArrayList<String>()
        val reader = BufferedReader(InputStreamReader(assetManager.open(labelPath)))
        while (true) {
            val line = reader.readLine() ?: break
            labelList.add(line)
        }
        reader.close()
        return labelList
    }

    private fun convertBitmapToByteBuffer1(mat: Mat): ByteBuffer {
        val bitmap = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        val byteBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())
//        byteBuffer.order()
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val `val` = intValues[pixel++]
                byteBuffer.put((`val` shr 16 and 0xFF).toByte())
                byteBuffer.put((`val` shr 8 and 0xFF).toByte())
                byteBuffer.put((`val` and 0xFF).toByte())

                if (i == inputSize / 2 && j == inputSize / 3) {
                    Log.e("hhe", "This is from bitmap:1:" + (`val` shr 16 and 0xFF).toByte().toString() + "//" + (`val` shr 8 and 0xFF).toByte().toString() + "//" + (`val` and 0xFF).toByte().toString())
                } else if (i == inputSize / 4 && j == inputSize / 3) {
                    Log.e("hhe", "This is from bitmap:2:" + (`val` shr 16 and 0xFF).toByte().toString() + "//" + (`val` shr 8 and 0xFF).toByte().toString() + "//" + (`val` and 0xFF).toByte().toString())
                }
            }

        }
        return byteBuffer
    }

    private fun convertBitmapToByteBuffer(mat: Mat): ByteBuffer {
        //最后的乘以4，是因为如果不byte只有float4分之一的大小，如果不扩容，会导致内存溢出
        val byteBuffer = ByteBuffer.allocateDirect((BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE * 4)).let {
            it.order(ByteOrder.nativeOrder())
//            it.asFloatBuffer()
        }

        val channels: Int = mat.channels()
        val width: Int = mat.cols()
        val height: Int = mat.rows()
        val data = ByteArray(channels)

        for (i in 0 until width) {
            for (j in 0 until height) {
                mat.get(i, j, data)
                val b = (data[0] - 128)
                val g = (data[1] - 128)
                //位运算符，但我还用不好，目前暂时先用-128替代
//                val r = (data[2] and 0xff.toByte())
                val r = (data[2] - 128)

                byteBuffer.putFloat(b.toFloat())
                byteBuffer.putFloat(g.toFloat())
                byteBuffer.putFloat(r.toFloat())
            }
        }

        return byteBuffer
    }

    private fun getSortedResult(labelProbArray: FloatBuffer): List<IClassifier.Recognition> {
        val pq = PriorityQueue(
                MAX_RESULTS,
                Comparator<IClassifier.Recognition> { (_, _, confidence1), (_, _, confidence2) -> Float.compare(confidence1, confidence2) })

        for (i in labelList.indices) {
            Log.e("hehe",labelProbArray[i].toString())
            //特奶奶的，已经有数据了，但因为强转int转没了
            val confidence = labelProbArray[i]
            if (confidence > THRESHOLD) {
                pq.add(IClassifier.Recognition("" + i,
                        if (labelList.size > i) labelList[i] else "Unknown",
                        confidence))
            }
        }

        val recognitions = ArrayList<IClassifier.Recognition>()
        val recognitionsSize = Math.min(pq.size, MAX_RESULTS)
        for (i in 0 until recognitionsSize) {
            recognitions.add(pq.poll())
        }

        return recognitions
    }
}