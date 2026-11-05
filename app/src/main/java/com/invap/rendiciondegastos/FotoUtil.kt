package com.invap.rendiciondegastos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FotoUtil {

    /** Lado mayor (px) al que se reducen las fotos al guardarlas: sobra para imprimir un recibo y ahorra mucho espacio. */
    const val LADO_MAXIMO_PX = 2400
    private const val CALIDAD_JPEG = 88

    /**
     * Reduce una foto JPEG grande (p. ej. 12 MP de la cámara) a [maximoPx] de lado mayor y la reescribe
     * sin etiqueta EXIF (aplicando antes su orientación). Si ya es chica y está derecha, no la toca.
     * Debe llamarse fuera del hilo principal. Si algo falla, la foto original queda intacta.
     */
    fun reducir(archivo: File, maximoPx: Int = LADO_MAXIMO_PX) {
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.absolutePath, opciones)
        val ancho = opciones.outWidth
        val alto = opciones.outHeight
        if (ancho <= 0 || alto <= 0) return

        val orientacion = ExifInterface(archivo.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val derecha = orientacion == ExifInterface.ORIENTATION_NORMAL || orientacion == ExifInterface.ORIENTATION_UNDEFINED
        if (maxOf(ancho, alto) <= maximoPx && derecha) return

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

        try {
            // Decodificar ya reducida (potencia de 2) para no cargar los 12 MP completos
            var muestreo = 1
            while (maxOf(ancho, alto) / (muestreo * 2) >= maximoPx) muestreo *= 2
            val decodificada = BitmapFactory.decodeFile(
                archivo.absolutePath, BitmapFactory.Options().apply { inSampleSize = muestreo }
            ) ?: throw IOException("No se pudo leer la foto")
            var escalada = decodificada
            try {
                val lado = maxOf(decodificada.width, decodificada.height)
                if (lado > maximoPx) {
                    val factor = maximoPx.toFloat() / lado
                    escalada = Bitmap.createScaledBitmap(
                        decodificada,
                        maxOf(1, Math.round(decodificada.width * factor)),
                        maxOf(1, Math.round(decodificada.height * factor)),
                        true
                    )
                }
                val resultado = if (matriz.isIdentity) escalada
                    else Bitmap.createBitmap(escalada, 0, 0, escalada.width, escalada.height, matriz, true)
                try {
                    val temporal = File(archivo.parentFile, archivo.name + ".tmp")
                    FileOutputStream(temporal).use { salida ->
                        if (!resultado.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)) {
                            throw IOException("No se pudo guardar la foto reducida")
                        }
                    }
                    if (!temporal.renameTo(archivo)) {
                        temporal.copyTo(archivo, overwrite = true)
                        temporal.delete()
                    }
                } finally {
                    if (resultado !== escalada && resultado !== decodificada) resultado.recycle()
                }
            } finally {
                if (escalada !== decodificada) escalada.recycle()
                decodificada.recycle()
            }
        } catch (e: OutOfMemoryError) {
            throw IOException("Memoria insuficiente para reducir la foto")
        }
    }

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
