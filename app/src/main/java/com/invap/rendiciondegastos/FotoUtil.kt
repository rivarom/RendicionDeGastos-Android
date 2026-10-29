package com.invap.rendiciondegastos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FotoUtil {

    /**
     * Gira una foto JPEG [grados] en sentido horario (múltiplo de 90) respecto de cómo se ve hoy
     * (es decir, aplicando antes su orientación EXIF) y la reescribe en el mismo archivo.
     * El resultado ya no depende de la etiqueta EXIF: se ve igual en cualquier visor y en el PDF.
     * Debe llamarse fuera del hilo principal.
     */
    fun girarJpeg(archivo: File, grados: Int) {
        if (grados % 360 == 0) return

        val orientacion = ExifInterface(archivo.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matriz = Matrix()
        when (orientacion) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matriz.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matriz.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matriz.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matriz.postRotate(90f); matriz.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matriz.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matriz.postRotate(-90f); matriz.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matriz.postRotate(270f)
        }
        matriz.postRotate(grados.toFloat())

        try {
            val original = BitmapFactory.decodeFile(archivo.absolutePath)
                ?: throw IOException("No se pudo leer la foto")
            try {
                val girado = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matriz, true)
                try {
                    // Se escribe a un temporal y recién al final se reemplaza: si algo falla, la foto original queda intacta
                    val temporal = File(archivo.parentFile, archivo.name + ".tmp")
                    FileOutputStream(temporal).use { salida ->
                        if (!girado.compress(Bitmap.CompressFormat.JPEG, 95, salida)) {
                            throw IOException("No se pudo guardar la foto girada")
                        }
                    }
                    if (!temporal.renameTo(archivo)) {
                        temporal.copyTo(archivo, overwrite = true)
                        temporal.delete()
                    }
                } finally {
                    if (girado !== original) girado.recycle()
                }
            } finally {
                original.recycle()
            }
        } catch (e: OutOfMemoryError) {
            throw IOException("Memoria insuficiente para girar la foto")
        }
    }
}
