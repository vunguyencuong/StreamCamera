package com.example.sticker_ex2

import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.LocaleList
import android.provider.MediaStore
import android.util.Log
import android.view.OrientationEventListener
import android.view.SurfaceView
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
import com.example.sticker_ex2.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import com.xiaopo.flying.sticker.DrawableSticker
import com.xiaopo.flying.sticker.Sticker
import com.xiaopo.flying.sticker.StickerView
import com.xiaopo.flying.sticker.TextSticker
import java.io.File
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

        if (checkMultiplePermission()) {
            binding.cameraPreview.post {
                startCamera()
            }
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


    private fun checkMultiplePermission() : Boolean{
        val listPermissionNeed = arrayListOf<String>()
        for(permission in multiplePermissionNameList){
            if(ContextCompat.checkSelfPermission(
                this,
                permission
                ) != PackageManager.PERMISSION_GRANTED
                ){
                listPermissionNeed.add(permission)
            }
        }
        if(listPermissionNeed.isNotEmpty()){
            ActivityCompat.requestPermissions(
                this,
                listPermissionNeed.toTypedArray(),
                multiplePermissionId
            )
            return false
        }
        return true
    }


    private  fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUserCases()
        },ContextCompat.getMainExecutor(this))
    }

    private fun takePicture(){

        val imageFolder = File(
            Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES),"ImagesTattoo"
        )
        if(!imageFolder.exists()){
            imageFolder.mkdir()
        }

        val fileName = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(System.currentTimeMillis()) + ".jpg"

        val imageFile = File(imageFolder,fileName)
        val outputOption = OutputFileOptions.Builder(imageFile).build()

        imageCapture.takePicture(
            outputOption,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback{
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val message = "Photo capture succeeded: ${outputFileResults.savedUri}"
                    Toast.makeText(
                        this@MainActivity,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.d("bug", "onError: ${exception.message.toString()}")
                    Toast.makeText(
                        this@MainActivity,
                        exception.message.toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                }

            }
        )

    }



    private fun aspectRatio(width: Int, height: Int) : Int{
        if (width == 0 || height == 0) {
            // Trả về một tỉ lệ mặc định nếu width hoặc height là 0
            return AspectRatio.RATIO_4_3
        }
        val previewRatio = (Math.max(width,height) / Math.min(width,height)).toDouble()
        if(Math.abs(previewRatio-4.0/3.0) <= Math.abs(previewRatio-16.0/9.0)){
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }
    private fun bindCameraUserCases(){
        val rotation = binding.cameraPreview.display.rotation
        val screenAspectRatio = aspectRatio(
            binding.cameraPreview.width,
            binding.cameraPreview.height
        )
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
    override fun onStart() {
        super.onStart()

        // Tạo một hình ảnh (Drawable) từ tài nguyên drawable
        val pandaImage = ContextCompat.getDrawable(this, R.drawable.panda)

        // Kiểm tra xem pandaImage có null hay không trước khi tiếp tục
        pandaImage?.let {
            // Tạo một hình chữ nhật để đặt kích thước cho hình ảnh
            val rect = Rect(0, 0, it.intrinsicWidth, it.intrinsicHeight)

            // Gắn hình chữ nhật vào hình ảnh
            it.bounds = rect

            // Tạo một sticker từ hình ảnh
            val sticker: Sticker = DrawableSticker(it)

            // Thêm sticker vào stickerView
            stickerView.addSticker(sticker)

        }
    }
}