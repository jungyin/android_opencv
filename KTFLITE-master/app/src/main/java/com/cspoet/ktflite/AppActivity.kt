package com.cspoet.ktflite

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.opencv.android.*
import org.opencv.android.CameraBridgeViewBase.RGBA
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors


class AppActivity : CameraActivity() {
    lateinit var classifier: Classifier
    private val executor = Executors.newSingleThreadExecutor()
    lateinit var textViewResult: TextView
    lateinit var btnDetectObject: Button
    lateinit var btnToggleCamera: Button
    lateinit var imageViewResult: ImageView
    lateinit var cameraView: JavaCameraView
    lateinit var mLoaderCallback: LoaderCallbackInterface
    lateinit var rv: RecyclerView
    lateinit var rectKernel: Mat
    lateinit var sqKernel: Mat
    var detectorMax: IClassifier.Recognition? = null
    var matRectFace: Rect? = null
    var timeChache: Long = 0
    var outTimeChache = 0
    var images: ArrayList<Mat> = ArrayList()
    var detector: Boolean = false
    var nowdetectoring: Boolean = false
    var width: Int = 0
    var height: Int = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraView = findViewById(R.id.cameraView)
        imageViewResult = findViewById<ImageView>(R.id.imageViewResult)
        textViewResult = findViewById(R.id.textViewResult)
        rv = findViewById(R.id.rv)
        textViewResult.movementMethod = ScrollingMovementMethod()

        btnToggleCamera = findViewById(R.id.btnToggleCamera)
        btnDetectObject = findViewById(R.id.btnDetectObject)

        var arr: ArrayList<String> = getWalkPerssionArr()
        arr!!.addAll(getCameraPerssionArr())
        hasRequestPermissions(1001, arr, true)
        width = resources.getDimensionPixelOffset(R.dimen.dp_300)
        height = resources.getDimensionPixelOffset(R.dimen.dp_200)

        val lp = cameraView.layoutParams
        lp.height = height
        lp.width = width
        cameraView.layoutParams = lp

        val resultDialog = Dialog(this)
        val customProgressView = LayoutInflater.from(this).inflate(R.layout.result_dialog_layout, null)
        resultDialog.setCancelable(false)
        resultDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        resultDialog.setContentView(customProgressView)

        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val ivImageResult = customProgressView.findViewById<ImageView>(R.id.iViewResult)

        val tvLoadingText = customProgressView.findViewById<TextView>(R.id.tvLoadingRecognition)

        val tvTextResults = customProgressView.findViewById<TextView>(R.id.tvResult)

        var audioManager: AudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager;

        audioManager.isMicrophoneMute = true

        // The Loader Holder is used due to a bug in the Avi Loader library
        val aviLoaderHolder = customProgressView.findViewById<View>(R.id.aviLoaderHolderView)




        mLoaderCallback = object : LoaderCallbackInterface {
            override fun onManagerConnected(status: Int) {
                //opencv加载就绪
                if (status == 0 && (images == null || images.size == 0)) {

                    //协程注意事项：协程与线程一样，创建后运行的内容并非在ui线程中，如果有界面更新，或者页面跳转，记得切换线程
                    var job = GlobalScope.launch(Dispatchers.IO) {

                        var mat: Mat = Mat()
                        var hierarchy: Mat = Mat()
                        var contours: ArrayList<MatOfPoint> = ArrayList()
                        Utils.bitmapToMat(BitmapFactory.decodeResource(resources, R.mipmap.ocr_a_reference), mat)
                        rectKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(12.toDouble(), 5.toDouble()))
                        sqKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.toDouble(), 5.toDouble()))
                        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY, 0)
                        Imgproc.threshold(mat, mat, 10.toDouble(), 255.toDouble(), Imgproc.THRESH_BINARY_INV)
                        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

