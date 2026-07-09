package com.callrecorder.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object PhotoUtils {

    /** 카메라 촬영용 임시 파일 생성 (cache/camera_photos/) */
    fun createTempImageFile(context: Context): File {
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        return File.createTempFile("IMG_${System.currentTimeMillis()}_", ".jpg", dir)
    }

    /** FileProvider authority */
    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /**
     * Uri의 이미지를 적당히 압축한 JPEG ByteArray로 변환.
     * - 긴 변 기준 1600px로 리사이즈 (업로드 용량 절감)
     * - EXIF 회전 보정
     * - JPEG 85% 품질
     */
    fun uriToCompressedBytes(context: Context, uri: Uri, maxSize: Int = 1600, quality: Int = 85): ByteArray? {
        return try {
            val bitmap = decodeSampledBitmap(context, uri, maxSize) ?: return null
            val rotated = applyExifRotation(context, uri, bitmap)
            val out = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, quality, out)
            rotated.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    fun copyUriToCustomerImageFile(context: Context, uri: Uri, fileName: String): String? {
        return try {
            val dir = File(context.filesDir, "customer_images").apply { mkdirs() }
            val safeName = fileName.ifBlank { "customer_${System.currentTimeMillis()}.jpg" }
            val file = File(dir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            file.toURI().toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        // 1차: 크기만 측정
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // inSampleSize 계산
        var sample = 1
        val longer = maxOf(bounds.outWidth, bounds.outHeight)
        while (longer / sample > maxSize * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        // 정확한 리사이즈
        val w = decoded.width
        val h = decoded.height
        val longest = maxOf(w, h)
        if (longest <= maxSize) return decoded
        val scale = maxSize.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(decoded, (w * scale).toInt(), (h * scale).toInt(), true)
        if (scaled != decoded) decoded.recycle()
        return scaled
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: Exception) {
            bitmap
        }
    }
}
