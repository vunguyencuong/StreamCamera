package com.example.sticker_ex2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle:Int = 0
) : View(context,attrs,defStyle) {

    private val TOUCH_TOLERANCE = 4f
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var path: Path = Path()
    private var bitmapPaint: Paint = Paint(Paint.DITHER_FLAG)
    private var drawPaint: Paint = Paint()
    private var erasePaint: Paint = Paint()
    private var drawMode: Boolean = true
    private var x = 0f
    private var y = 0f
    var penSize: Float = 10f
    var eraserSize: Float = 10f

    init {
        init()
    }

    private fun init() {
        // Thiết lập cho drawPaint
        drawPaint.isAntiAlias = true
        drawPaint.isDither = true
        drawPaint.color = getPenColor()
        drawPaint.style = Paint.Style.STROKE
        drawPaint.strokeJoin = Paint.Join.ROUND
        drawPaint.strokeCap = Paint.Cap.ROUND
        drawPaint.strokeWidth = penSize
        drawPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)

        // Thiết lập cho erasePaint
        erasePaint.isAntiAlias = true
        erasePaint.isDither = true
        erasePaint.color = Color.TRANSPARENT
        erasePaint.style = Paint.Style.STROKE
        erasePaint.strokeJoin = Paint.Join.ROUND
        erasePaint.strokeCap = Paint.Cap.ROUND
        erasePaint.strokeWidth = eraserSize
        erasePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        canvas = Canvas(bitmap!!)
        canvas?.drawColor(Color.TRANSPARENT)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(bitmap!!, 0f, 0f, bitmapPaint)
        canvas.drawPath(path, if (drawMode) drawPaint else erasePaint)
    }

    private fun touchStart(x: Float, y: Float) {
        path.reset()
        path.moveTo(x, y)
        this.x = x
        this.y = y
        canvas?.drawPath(path, if (drawMode) drawPaint else erasePaint)
    }

    private fun touchMove(x: Float, y: Float) {
        val dx = Math.abs(x - this.x)
        val dy = Math.abs(y - this.y)
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            path.quadTo(this.x, this.y, (x + this.x) / 2, (y + this.y) / 2)
            this.x = x
            this.y = y
        }
        canvas?.drawPath(path, if (drawMode) drawPaint else erasePaint)
    }

    private fun touchUp() {
        path.lineTo(x, y)
        canvas?.drawPath(path, if (drawMode) drawPaint else erasePaint)
        path.reset()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStart(x, y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                touchMove(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                touchUp()
                invalidate()
            }
        }
        return true
    }

    fun initializePen() {
        drawMode = true
        drawPaint.strokeWidth = penSize
    }

    fun initializeEraser() {
        drawMode = false
        erasePaint.strokeWidth = eraserSize
    }

    fun clear() {
        canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        invalidate()
    }

    override fun setBackgroundColor(color: Int) {
        if (canvas == null) {
            canvas = Canvas()
        }
        canvas?.drawColor(color)
        super.setBackgroundColor(color)
    }


    fun setPenColor(@ColorInt color: Int) {
        drawPaint.color = color
    }

    fun getPenColor(): Int {
        return drawPaint.color
    }

//    fun loadImage(bitmap: Bitmap) {
//        // Tạo một bản sao của bitmap với cấu hình ARGB_8888
//        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
//
//        // Tính toán tỷ lệ để căn lề trái và phải
//        val scale = width.toFloat() / newBitmap.width
//        val scaledHeight = newBitmap.height * scale
//        val left = 0f
//        val top = (height - scaledHeight) / 2f
//
//        // Tạo một bitmap mới để chứa ảnh nền và ảnh mới
//        val combinedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        val combinedCanvas = Canvas(combinedBitmap)
//
//        // Vẽ ảnh nền hiện tại nếu có
//        this.bitmap?.let {
//            combinedCanvas.drawBitmap(it, 0f, 0f, bitmapPaint)
//        }
//
//        // Vẽ ảnh mới được căn lề trái và phải
//        combinedCanvas.drawBitmap(
//            Bitmap.createScaledBitmap(newBitmap, width, scaledHeight.toInt(), true),
//            left,
//            top,
//            bitmapPaint
//        )
//
//        // Cập nhật bitmap hiện tại với hình ảnh kết hợp
//        this.bitmap = combinedBitmap
//
//        // Giải phóng tài nguyên của ảnh mới
//        newBitmap.recycle()
//
//        // Yêu cầu vẽ lại view
//        invalidate()
//    }

    fun loadImage(bitmap: Bitmap) {
        this.bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        canvas?.setBitmap(this.bitmap)
        bitmap.recycle()
        invalidate()
    }

    fun saveImage(filePath: String, filename: String, format: Bitmap.CompressFormat, quality: Int): Boolean {
        if (quality > 100) {
            Log.d("saveImage", "quality cannot be greater than 100")
            return false
        }
        val file: File
        var out: FileOutputStream? = null
        return try {
            file = when (format) {
                Bitmap.CompressFormat.PNG -> File(filePath, "$filename.png")
                Bitmap.CompressFormat.JPEG -> File(filePath, "$filename.jpg")
                else -> File(filePath, "$filename.png")
            }
            out = FileOutputStream(file)
            bitmap!!.compress(format, quality, out)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                out?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

}