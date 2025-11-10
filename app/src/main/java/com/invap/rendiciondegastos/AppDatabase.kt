package com.invap.rendiciondegastos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Clase principal de la base de datos Room.
 * Define las entidades (tablas) y provee acceso a los DAOs.
 */
@Database(entities = [Viaje::class, Gasto::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // Define los DAOs que la base de datos expondrá
    abstract fun viajeDao(): ViajeDao
    abstract fun gastoDao(): GastoDao

    companion object {
        // La anotación @Volatile asegura que el valor de INSTANCE
        // esté siempre actualizado y sea el mismo para todos los hilos de ejecución.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Obtiene la instancia única de la base de datos (patrón Singleton).
         * Si la instancia no existe, la crea de forma segura (synchronized).
         */
        fun getInstance(context: Context): AppDatabase {
            // El bloque synchronized asegura que solo un hilo a la vez
            // pueda ejecutar este código, evitando crear dos instancias
            // de la base de datos por error.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rendicion_gastos_db" // Nombre del archivo de la base de datos
                ).build()
                INSTANCE = instance
                // Retorna la instancia recién creada
                instance
            }
        }
    }
}