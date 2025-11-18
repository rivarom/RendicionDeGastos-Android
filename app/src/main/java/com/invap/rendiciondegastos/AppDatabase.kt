package com.invap.rendiciondegastos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
// --- IMPORT AÑADIDO ---


// Versión 2 (esto es correcto)
@Database(entities = [Viaje::class, Gasto::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun viajeDao(): ViajeDao
    abstract fun gastoDao(): GastoDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rendicion_gastos_db"
                )
                    // Esta línea ahora funcionará gracias a la importación
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}