package com.example.sticker_ex2

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.LocaleList
import android.provider.MediaStore
import android.util.Half.toFloat
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
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
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

        if(!hasPermissions(baseContext)){
            //Request camera-related permissions
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        } else {
            startCamera()
        }

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

    }


    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
    { permissions ->
        var permissionGranted = true
        permissions.entries.forEach {
            if(it.key in REQUIRED_PERMISSIONS && it.value == false)
                permissionGranted = false
        }
        if(!permissionGranted){
            Toast.makeText(this,"Permission request denied",Toast.LENGTH_SHORT).show()
        } else{
            startCamera()
        }
    }

    companion object{
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                android.Manifest.permission.CAMERA
            ).apply {
                if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P){
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()

        fun hasPermissions(context: Context) = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context,it) == PackageManager.PERMISSION_GRANTED
        }

    }


    private  fun startCamera(){
        Log.d("check camera", "startCamera: ")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUserCases()
        },ContextCompat.getMainExecutor(this))
    }

    private fun takePicture() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val cameraBitmap = imageProxy.toBitmap()
                    val rotatedCameraBitmap = rotateBitmap(cameraBitmap, imageProxy.imageInfo.rotationDegrees)

                    val overlayBitmap = getBitmapFromView(binding.stickerView)
                    val combinedBitmap = combineBitmaps(rotatedCameraBitmap, overlayBitmap)

                    saveImageToGallery(combinedBitmap)

                    val combinedFilePath = "${getOutputDirectory()}/combined_image_${System.currentTimeMillis()}.jpg"
                    Toast.makeText(this@MainActivity, "Image saved to $combinedFilePath", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    Toast.makeText(this@MainActivity, "Error capturing image: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }


    private fun getBitmapFromView(view : View) : Bitmap{
        val bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        view.draw(canvas)
        return bitmap
    }

    private fun combineBitmaps(cameraBitmap : Bitmap, overlayBitmap : Bitmap) : Bitmap{
        val scaledOverlayBitmap = Bitmap.createScaledBitmap(
            overlayBitmap,
            cameraBitmap.width,
            cameraBitmap.height,
            false
        )
        val combinedBitmap = cameraBitmap.copy(cameraBitmap.config, true)
        val canvas = Canvas(combinedBitmap)
        canvas.drawBitmap(scaledOverlayBitmap, 0f, 0f, null)
        return combinedBitmap
    }

    private fun getOutputDirectory() : File{
        val mediaDir = applicationContext.externalMediaDirs.firstOrNull()?.let {
            File(it,resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else applicationContext.getExternalFilesDir(null)!!
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

    private fun rotateBitmap(bitmap: Bitmap,orientation: Int) : Bitmap{
        val matrix = Matrix()
        matrix.postRotate(orientation.toFloat())
        return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
    }

    private fun bindCameraUserCases(){
        Log.d("check camera", "bindCameraUserCases: ")
        val rotation = binding.cameraPreview.display.rotation

        // Lấy tỷ lệ khung hình của preview
        val screenAspectRatio = AspectRatio.RATIO_16_9

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