package com.invap.rendiciondegastos

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Error esperable al exportar/importar; su mensaje se muestra tal cual al usuario. */
class ViajeBackupException(message: String) : Exception(message)

data class ResultadoImportacion(
    val nombreViaje: String,
    val gastosImportados: Int,
    /** Gastos que referenciaban una foto que no venía en el archivo. */
    val fotosFaltantes: Int,
    /** Elementos agregados a la configuración del usuario para que el viaje se exporte completo. */
    val configAgregada: List<String>
)

/**
 * Exporta un viaje (con sus gastos y fotos de recibos) a un único archivo .zip y lo vuelve a importar.
 *
 * Contenido del zip:
 *  - viaje.json: datos del viaje y de cada gasto (primera entrada)
 *  - fotos/N.jpg: fotos de los recibos, referenciadas desde viaje.json
 */
object ViajeBackup {

    private const val FORMATO = "invap-rendicion-viaje"
    private const val VERSION_FORMATO = 1
    private const val ENTRADA_JSON = "viaje.json"

    // Límites para no agotar memoria/almacenamiento con un archivo dañado o malicioso
    private const val MAX_JSON_BYTES = 20L * 1024 * 1024
    private const val MAX_FOTO_BYTES = 60L * 1024 * 1024
    private const val MAX_FOTOS = 2000
    private val REGEX_FOTO = Regex("fotos/[0-9]{1,6}\\.jpg")

    fun nombreSugerido(nombreViaje: String): String {
        val seguro = nombreViaje.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").ifEmpty { "viaje" }
        return "Viaje_$seguro.zip"
    }

