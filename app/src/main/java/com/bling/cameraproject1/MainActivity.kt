package com.bling.cameraproject1

import android.Manifest.permission.RECORD_AUDIO
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.bling.cameraproject1.databinding.ActivityMainBinding
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.view.GestureDetectorCompat
import java.lang.Math.abs


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: ActivityMainBinding
    private lateinit var photoFile: File
    private lateinit var photoURI: Uri
    var ocrResult: String? = null
    private lateinit var recognitionListener: RecognitionListener
    private lateinit var mRecognizer: SpeechRecognizer
    lateinit var tess: TessBaseAPI //Tesseract API 객체 생성
    var dataPath: String = "" //데이터 경로 변수 선언
    var isLongPressed = false
    private var tts: TextToSpeech? = null
    var detector: GestureDetectorCompat? = null
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {

            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        // Request camera permission if not already granted
        tts = TextToSpeech(this, this)
        val permissions =
            arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA)

        val deniedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED
        }

        if (deniedPermissions.isNotEmpty()) {
            // Request permissions
            ActivityCompat.requestPermissions(this, deniedPermissions.toTypedArray(), 1)
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)

        } else {
            // Permissions already granted
            startCamera()
        }
        binding.viewFinder.setScaleType(PreviewView.ScaleType.FIT_CENTER);


        recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) {
                Toast.makeText(this@MainActivity, "음성인식을 시작 합니다.", Toast.LENGTH_SHORT).show()

            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onResults(results: Bundle) {
                val voiceResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (voiceResults != null && voiceResults.isNotEmpty()) {
                    val recognizedText = voiceResults[0]
                    // Do something with the recognized text성
                    Log.d("JIWOUNG","speakjiwoung888888: "+recognizedText)
                    if (recognizedText.contains("재생")) {
                        showToast("음성을 다시 재생합니다.")
                        speakOut()
                    } else if (recognizedText.contains("저장")) {
                        savePhoto()
                        showToast("사진을 저장합니다.")
                    } else if (recognizedText.contains("삭제")) {
                        stopTTS()
                        showToast("재촬영 합니다.")
                    }

                }
            }

            override fun onPartialResults(partialResults: Bundle) {}
            override fun onEvent(eventType: Int, params: Bundle) {}
        }

        mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mRecognizer.setRecognitionListener(recognitionListener);


        // Set up the directory where the captured images will be stored
        outputDirectory = getOutputDirectory()

        // Set up the camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Set up the capture button click listener


        dataPath = "$filesDir/tesseract/" //언어데이터의 경로 미리 지정

        checkFile(File(dataPath + "tessdata/"), "kor") //사용할 언어파일의 이름 지정
        checkFile(File(dataPath + "tessdata/"), "eng")

        val lang: String = "kor+eng"
        tess = TessBaseAPI() //api준비
        tess.init(dataPath, lang) //해당 사용할 언어데이터로 초기화

        detector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onLongPress(e: MotionEvent) {
                super.onLongPress(e)
                Log.d("JIWOUNG", "speakjiwoung111111")
                isLongPressed = true
                if (tts != null) {
                    if (tts!!.isSpeaking) {
                        tts!!.stop()
                    }
                }
                var intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
                mRecognizer.startListening(intent);
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                Log.d("JIWOUNG", "speakjiwoung77777")

                takePhoto()
                return true
            }

            override fun onFling(
                e1: MotionEvent,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // Handle the fling gesture here
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

Log.d("JIWOUNG", "abscheck: "+abs(deltaX)+"|||"+abs(deltaY))

                if ((deltaY > 0 && Math.abs(deltaY) > Math.abs(velocityY))) {
                    stopTTS()
                    showToast("재촬영 합니다.")

                }
                   else if (deltaX < 0) {
                        // Handle the fling gesture from right to left here
                        Log.d("JIWOUNG", "ewfnwwe:  " + "right-left")
                        showToast("음성을 다시 재생합니다.")
                        speakOut()
                    } else if (deltaX > 0) {
                        Log.d("JIWOUNG", "ewfnwwe:  " + "left-right")
                        savePhoto()
                        stopTTS()
                        showToast("사진을 저장합니다.")

                    }

                Log.d("JIWOUNG", "fliehc")
                return true
            }

        })


        binding.captureButton.setOnTouchListener { v, event ->
            event?.let { detector?.onTouchEvent(it) }
            when (event?.action) {
                MotionEvent.ACTION_UP -> {
                    if (isLongPressed) {
                        isLongPressed = false
                        mRecognizer.stopListening()

                        Log.d("JIWOUNG", "speakjiwoung444444")
                    }
                }
            }
            super.onTouchEvent(event)
        }
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Get the camera provider
            val cameraProvider = cameraProviderFuture.get()

            // Set up the preview
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3) //디폴트 표준 비율
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // Set up the image capture
            imageCapture = ImageCapture.Builder()
                .build()

            // Set up the image analyzer

            // Choose the back camera as the default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind any previous use cases before rebinding
                cameraProvider.unbindAll()

                // Bind the use cases to the camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Get a file to save the image
        photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                FILENAME_FORMAT,
                Locale.KOREA
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        // Set up the output options
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Take the photo
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Display a toast message with the file name
                    val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                    if (savedUri != null) {
                        // Save the image to the gallery
                        binding.imageView.visibility = View.VISIBLE
                        binding.imageView.setImageURI(savedUri)
                        val bitmap = binding.imageView.drawable.toBitmap()
                        // OCR 동작 버튼

                        processImage(bitmap)

                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    exception.printStackTrace()
                }
            }
        )
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun showToast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXExample"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    }

    private fun copyFile(lang: String) {
        try {
            //언어데이타파일의 위치
            val filePath: String = "$dataPath/tessdata/$lang.traineddata"

            //AssetManager를 사용하기 위한 객체 생성
            val assetManager: AssetManager = assets;

            //byte 스트림을 읽기 쓰기용으로 열기
            val inputStream: InputStream = assetManager.open("tessdata/$lang.traineddata")
            val outStream: OutputStream = FileOutputStream(filePath)


            //위에 적어둔 파일 경로쪽으로 해당 바이트코드 파일을 복사한다.
            val buffer = ByteArray(1024)

            var read: Int = 0
            read = inputStream.read(buffer)
            while (read != -1) {
                outStream.write(buffer, 0, read)
                read = inputStream.read(buffer)
            }
            outStream.flush()
            outStream.close()
            inputStream.close()

        } catch (e: FileNotFoundException) {
            Log.v("오류발생", e.toString())
        } catch (e: IOException) {
            Log.v("오류발생", e.toString())
        }

    }


    /***
     *  언어 데이터 파일이 기기에 존재하는지 체크하는 기능
     *  @param dir: 언어 데이터 파일 경로
     *  @param lang: 언어 종류 데이터 파일
     *
     *  -> 파일 없으면 파일 생성
     *  -> 있으면 언어 종류 데이터 파일 복사
     */
    private fun checkFile(dir: File, lang: String) {

        //파일의 존재여부 확인 후 내부로 복사
        if (!dir.exists() && dir.mkdirs()) {
            copyFile(lang)
        }

        if (dir.exists()) {
            val datafilePath: String = "$dataPath/tessdata/$lang.traineddata"
            val dataFile: File = File(datafilePath)
            if (!dataFile.exists()) {
                copyFile(lang)
            }
        }

    }

    private fun processImage(bitmap: Bitmap) {

        var dialog = LoadingDialog(this@MainActivity)
        dialog.show()

        tess.setImage(bitmap)

        CoroutineScope(Dispatchers.IO).launch {
            ocrResult = tess.utF8Text
            speakOut()
            CoroutineScope(Dispatchers.Main).launch {
                dialog.dismiss()
            }
        }


    }

    override fun onInit(status: Int) {
        if (status === TextToSpeech.SUCCESS) {
            val result: Int = tts!!.setLanguage(Locale.KOREA)
            if (result == TextToSpeech.LANG_MISSING_DATA
                || result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.e("TTS", "This Language is not supported")
            } else {
                speakOut()
            }
        } else {
            Log.e("TTS", "Initilization Failed!")
        }
    }

    private fun speakOut() {
        val text: String? = ocrResult
        Log.d("JIWOUNG","ocrresult: "+text)
        tts!!.setPitch(0.6.toFloat())
        tts!!.setSpeechRate(0.1.toFloat())
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "id1")
    }

    fun savePhoto() {

        val contentResolver = applicationContext.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, photoFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val imageUri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        if (imageUri != null) {
            contentResolver.openOutputStream(imageUri).use { outputStream ->
                val inputStream = FileInputStream(photoFile)
                inputStream.copyTo(outputStream!!)
                outputStream.close()
                inputStream.close()
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(imageUri, contentValues, null, null)
                showToast("Image saved to gallery!")
            }
        }
    }

    fun stopTTS() {
        if (tts != null) {
            binding.imageView.visibility = View.GONE
            if (tts!!.isSpeaking) {
                tts!!.stop()
            }
        }
    }


}