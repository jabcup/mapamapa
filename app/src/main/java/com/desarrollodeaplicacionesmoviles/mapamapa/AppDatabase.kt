package com.desarrollodeaplicacionesmoviles.mapamapa

import android.content.Context
import androidx.room.*

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

@Entity(tableName = "puntos_personales")
data class PuntoPersonal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val descripcion: String,
    val latitud: String,
    val longitud: String
)

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
interface PuntosPersonalesDao {
    @Query("SELECT * FROM puntos_personales")
    suspend fun getAll(): List<PuntoPersonal>

    @Insert
    suspend fun insert(punto: PuntoPersonal)

    @Delete
    suspend fun delete(punto: PuntoPersonal)
}

@Database(entities = [PuntoFavorito::class, PuntoPersonal::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritosDao(): FavoritosDao
    abstract fun puntosPersonalesDao(): PuntosPersonalesDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "mapamapa.db")
                    .build().also { INSTANCE = it }
            }
        }
    }
}