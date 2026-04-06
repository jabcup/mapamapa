package com.desarrollodeaplicacionesmoviles.mapamapa

import android.content.Context
import androidx.room.*

// --------------------------------------------------------------
// ENTITIES
// --------------------------------------------------------------

@Entity(tableName = "favoritos")
data class PuntoFavorito(
    @PrimaryKey val id: Int,
    val nombre: String,
    val descripcion: String,
    val latitud: String,
    val longitud: String,
    val tipo_nombre: String,
    val tipo_color: String
)

@Entity(tableName = "rutas")
data class Ruta(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val polyline: String
    // Puedes agregar más campos si los necesitas (origen, destino, fecha, etc.)
)

@Entity(tableName = "puntos_personales")
data class PuntoPersonal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val descripcion: String,
    val latitud: String,
    val longitud: String,
    val isHome: Boolean = false  // nuevo campo, por defecto false
)

// --------------------------------------------------------------
// DAOs
// --------------------------------------------------------------

@Dao
interface FavoritosDao {
    @Query("SELECT * FROM favoritos")
    suspend fun getAll(): List<PuntoFavorito>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(punto: PuntoFavorito)

    @Delete
    suspend fun delete(punto: PuntoFavorito)

    @Query("SELECT EXISTS(SELECT 1 FROM favoritos WHERE id = :id)")
    suspend fun esFavorito(id: Int): Boolean
}

@Dao
interface RutaDao {
    @Query("SELECT * FROM rutas")
    suspend fun getAll(): List<Ruta>

    @Insert
    suspend fun insert(ruta: Ruta)

    @Delete
    suspend fun delete(ruta: Ruta)

    @Query("DELETE FROM rutas")
    suspend fun deleteAll()
}

@Dao
interface PuntosPersonalesDao {
    @Query("SELECT * FROM puntos_personales")
    suspend fun getAll(): List<PuntoPersonal>

    @Query("SELECT * FROM puntos_personales WHERE isHome = 1 LIMIT 1")
    suspend fun getHome(): PuntoPersonal?

    @Insert
    suspend fun insert(punto: PuntoPersonal)

    @Update
    suspend fun update(punto: PuntoPersonal)

    @Delete
    suspend fun delete(punto: PuntoPersonal)

    // Elimina la bandera "hogar" de cualquier punto que la tenga
    @Query("UPDATE puntos_personales SET isHome = 0 WHERE isHome = 1")
    suspend fun clearHomeFlag()

    /**
     * Inserta o actualiza un punto asegurando que solo uno tenga isHome = true.
     * Si el punto pasado tiene isHome = true, primero limpia la bandera de cualquier otro.
     */
    @Transaction
    suspend fun setAsHome(punto: PuntoPersonal) {
        if (punto.isHome) {
            clearHomeFlag()
        }
        // Si el punto ya existe (id != 0), hace update; si no, insert
        if (punto.id == 0) {
            insert(punto)
        } else {
            update(punto)
        }
    }
}

// --------------------------------------------------------------
// DATABASE
// --------------------------------------------------------------

@Database(
    entities = [PuntoFavorito::class, Ruta::class, PuntoPersonal::class],
    version = 2  // incrementada por los nuevos cambios
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritosDao(): FavoritosDao
    abstract fun rutaDao(): RutaDao
    abstract fun puntosPersonalesDao(): PuntosPersonalesDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mapamapa.db"
                )
                    .fallbackToDestructiveMigration() // Borra datos al cambiar de versión (solo desarrollo)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}