    /**
     * Escribe el viaje en [destino] (se cierra al terminar).
     * @return cantidad de gastos cuya foto no se encontró en el teléfono
     */
    suspend fun exportar(db: AppDatabase, viajeId: Long, destino: OutputStream): Int = withContext(Dispatchers.IO) {
        val viaje = db.viajeDao().getViajeById(viajeId) ?: throw ViajeBackupException("No se encontró el viaje.")
        val gastos = db.gastoDao().getGastosByViajeId(viajeId)

        var fotosFaltantes = 0
        val fotos = mutableListOf<Pair<String, File>>()
        val gastosJson = JSONArray()
        gastos.forEachIndexed { indice, g ->
            var entradaFoto: String? = null
            if (g.urlFotoRecibo.isNotEmpty()) {
                val archivo = File(g.urlFotoRecibo)
                if (archivo.exists()) {
                    entradaFoto = "fotos/${indice + 1}.jpg"
                    fotos.add(entradaFoto to archivo)
                } else {
                    fotosFaltantes++
                }
            }
            val o = JSONObject()
            o.put("descripcion", g.descripcion)
            o.put("monto", g.monto)
            o.put("fecha", g.fecha)
            o.put("foto", entradaFoto ?: JSONObject.NULL)
            o.put("moneda", g.moneda)
            o.put("tipoGasto", g.tipoGasto)
            o.put("formaDePago", g.formaDePago)
            o.put("tagGasto", g.tagGasto)
            o.put("imputacionPT", g.imputacionPT)
            o.put("imputacionWP", g.imputacionWP)
            o.put("nombrePersona", g.nombrePersona)
            o.put("legajo", g.legajo)
            o.put("centroCostos", g.centroCostos)
            o.put("timestamp", g.timestamp)
            o.put("sinComprobante", g.sinComprobante)
            o.put("tamanoRecibo", g.tamanoRecibo)
            // Opcionales (agregados después): se omiten si no hay dato
            if (g.importeDolares != null) {
                o.put("importeDolares", g.importeDolares)
                o.put("dolaresEstimado", g.dolaresEstimado)
            }
            gastosJson.put(o)
        }

        val viajeJson = JSONObject()
        viajeJson.put("nombre", viaje.nombre)
        viajeJson.put("fecha", viaje.fecha)
        viajeJson.put("monedaPorDefecto", viaje.monedaPorDefecto)
        viajeJson.put("imputacionPorDefectoPT", viaje.imputacionPorDefectoPT)
        viajeJson.put("imputacionPorDefectoWP", viaje.imputacionPorDefectoWP)
        viajeJson.put("nombrePersona", viaje.nombrePersona)
        viajeJson.put("legajo", viaje.legajo)
        viajeJson.put("centroCostos", viaje.centroCostos)
        viajeJson.put("fechaFin", viaje.fechaFin)

        val raiz = JSONObject()
        raiz.put("formato", FORMATO)
        raiz.put("version", VERSION_FORMATO)
        raiz.put("exportado", System.currentTimeMillis())
        raiz.put("viaje", viajeJson)
        raiz.put("gastos", gastosJson)

        ZipOutputStream(BufferedOutputStream(destino)).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry(ENTRADA_JSON))
            zip.write(raiz.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.setLevel(Deflater.NO_COMPRESSION) // los JPEG ya están comprimidos
            for ((nombre, archivo) in fotos) {
                zip.putNextEntry(ZipEntry(nombre))
                archivo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        fotosFaltantes
    }

    /**
     * Importa un viaje como un viaje NUEVO (nunca pisa ni mezcla viajes existentes).
     * Si algo falla no queda nada a medias: se revierte la base y se borran las fotos copiadas.
     */
    suspend fun importar(context: Context, db: AppDatabase, origen: InputStream): ResultadoImportacion =
        withContext(Dispatchers.IO) {
            val fotosCopiadas = mutableMapOf<String, File>()
            try {
                val dirFotos = context.getExternalFilesDir("Pictures")
                    ?: throw ViajeBackupException("No hay almacenamiento disponible en el teléfono.")
                if (!dirFotos.exists()) dirFotos.mkdirs()

                // 1. Leer viaje.json y copiar las fotos (una sola pasada por el zip)
                val raiz: JSONObject = ZipInputStream(BufferedInputStream(origen)).use { zip ->
                    val primera = zip.nextEntry
                    if (primera == null || primera.name != ENTRADA_JSON) {
                        throw ViajeBackupException("El archivo no es una exportación de viaje de esta app.")
                    }
                    val json = JSONObject(leerLimitado(zip, MAX_JSON_BYTES).toString(Charsets.UTF_8))
                    validarCabecera(json)
                    val referenciadas = fotosReferenciadas(json)

                    var entrada = zip.nextEntry
                    while (entrada != null) {
                        val nombre = entrada.name
                        if (!entrada.isDirectory && nombre in referenciadas && !fotosCopiadas.containsKey(nombre)) {
                            // El nombre de la entrada NUNCA se usa como ruta: la foto se guarda con nombre propio
                            val destinoFoto = File.createTempFile("JPEG_imp_", ".jpg", dirFotos)
                            fotosCopiadas[nombre] = destinoFoto
                            destinoFoto.outputStream().use { copiarLimitado(zip, it, MAX_FOTO_BYTES) }
                        }
                        entrada = zip.nextEntry
                    }
                    json
                }

                // 2. Insertar viaje y gastos en una sola transacción
                val viajeJson = raiz.getJSONObject("viaje")
                val gastosJson = raiz.getJSONArray("gastos")

                val fecha = viajeJson.optString("fecha", "")
                val nombreOriginal = viajeJson.getString("nombre")
                val existentes = db.viajeDao().getAllViajes()
                var nombre = nombreOriginal
                var intento = 1
                while (existentes.any { it.nombre == nombre && it.fecha == fecha }) {
                    nombre = if (intento == 1) "$nombreOriginal (importado)" else "$nombreOriginal (importado $intento)"
                    intento++
                }

                val viaje = Viaje(
                    nombre = nombre,
                    fecha = fecha,
                    monedaPorDefecto = viajeJson.optString("monedaPorDefecto", ""),
                    imputacionPorDefectoPT = viajeJson.optString("imputacionPorDefectoPT", ""),
                    imputacionPorDefectoWP = viajeJson.optString("imputacionPorDefectoWP", ""),
                    nombrePersona = viajeJson.optString("nombrePersona", ""),
                    legajo = viajeJson.optString("legajo", ""),
                    centroCostos = viajeJson.optString("centroCostos", ""),
                    fechaFin = viajeJson.optString("fechaFin", "")
                )

                var fotosFaltantes = 0
                val ahora = System.currentTimeMillis()
                val gastosNuevos = mutableListOf<Gasto>()
                db.withTransaction {
                    val viajeId = db.viajeDao().insertViaje(viaje)
                    // Se insertan en el orden del archivo: los ids nuevos conservan el orden de carga original
                    for (i in 0 until gastosJson.length()) {
                        val o = gastosJson.getJSONObject(i)
                        val entradaFoto = if (o.isNull("foto")) "" else o.optString("foto", "")
                        val archivoFoto = fotosCopiadas[entradaFoto]
                        if (entradaFoto.isNotEmpty() && archivoFoto == null) fotosFaltantes++
                        val sinComprobante = o.optBoolean("sinComprobante", false)
                        val gasto = Gasto(
                            viajeId = viajeId,
                            descripcion = o.optString("descripcion", ""),
                            monto = o.getDouble("monto"),
                            fecha = o.optString("fecha", ""),
                            urlFotoRecibo = archivoFoto?.absolutePath ?: "",
                            moneda = o.optString("moneda", ""),
                            tipoGasto = o.optString("tipoGasto", ""),
                            formaDePago = o.optString("formaDePago", ""),
                            tagGasto = o.optString("tagGasto", ""),
                            imputacionPT = o.optString("imputacionPT", ""),
                            imputacionWP = o.optString("imputacionWP", ""),
                            nombrePersona = o.optString("nombrePersona", ""),
                            legajo = o.optString("legajo", ""),
                            centroCostos = o.optString("centroCostos", ""),
                            timestamp = o.optLong("timestamp", ahora),
                            sinComprobante = sinComprobante,
                            tamanoRecibo = TamanoRecibo.desde(o.optString("tamanoRecibo", "")).name,
                            importeDolares = if (o.has("importeDolares") && !o.isNull("importeDolares")) o.optDouble("importeDolares").takeIf { !it.isNaN() } else null,
                            dolaresEstimado = o.optBoolean("dolaresEstimado", false)
                        )
                        db.gastoDao().insertGasto(gasto)
                        gastosNuevos.add(gasto)
                    }
                }

                val agregado = agregarAConfiguracion(context, viaje, gastosNuevos)
                ResultadoImportacion(nombre, gastosNuevos.size, fotosFaltantes, agregado)
            } catch (e: CancellationException) {
                limpiar(fotosCopiadas)
                throw e
            } catch (e: ViajeBackupException) {
                limpiar(fotosCopiadas)
                throw e
            } catch (e: JSONException) {
                limpiar(fotosCopiadas)
                throw ViajeBackupException("El archivo está dañado o tiene un formato inválido.")
            } catch (e: Exception) {
                limpiar(fotosCopiadas)
                throw ViajeBackupException("No se pudo importar el viaje: ${e.message}")
            }
        }

    private fun validarCabecera(json: JSONObject) {
        if (json.optString("formato") != FORMATO) {
            throw ViajeBackupException("El archivo no es una exportación de viaje de esta app.")
        }
        if (json.optInt("version", 0) > VERSION_FORMATO) {
            throw ViajeBackupException("El archivo fue creado con una versión más nueva de la app. Actualizá la app para importarlo.")
        }
        if (!json.has("viaje") || !json.has("gastos")) {
            throw ViajeBackupException("El archivo está incompleto.")
        }
    }

    private fun fotosReferenciadas(json: JSONObject): Set<String> {
        val gastos = json.getJSONArray("gastos")
        val fotos = mutableSetOf<String>()
        for (i in 0 until gastos.length()) {
            val o = gastos.getJSONObject(i)
            if (o.isNull("foto")) continue
            val ruta = o.optString("foto", "")
            if (ruta.isEmpty()) continue
            if (!REGEX_FOTO.matches(ruta)) throw ViajeBackupException("El archivo contiene referencias de foto inválidas.")
            fotos.add(ruta)
        }
        if (fotos.size > MAX_FOTOS) throw ViajeBackupException("El archivo contiene demasiadas fotos.")
        return fotos
    }

    /**
     * Agrega a la configuración del usuario lo que el viaje usa y no tiene (tipos de gasto, monedas,
     * formas de pago, imputaciones). Sin esto, el Excel del viaje importado perdería columnas.
     * Nunca modifica ni borra lo existente.
     */
    private fun agregarAConfiguracion(context: Context, viaje: Viaje, gastos: List<Gasto>): List<String> {
        val prefs = context.getSharedPreferences("UserPrefs_local", Context.MODE_PRIVATE)
        val agregados = mutableListOf<String>()
        val editor = prefs.edit()

        fun conjunto(clave: String): MutableSet<String> = HashSet(prefs.getStringSet(clave, emptySet()) ?: emptySet())

        val tipos = conjunto("TIPOS_GASTO")
        gastos.map { it.tipoGasto }.filter { it.isNotBlank() }.distinct().filter { it !in tipos }.forEach {
            tipos.add(it); agregados.add("Tipo de gasto: $it")
        }
        editor.putStringSet("TIPOS_GASTO", tipos)

        val monedas = conjunto("MONEDAS")
        (gastos.map { it.moneda } + viaje.monedaPorDefecto).filter { it.isNotBlank() }.distinct().filter { it !in monedas }.forEach {
            monedas.add(it); agregados.add("Moneda: $it")
        }
        editor.putStringSet("MONEDAS", monedas)

        val formasPago = conjunto("FORMAS_PAGO")
        val nombresFormas = formasPago.map { it.substringBefore("::") }.toMutableSet()
        gastos.map { it.formaDePago }.filter { it.isNotBlank() }.distinct().filter { it !in nombresFormas }.forEach { nombre ->
            val tag = gastos.first { it.formaDePago == nombre }.tagGasto
            val prefijo = Regex("^(.*?)\\d+$").find(tag)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
                ?: nombre.filter { it.isLetter() }.take(3).uppercase().ifEmpty { "GST" }
            formasPago.add("$nombre::$prefijo"); nombresFormas.add(nombre)
            agregados.add("Forma de pago: $nombre ($prefijo)")
        }
        editor.putStringSet("FORMAS_PAGO", formasPago)

        val imputaciones = conjunto("IMPUTACIONES")
        val usadas = gastos.map { it.imputacionPT to it.imputacionWP } + (viaje.imputacionPorDefectoPT to viaje.imputacionPorDefectoWP)
        usadas.filter { it.first.isNotBlank() && it.second.isNotBlank() }.distinct()
            .filter { "${it.first}::${it.second}" !in imputaciones }.forEach {
                imputaciones.add("${it.first}::${it.second}"); agregados.add("Imputación: PT ${it.first} / WP ${it.second}")
            }
        editor.putStringSet("IMPUTACIONES", imputaciones)

        if (agregados.isNotEmpty()) editor.apply()
        return agregados
    }

    private fun limpiar(fotos: Map<String, File>) {
        fotos.values.forEach { runCatching { it.delete() } }
    }

    private fun leerLimitado(entrada: InputStream, max: Long): ByteArray {
        val salida = ByteArrayOutputStream()
        copiarLimitado(entrada, salida, max)
        return salida.toByteArray()
    }

    private fun copiarLimitado(entrada: InputStream, salida: OutputStream, max: Long) {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val leidos = entrada.read(buffer)
            if (leidos < 0) break
            total += leidos
            if (total > max) throw ViajeBackupException("El archivo contiene datos demasiado grandes.")
            salida.write(buffer, 0, leidos)
        }
    }
}