                        for (i in 0..contours.size - 1) {
                            val boundingRect: Rect = Imgproc.boundingRect(contours[i])
                            var roi: Mat = mat.submat(boundingRect)
                            Imgproc.resize(roi, roi, Size(57.toDouble(), 88.toDouble()))
                            images.add(roi)
                        }
                        //回到主线程
                        launch(Dispatchers.Main) {
                            var mat: Mat = Mat()
                            Utils.bitmapToMat(BitmapFactory.decodeResource(resources, R.mipmap.credit_card_01), mat)
//                            images.add(distinguishCard(mat))
                            rv.adapter = ImageAdapter(this@AppActivity, images)

                        }
                    }
                }
            }

            override fun onPackageInstall(operation: Int, callback: InstallCallbackInterface) {
                Log.e("hehe", "onPackageInstall:" + operation)

            }
        }


        var map = BitmapFactory.decodeResource(resources, R.mipmap.credit_card_01)
        cameraView.setCvCameraViewListener(object : CameraBridgeViewBase.CvCameraViewListener2 {
            override fun onCameraViewStarted(width: Int, height: Int) {
                Log.e("hehe", "开始")
            }

            override fun onCameraViewStopped() {
                Log.e("hehe", "结束")
            }

            override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
                var result: Mat? = null
                when (RGBA) {
                    CameraBridgeViewBase.RGBA -> result = (inputFrame!!.rgba())
                    CameraBridgeViewBase.GRAY -> result = (inputFrame!!.gray())
                    else -> Log.e("hehe", "Invalid frame format! Only RGBA and Gray Scale are supported!")
                }
//                Utils.bitmapToMat(map,result)
                synchronized(this) {
                    if (result != null) {
                        //先把图片摆正
                        var angle = 90
//                        自己写的方法，获取当前摄像机位置，旋转mat
                        if (cameraView.getmCameraIndex() == CameraBridgeViewBase.CAMERA_ID_FRONT) {
                            angle = 270
                        }
                        var size = Size(result.width().toDouble(), result.height().toDouble())
                        Imgproc.warpAffine(result, result, Imgproc.getRotationMatrix2D(Point((result.width() / 2).toDouble(), (result.height() / 2).toDouble()), angle.toDouble(), -1.0), size)
                        try {
//                            if (isDetector()) {
//                                if (!isNowDetectorIng()) {
//                                    setNowDetectorIng(true)
//
//                                    val bitmap = Bitmap.createBitmap(result.width(), result.height(), Bitmap.Config.ARGB_8888)
//
//                                    Utils.matToBitmap(result, bitmap)
////                                    var mFaceRec = FaceRec(Constants.getDLibDirectoryPath())
//                                    var matRect: List<VisionDetRet> = mFaceRec.recognize(bitmap)!!
//
//                                    if (matRect.size > 0 && matRect.get(0) != null) {
//                                    try {
//                                        var matRectFace = matRect.get(0)
//                                        //只要第一张人脸,把里面的人脸切割下来
//                                        var detectorResult = result.submat(matRect.get(0).left, Math.min(matRect.get(0).left + matRect.get(0).right, result.rows()), matRect.get(0).top, Math.min(matRect.get(0).top + matRect.get(0).bottom, result.cols()))
//                                        //调整大小
//                                        var bitResult = Mat()
//                                        Imgproc.resize(detectorResult, bitResult, Size(224.toDouble(), 224.toDouble()))
//
//                                        var detectorEnd = classifier.recognizeImage(bitResult)
//
//                                        if (detectorEnd.size > 0) {
//                                            var maxIndex = 0;
//                                            if (detectorEnd.size != 1) {
//                                                for (m in 0 until detectorEnd.size) {
//                                                    if (detectorEnd[maxIndex].confidence < detectorEnd[m].confidence) {
//                                                        maxIndex = m;
//                                                    }
//                                                }
//                                            }
//                                            detectorMax = detectorEnd[maxIndex]
//                                        }
//
//                                    } catch (e: Exception) {
//                                        Log.e("hehe1", "onSuccess错误: " + e.message)
//                                        val stackTrace = e.stackTrace
//                                        for (i in stackTrace.indices) {
//                                            Log.e("hehe1", "onSuccess错误: " + stackTrace[i].className + "//" + stackTrace[i].lineNumber)
//                                        }
//                                    }
//
//                                    }
//
//
//                                    setNowDetectorIng(false)
//                                }
////                        detector = false
////                        runOnUiThread(object : Runnable {
////                            override fun run() {
////                                if(detectorMax!=null) {
////                                    aviLoaderHolder.visibility = View.GONE
////                                    tvLoadingText.visibility = View.GONE
////                                    tvTextResults.text = detectorMax.toString()
////                                    tvTextResults.visibility = View.VISIBLE
////                                    ivImageResult.visibility = View.VISIBLE
////                                    resultDialog.setCancelable(true)
////                                }
////                            }
////                        })
//                                //人脸框绘制
//                                if (matRectFace != null) {
//                                    var color = Scalar(0.0, 0.0, 0.0)
//                                    Imgproc.rectangle(result, matRectFace, color)
//                                }
//                                //识别率绘制
//                                if (detectorMax != null) {
//                                    var pt1 = Point(result.width().toDouble() / 3, result.height().toDouble() / 3 * 2)
//
//                                    var color = Scalar(46.0, 139.0, 87.0)
//                                    if (detectorMax!!.title.contains("out")) {
//                                        color = Scalar(127.0, 255.0, 0.0)
//                                    }
//                                    Imgproc.putText(result, detectorMax!!.title + ":" + detectorMax!!.confidence, pt1, Imgproc.FONT_HERSHEY_COMPLEX, 2.0, color)
//                                }
//
//                            }
                        } catch (e: Exception) {
                            Log.e("hehe1", "onSuccess错误: " + e.message)
                            val stackTrace = e.stackTrace
                            for (i in stackTrace.indices) {
                                Log.e("hehe1", "onSuccess错误: " + stackTrace[i].className + "//" + stackTrace[i].lineNumber)
                            }
                        }
                    }
                }


                return result!!

            }

        })

        //切换镜头
        btnToggleCamera.setOnClickListener {
//            自己写的方法，用于切换摄像机正反面
            if (cameraView.getmCameraIndex() == CameraBridgeViewBase.CAMERA_ID_FRONT) {
                cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK)
            } else {
                cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT)

            }
