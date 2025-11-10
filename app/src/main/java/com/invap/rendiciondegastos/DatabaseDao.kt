package com.invap.rendiciondegastos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * DAO (Data Access Object) para la tabla 'viajes'
 */
@Dao
interface ViajeDao {

    @Insert
    suspend fun insertViaje(viaje: Viaje): Long

    @Update
    suspend fun updateViaje(viaje: Viaje)

    @Delete
    suspend fun deleteViaje(viaje: Viaje)

    @Query("SELECT * FROM viajes ORDER BY id DESC")
    suspend fun getAllViajes(): List<Viaje>

    @Query("SELECT * FROM viajes WHERE id = :viajeId")
    suspend fun getViajeById(viajeId: Long): Viaje?
}

/**
 * DAO (Data Access Object) para la tabla 'gastos'
 */
@Dao
interface GastoDao {

    @Insert
    suspend fun insertGasto(gasto: Gasto): Long

    @Update
    suspend fun updateGasto(gasto: Gasto)

    @Delete
    suspend fun deleteGasto(gasto: Gasto)

    @Query("SELECT * FROM gastos WHERE viajeId = :viajeId ORDER BY timestamp")
    suspend fun getGastosByViajeId(viajeId: Long): List<Gasto>

    /**
     * Esta consulta es necesaria para replicar la lógica de generación de Tags
     * (ej: TDR1, TDR2) que antes se hacía contando documentos en Firestore.
     */
    @Query("SELECT COUNT(*) FROM gastos WHERE viajeId = :viajeId AND formaDePago = :formaDePago")
    suspend fun countGastosByFormaDePago(viajeId: Long, formaDePago: String): Int

}