package com.example.sticker_ex2

import android.content.ContentValues

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.media.ExifInterface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.LocaleList
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.drawToBitmap
import androidx.core.view.get
import com.example.sticker_ex2.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import com.xiaopo.flying.sticker.DrawableSticker
import com.xiaopo.flying.sticker.Sticker
import com.xiaopo.flying.sticker.StickerView
import com.xiaopo.flying.sticker.TextSticker
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.log

class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding

    lateinit var stickerView: StickerView
    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    lateinit var sticker : Sticker

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var cameraSelector: CameraSelector




    private val multiplePermissionId = 14
    private val multiplePermissionNameList = if (Build.VERSION.SDK_INT >= 33) {
        arrayListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    } else {
        arrayListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        stickerView = binding.stickerView
        // Tạo một hình ảnh (Drawable) từ tài nguyên drawable
        val pandaImage = ContextCompat.getDrawable(this, R.drawable.panda)

        // Kiểm tra xem pandaImage có null hay không trước khi tiếp tục
        pandaImage?.let {
            // Tạo một hình chữ nhật để đặt kích thước cho hình ảnh
            val rect = Rect(0, 0, it.intrinsicWidth, it.intrinsicHeight)

            // Gắn hình chữ nhật vào hình ảnh
            it.bounds = rect

            // Tạo một sticker từ hình ảnh
            sticker = DrawableSticker(it)

            // Thêm sticker vào stickerView
            stickerView.addSticker(sticker)
        }


        startCamera()

        binding.btnSwitch.setOnClickListener {
            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            Log.d("open camera", "onCreate: $cameraFacing")
            startCamera()
        }

        binding.btnCapture.setOnClickListener {
            takePicture()

        }

        binding.cameraPreview.viewTreeObserver.addOnGlobalLayoutListener(object  : ViewTreeObserver.OnGlobalLayoutListener{

            override fun onGlobalLayout() {
                binding.cameraPreview.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val textureView = binding.cameraPreview.getChildAt(0) as? TextureView

                textureView?.surfaceTextureListener = object  : TextureView.SurfaceTextureListener{
                    override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                        bindCameraUserCases()
                    }

                    override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

                    }

                    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                        return true
                    }

                    override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

                    }

                }
            }

        })

    }


    private  fun startCamera(){
        Log.d("check camera", "startCamera: ")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUserCases()
        },ContextCompat.getMainExecutor(this))
    }

    private fun takePicture(){


        val stickerLocation = stickerView.getStickerPoints(sticker)
        Log.d("Dimensions", "takePicture: A: ${stickerLocation[0]} - ${stickerLocation[1]} " +
                " B: ${stickerLocation[2]} - ${stickerLocation[3]}" +
                " C: ${stickerLocation[4]} - ${stickerLocation[5]}" +
                " D: ${stickerLocation[6]} - ${stickerLocation[7]}")


        // Lấy kích thước của cameraPreview và stickerView
        val previewWidth = binding.cameraPreview.width.toFloat()
        val previewHeight = binding.cameraPreview.height.toFloat()

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(applicationContext),
            object : ImageCapture.OnImageCapturedCallback(){
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val rotationDegress = imageProxy.imageInfo.rotationDegrees
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()

                    val combinedBitmap = combineImageWithSticker(
                        rotateBitmap(bitmap,ExifInterface.ORIENTATION_NORMAL),
                        stickerLocation,
                        previewWidth,
                        previewHeight
                    )
                    if(combinedBitmap != null){
                        saveImageToGallery(combinedBitmap)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    val errorMsg = when (exception.imageCaptureError) {
                        ImageCapture.ERROR_CAPTURE_FAILED -> "Capture failed"
                        ImageCapture.ERROR_FILE_IO -> "File I/O error"
                        ImageCapture.ERROR_CAMERA_CLOSED -> "Camera closed unexpectedly"
                        ImageCapture.ERROR_INVALID_CAMERA -> "Invalid camera"
                        ImageCapture.ERROR_UNKNOWN -> "Unknown error"
                        else -> "Unknown error"
                    }
                    Log.d("check camera", "onError: ${errorMsg}: ${exception.message}")
                    Toast.makeText(this@MainActivity, "Photo capture failed: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            }
        )


//        imageCapture.takePicture(
//            outputOptions,
//            ContextCompat.getMainExecutor(applicationContext),
//            object  : ImageCapture.OnImageSavedCallback{
//                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
//                    val message = "Photo capture succeeded: ${outputFileResults.savedUri}"
//                    Log.d("check camera", "onImageSaved: ${outputFileResults.savedUri}")
//                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
//                    Log.d("check camera", "onImageSaved: File(path).exists: ${outputFileResults.savedUri?.path?.let { File(it).exists() }}")
//                    notifyNewFileCreate(outputFileResults.savedUri.toString())
//                    val combinedFilePath = combineImageWithSticker(filePath,stickerX,stickerY)
//                    if (combinedFilePath != null) {
//                        saveImageToGallery(combinedFilePath)
//                    }
//                }
//
//                override fun onError(exception: ImageCaptureException) {
//                    val errorMsg = when (exception.imageCaptureError) {
//                            ImageCapture.ERROR_CAPTURE_FAILED -> "Capture failed"
//                            ImageCapture.ERROR_FILE_IO -> "File I/O error"
//                            ImageCapture.ERROR_CAMERA_CLOSED -> "Camera closed unexpectedly"
//                            ImageCapture.ERROR_INVALID_CAMERA -> "Invalid camera"
//                            ImageCapture.ERROR_UNKNOWN -> "Unknown error"
//                            else -> "Unknown error"
//                    }
//                    Log.d("check camera", "onError: ${errorMsg}: ${exception.message}")
//                    Toast.makeText(this@MainActivity, "Photo capture failed: $errorMsg", Toast.LENGTH_SHORT).show()
//                }
//
//            }
//        )

    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Xoay bitmap dựa trên rotationDegrees
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return rotateBitmap(bitmap, rotationDegrees)
    }






    private fun getOutputDirectory() : File{
        val mediaDir = applicationContext.externalMediaDirs.firstOrNull()?.let {
            File(it,resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else applicationContext.getExternalFilesDir(null)!!
    }

//    private fun saveImageToGallery(file: File) {
//        val contentValues = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
//            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
//            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ImageTattoos")
//        }
//
//        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
//        if (uri != null) {
//            contentResolver.openOutputStream(uri)?.use { outputStream ->
//                val inputStream = FileInputStream(file)
//                inputStream.copyTo(outputStream)
//                inputStream.close()
//                outputStream.close()
//            }
//        }
//    }

    private fun combineImageWithSticker(bitmap: Bitmap, stickerLocation: FloatArray, previewWidth: Float, previewHeight: Float): Bitmap? {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // Tạo một bitmap có thể chỉnh sửa

        val canvas = Canvas(mutableBitmap)
        canvas.save()



        // Lấy kích thước của ảnh chụp
        val imageWidth = mutableBitmap.width.toFloat()
        val imageHeight = mutableBitmap.height.toFloat()

        // Tính tỷ lệ chuyển đổi từ PreviewView sang ảnh chụp
        val scaleX = imageWidth / previewWidth
        val scaleY = imageHeight / previewHeight

        Log.d("Dimensions", "Image width: $imageWidth, height: $imageHeight"+ "\n"
                                    + "Preview width: $previewWidth, height: $previewHeight"+ "\n"
                                    + "Scale X: $scaleX, Scale Y: $scaleY")

        // Chuyển đổi tọa độ của bốn góc sticker sang hệ tọa độ của ảnh
        val transformedStickerX0 = stickerLocation[0] * scaleX
        val transformedStickerY0 = stickerLocation[1] * scaleY
        val transformedStickerX1 = stickerLocation[2] * scaleX
        val transformedStickerY1 = stickerLocation[3] * scaleY
        val transformedStickerX2 = stickerLocation[4] * scaleX
        val transformedStickerY2 = stickerLocation[5] * scaleY
        val transformedStickerX3 = stickerLocation[6] * scaleX
        val transformedStickerY3 = stickerLocation[7] * scaleY

        Log.d("Dimensions", "transformedStickerX0: $transformedStickerX0")
        Log.d("Dimensions", "transformedStickerY0: $transformedStickerY0")
        Log.d("Dimensions", "transformedStickerX1: $transformedStickerX1")
        Log.d("Dimensions", "transformedStickerY1: $transformedStickerY1")
        Log.d("Dimensions", "transformedStickerX2: $transformedStickerX2")
        Log.d("Dimensions", "transformedStickerY2: $transformedStickerY2")
        Log.d("Dimensions", "transformedStickerX3: $transformedStickerX3")
        Log.d("Dimensions", "transformedStickerY3: $transformedStickerY3")
        // Tính toán ma trận biến đổi để đưa sticker vào đúng vị trí trên ảnh chụp
        val matrix = Matrix()
        matrix.setPolyToPoly(
            floatArrayOf(
                0f, 0f,
                stickerView.width.toFloat(), 0f,
                stickerView.width.toFloat(), stickerView.height.toFloat(),
                0f, stickerView.height.toFloat()
            ),
            0,
            floatArrayOf(
                transformedStickerX2, transformedStickerY2,
                transformedStickerX3, transformedStickerY3,
                transformedStickerX0, transformedStickerY0,
                transformedStickerX1, transformedStickerY1,
            ),
            0,
            4
        )
        // Áp dụng ma trận biến đổi cho canvas
        canvas.setMatrix(matrix)

        // Vẽ sticker lên ảnh chụp
        sticker.drawable.draw(canvas)
        canvas.restore()

        return mutableBitmap
    }


    private fun saveImageToGallery(bitmap: Bitmap) {
        val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            .format(System.currentTimeMillis()) + ".jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ImageTattoos")
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
            }
        }
    }


//    private fun combineImageWithSticker(imageFile: File, stickerX: Float, stickerY:Float) : File? {
//        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)?.copy(Bitmap.Config.ARGB_8888,true)
//
//        val rotatedBitmap = bitmap?.let {
//            val exif = ExifInterface(imageFile.absolutePath)
//            val rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
//            rotateBitmap(it,rotation)
//        }
//
//        return rotatedBitmap?.let {
//            val canvas = Canvas(it)
//            canvas.save()
//
//            // Lấy kích thước của StickerView và ảnh chụp
//            val imageWidth = it.width.toFloat()
//            val imageHeight = it.height.toFloat()
//
//            // Tính tỷ lệ chuyển đổi từ PreviewView sang ảnh chụp
//            val scaleX = imageWidth / previewWidth
//            val scaleY = imageHeight / previewHeight
//
//            Log.d("Dimensions", "Image width: $imageWidth, height: $imageHeight")
//            Log.d("Scale", "Scale X: $scaleX, Scale Y: $scaleY")
//
//
//            // Chuyển đổi tọa độ của sticker sang hệ tọa độ của ảnh
//            val transformedStickerX = stickerX * scaleX
//            val transformedStickerY = stickerY * scaleY
//
//            // Đặt sticker vào vị trí đã chuyển đổi
//            canvas.translate(transformedStickerX, transformedStickerY)
//            stickerView.draw(canvas)
//            canvas.restore()
//
//            val combinedFileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
//                .format(System.currentTimeMillis()) + "_combined.jpg"
//            val combinedFile = File(getOutputDirectory(), combinedFileName)
//
//            try {
//                val outputStream = FileOutputStream(combinedFile)
//                it.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
//                outputStream.flush()
//                outputStream.close()
//                Toast.makeText(this@MainActivity, "Combined image saved successfully", Toast.LENGTH_SHORT).show()
//                combinedFile
//            } catch (e: IOException) {
//                e.printStackTrace()
//                Toast.makeText(this@MainActivity, "Failed to save combined image", Toast.LENGTH_SHORT).show()
//                null
//            }
//        }
//    }

    private fun rotateBitmap(bitmap: Bitmap,orientation: Int) : Bitmap{
        val matrix = Matrix()
        matrix.postRotate(orientation.toFloat())
//        when(orientation){
//            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
//            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
//            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
//            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f,1f)
//            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f,-1f)
//            ExifInterface.ORIENTATION_TRANSPOSE -> {
//                matrix.postRotate(90f)
//                matrix.postScale(-1f, 1f)
//            }
//            ExifInterface.ORIENTATION_TRANSVERSE -> {
//                matrix.postRotate(270f)
//                matrix.postScale(-1f, 1f)
//            }
//            else -> return bitmap
//        }
        return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
    }

    private fun aspectRatio(width: Int, height: Int) : Int{
//        if (width == 0 || height == 0) {
//            // Trả về một tỉ lệ mặc định nếu width hoặc height là 0
//            return AspectRatio.RATIO_4_3
//        }
        val previewRatio = (Math.max(width,height) / Math.min(width,height)).toDouble()
        if(Math.abs(previewRatio-4.0/3.0) <= Math.abs(previewRatio-16.0/9.0)){
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }
    private fun bindCameraUserCases(){
        Log.d("check camera", "bindCameraUserCases: ")
        val rotation = binding.cameraPreview.display.rotation

        // Lấy tỷ lệ khung hình của preview
        val screenAspectRatio = aspectRatio(
            binding.cameraPreview.width,
            binding.cameraPreview.height
        )

        // Đặt ResolutionSelector cho cả preview và ảnh chụp
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    screenAspectRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()
            .also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()

        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing)
            .build()

        try{
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this,cameraSelector,preview,imageCapture)
        } catch (e : Exception){
            e.printStackTrace()
        }
    }

}