//            自己改的方法，吧重新配置摄像机前后和调整分辨率功能打开，方便切换镜头
            cameraView.releaseCamera();
            cameraView.connectCamera(width, height)
        }


        btnDetectObject.setOnClickListener {
            detector = !detector
//            resultDialog.show()
//            tvTextResults.visibility = View.GONE
//            ivImageResult.visibility = View.GONE

        }

        resultDialog.setOnDismissListener {
            tvLoadingText.visibility = View.VISIBLE
            aviLoaderHolder.visibility = View.VISIBLE
        }

        initTensorFlowAndLoadModel()

    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

//      自己改的方法，可以重启摄像机
        cameraView.connectCamera(width, height)
    }

    override fun onPause() {
        cameraView.releaseCamera()
        super.onPause()
    }

    override fun onDestroy() {
        cameraView.disableView()
        executor.execute { classifier.close() }
        super.onDestroy()

    }

    private fun initTensorFlowAndLoadModel() {
        executor.execute {
            try {
                classifier = Classifier.create(
                        assets,
                        MODEL_PATH,
                        LABEL_PATH,
                        INPUT_SIZE)
                makeButtonVisible()
            } catch (e: Exception) {
                throw RuntimeException("Error initializing TensorFlow!", e)
            }
        }
    }

    private fun makeButtonVisible() {
        runOnUiThread { btnDetectObject.visibility = View.VISIBLE }
    }


    //获取相机权限数组
    fun getCameraPerssionArr(): ArrayList<String> {
        val requestPerssionArr = ArrayList<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            //相机
            val hasCamrea = checkSelfPermission(Manifest.permission.CAMERA)
            if (hasCamrea != PackageManager.PERMISSION_GRANTED) {
                requestPerssionArr.add(Manifest.permission.CAMERA)
            }
        }
        return requestPerssionArr
    }

    //获取相机权限数组
    fun getWalkPerssionArr(): ArrayList<String> {
        val requestPerssionArr = ArrayList<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            //相机
            val hasCamrea = checkSelfPermission(Manifest.permission.WAKE_LOCK)
            if (hasCamrea != PackageManager.PERMISSION_GRANTED) {
                requestPerssionArr.add(Manifest.permission.WAKE_LOCK)
            }
        }
        return requestPerssionArr
    }

    fun hasRequestPermissions(requestCode: Int, requestPerssionArr: ArrayList<String>, showToast: Boolean): Boolean? {
        return try {
            if (Build.VERSION.SDK_INT >= 23) {

                // 是否应该显示权限请求
                if (requestPerssionArr!!.size >= 1) {
                    if (showToast) {
                        runOnUiThread { Toast.makeText(this, "请先给予权限", Toast.LENGTH_SHORT).show() }
                    }
                    val s = arrayOfNulls<String>(requestPerssionArr.size)
                    for (i in requestPerssionArr.indices) {
                        s[i] = requestPerssionArr[i]
                    }
                    ActivityCompat.requestPermissions(this, s, requestCode)
                    return false
                }
                true
            } else {
                true
            }
        } catch (e: java.lang.Exception) {
            Toast.makeText(this, "授权失败", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun distinguishCard(result: Mat): Mat {
        var gray: Mat = Mat()
        //图像转灰度图
        Imgproc.cvtColor(result, gray, Imgproc.COLOR_BGR2GRAY, 0)
        var pix: Double = 300.0 / gray.width().toDouble()

        Imgproc.resize(gray, gray, Size(300.0, pix * gray.height()))


        var tophat = Mat()
        Imgproc.morphologyEx(gray, tophat, Imgproc.MORPH_TOPHAT, rectKernel)
        Imgproc.Sobel(tophat, tophat, CvType.CV_32F, 1, 0, -1)

        tophat.convertTo(tophat, CvType.CV_32F, 1 / 255.0)

//        tophat.convertTo(tophat, CvType.CV_8UC1)

        Imgproc.threshold(tophat, tophat, 0.5.toDouble(), 255.toDouble(), Imgproc.THRESH_BINARY_INV)

        Imgproc.morphologyEx(tophat, tophat, Imgproc.MORPH_CLOSE, rectKernel)

        Imgproc.threshold(tophat, tophat, 0.toDouble(), 255.toDouble(), Imgproc.THRESH_BINARY and Imgproc.THRESH_OTSU)

        Imgproc.morphologyEx(tophat, tophat, Imgproc.MORPH_CLOSE, sqKernel)

        if (true) {

            Utils.bitmapToMat(BitmapFactory.decodeResource(resources, R.mipmap.ceshi), tophat)
        }

        var hierarchy: Mat = Mat()
        var imageContours: ArrayList<MatOfPoint> = ArrayList()

        Imgproc.findContours(tophat, imageContours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)


        var locs: ArrayList<Rect> = ArrayList()
        //判定哪些区域有数字区
        for (i in 0..imageContours.size - 1) {
            val outRect: Rect = Imgproc.boundingRect(imageContours.get(i))
            var ar: Float = 0.0F
            if (outRect.width > 40 && outRect.width < 55 && outRect.height < 20 && outRect.height > 10) {
                ar = outRect.width / (outRect.height.toFloat())
            }
            if (ar > 2.5 && ar < 4.0) {
                locs.add(outRect)
            }
        }


        var output: ArrayList<MatOfPoint> = ArrayList()

        for (out in 0..locs.size - 1) {
            var groupOutput: ArrayList<MatOfPoint> = ArrayList()

            var groupImage: Mat = gray.submat(locs.get(out))

            Imgproc.threshold(groupImage, groupImage, 0.toDouble(), 255.toDouble(), Imgproc.THRESH_BINARY and Imgproc.THRESH_OTSU)

            var groupContours: ArrayList<MatOfPoint> = ArrayList()
            var groupMat = Mat()
            Imgproc.findContours(groupImage, groupContours, groupMat, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            groupContours = ImageUtils.SortContours(groupContours, "left-to-right")
//                        for (i, c) in enumerate(groupContours):
//                        (x, y, w, h) = cv2.boundingRect(c)
//                        groupRoi = gray[y:y+h, x:x+w]
//                        groupRoi = cv2.resize(groupRoi, (57, 88))
//                        scores = []
//                        for (i, c) in imgCents.items():
//                        result = cv2.matchTemplate(c, groupRoi, cv2.TM_CCOEFF)
//                        (_, score, _, _) = cv2.minMaxLoc(result)
//                        scores.append(score)
//                        groupOutput.append(str(np.argmax(scores)))
//
//                        # 画出来
//                        cv2.rectangle(image, (gX - 5, gY - 5),
//                        (gX + gW + 5, gY+gH+5), (0, 0, 255), 1)
//                        cv2.putText(image, "".join(groupOutput), (gX, gY - 15),
//                        cv2.FONT_HERSHEY_SIMPLEX, 0.65, (0, 0, 255), 2)
//
//                        # 得到结果
//                        output.extend(groupOutput)
        }
        return tophat

    }

    fun isDetector(): Boolean {
        synchronized(this) {
            return detector
        }
    }

    fun isNowDetectorIng(): Boolean {
        synchronized(this) {
            return nowdetectoring
        }
    }

    fun setNowDetectorIng(nowdetectoring: Boolean) {
        synchronized(this) {
            this.nowdetectoring = nowdetectoring
        }
    }

    companion object {
        //        private const val MODEL_PATH = "my_mask_detector.model"
//        private const val MODEL_PATH = "my_mask_detector.model"
        //        private const val MODEL_PATH = "demomodel.tflite"
        private const val MODEL_PATH = "mask_detectorByByte.tflite"
//      private const val MODEL_PATH = "mobilenet_quant_v1_224.tflite"
//      private const val MODEL_PATH = "dogandcat.tflite"

        //        private const val LABEL_PATH = "dogandcat.txt"
        private const val LABEL_PATH = "mask_labels.txt"

        //      private const val LABEL_PATH = "labels.txt"
        private const val INPUT_SIZE = 224
    }


}
