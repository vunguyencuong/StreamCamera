package com.example.sticker_ex2

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
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
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.log

class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding

    lateinit var stickerView: StickerView
//    lateinit var stickerContainer: FrameLayout
    private var cameraFacing = CameraSelector.LENS_FACING_BACK


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
//        stickerView = binding.stickerView

        binding.cameraPreview.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // Chỉ gọi startCamera sau khi layout đã được hoàn thành
                binding.cameraPreview.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    activityResultLauncher.launch(android.Manifest.permission.CAMERA)
                    Log.d("open camera", "onCreate: permission")
                } else {
                    startCamera(cameraFacing)
                    Log.d("open camera", "onCreate: start camera")
                }
            }
        })
        binding.btnSwitch.setOnClickListener {
            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            Log.d("open camera", "onCreate: $cameraFacing")
            startCamera(cameraFacing)
        }
    }

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("open camera", ": accept")
            startCamera(cameraFacing)
        } else {
            // Xử lý khi quyền bị từ chối
            Log.d("open camera", ": deny")
        }
    }

    private  fun startCamera(cameraFacing : Int){
        Log.d("open camera", "startCamera: ")
        val aspectRatio  = aspectRatio(binding.cameraPreview.width,binding.cameraPreview.height)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val listenableFuture : ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(this)
        listenableFuture.addListener({
            try {
                val cameraProvider = listenableFuture.get()as ProcessCameraProvider

                val preview = Preview.Builder().setTargetAspectRatio(aspectRatio).build()

                val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(windowManager.defaultDisplay.rotation).build()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraFacing).build()

                cameraProvider.unbindAll()

//                val camera = cameraProvider.bindToLifecycle(this, cameraSelector,preview,imageCapture)

                binding.btnCapture.setOnClickListener {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        activityResultLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                    takePicture(imageCapture)
                }

                preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            } catch (e: ExecutionException){
                e.printStackTrace()
            } catch (e: InterruptedException){
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePicture(imageCapture: ImageCapture){
        val file = File(getExternalFilesDir(null), "${System.currentTimeMillis()}.jpg")
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(outputFileOptions, Executors.newCachedThreadPool(), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Image saved at: ${file.path}", Toast.LENGTH_SHORT).show()
                }
                startCamera(cameraFacing)
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to save: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
                startCamera(cameraFacing)
            }
        })
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

//    override fun onStart() {
//        super.onStart()
//
//        // Tạo một hình ảnh (Drawable) từ tài nguyên drawable
//        val pandaImage = ContextCompat.getDrawable(this, R.drawable.panda)
//
//        // Kiểm tra xem pandaImage có null hay không trước khi tiếp tục
//        pandaImage?.let {
//            // Tạo một hình chữ nhật để đặt kích thước cho hình ảnh
//            val rect = Rect(0, 0, it.intrinsicWidth, it.intrinsicHeight)
//
//            // Gắn hình chữ nhật vào hình ảnh
//            it.bounds = rect
//
//            // Tạo một sticker từ hình ảnh
//            val sticker: Sticker = DrawableSticker(it)
//
//            // Thêm sticker vào stickerView
//            stickerView.addSticker(sticker)
//
//        }
//    }
}