package com.example.sticker_ex2

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.sticker_ex2.databinding.ActivityMain2Binding
import kotlin.math.log

class MainActivity2 : AppCompatActivity() {

    private lateinit var binding: ActivityMain2Binding
    private val PERMISSION_REQUEST_CODE = 100
    private val IMAGE_PICK_CODE = 101
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initDrawView()
        binding.btnUploadPhoto.setOnClickListener {
            Log.d("Upload", "onCreate: have click")
            openGallery()
        }

        binding.seekBarPenSize.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                    binding.scratchPad.penSize = p0!!.progress.toFloat()
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {

                }

                override fun onStopTrackingTouch(p0: SeekBar?) {
                    Log.d("Upload", "onStopTrackingTouch: ${p0!!.progress}")
                    binding.scratchPad.initializePen()
                }

            }
        )


        binding.seekBarEraseSize.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                    binding.scratchPad.eraserSize = p0!!.progress.toFloat()
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {

                }

                override fun onStopTrackingTouch(p0: SeekBar?) {
                    Log.d("Upload", "onStopTrackingTouch: ${p0!!.progress}")
                    binding.scratchPad.initializeEraser()
                }

            }
        )

    }

    private fun initDrawView(){
//        binding.scratchPad.initializePen()
        binding.scratchPad.setBackgroundColor(Color.TRANSPARENT)
        binding.scratchPad.setPenColor(Color.BLUE)
        binding.scratchPad.penSize = 0.0F
        binding.scratchPad.eraserSize = 0.0F
    }

    private fun openGallery(){
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d("Upload", "openGallery: ")
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
        } else {
            Log.d("Upload", "openGallery: pick image")
            pickImageFromGallery()
        }
    }

    private fun pickImageFromGallery(){
        binding.btnUploadPhoto.visibility = View.GONE
        Log.d("Upload", "pickImageFromGallery: pick iamge")
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pickImageFromGallery()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == IMAGE_PICK_CODE) {
            val imageUri = data?.data
            imageUri?.let {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val scaledBitmap = getScaledBitmap(bitmap, binding.scratchPad.width, binding.scratchPad.height)
                binding.scratchPad.loadImage(scaledBitmap)
            }
        }
    }

    private fun getScaledBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height
        val scaleFactor = Math.min(width.toFloat() / bitmapWidth, height.toFloat() / bitmapHeight)
        val scaledWidth = (bitmapWidth * scaleFactor).toInt()
        val scaledHeight = (bitmapHeight * scaleFactor).toInt()
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }


}