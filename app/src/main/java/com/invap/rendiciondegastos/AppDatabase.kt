package com.invap.rendiciondegastos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
// --- IMPORT AÑADIDO ---


// Versión 2 (esto es correcto)
@Database(entities = [Viaje::class, Gasto::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun viajeDao(): ViajeDao
    abstract fun gastoDao(): GastoDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Migraciones de esquema. Agregar una nueva por cada cambio de versión. */
        /** v2 -> v3: tamaño de impresión del recibo. Los gastos existentes quedan en CHICO (como se imprimían). */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE gastos ADD COLUMN tamanoRecibo TEXT NOT NULL DEFAULT 'CHICO'")
            }
        }

        /** v3 -> v4: importe en dólares por gasto (opcional) y fecha de fin del viaje. */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE gastos ADD COLUMN importeDolares REAL")
                db.execSQL("ALTER TABLE gastos ADD COLUMN dolaresEstimado INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE viajes ADD COLUMN fechaFin TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf(MIGRATION_2_3, MIGRATION_3_4)

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rendicion_gastos_db"
                )
                    // Los datos solo existen en el teléfono: NUNCA borrar la base ante un cambio
                    // de esquema. Para cada nueva versión, agregar un Migration a MIGRATIONS.
                    // Solo la versión 1 (anterior a la 2.0) se permite recrear.
                    .fallbackToDestructiveMigrationFrom(1)
                    .addMigrations(*MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}