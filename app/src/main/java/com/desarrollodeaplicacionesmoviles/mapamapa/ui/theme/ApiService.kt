package com.desarrollodeaplicacionesmoviles.mapamapa.ui.theme



import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.desarrollodeaplicacionesmoviles.mapamapa.Barrio
import com.desarrollodeaplicacionesmoviles.mapamapa.Delimitador
import com.desarrollodeaplicacionesmoviles.mapamapa.Polyline
import com.desarrollodeaplicacionesmoviles.mapamapa.Punto
import com.desarrollodeaplicacionesmoviles.mapamapa.Tipo

interface ApiService {

    @GET("puntos")
    suspend fun listarPuntos(): List<Punto>

    @GET("puntos/escenciales")
    suspend fun listarPuntosEscenciales(): List<Punto>

    @GET("puntos/{id}")
    suspend fun obtenerPunto(@Path("id") id: Int): Punto

    @GET("barrios")
    suspend fun listarBarrios(): List<Barrio>

    @GET("barrios/{id}")
    suspend fun obtenerBarrio(@Path("id") id: Int): Barrio

    @GET("tipos")
    suspend fun listarTipos(): List<Tipo>

    @GET("tipos/{id}")
    suspend fun obtenerTipo(@Path("id") id: Int): Tipo

    @GET("delimitadores")
    suspend fun listarDelimitadores(): List<Delimitador>

    @GET("delimitadores/{id}")
    suspend fun obtenerDelimitador(@Path("id") id: Int): Delimitador

    @GET("rutasimple")
    suspend fun obtenerPolylineSimple(
        @Query("lat1") lat1: String,
        @Query("lon1") long1: String,
        @Query("lat2") lat2: String,
        @Query("lon2") long2: String
    ): String
}



//
//"http://127.0.0.1:5000/route/v1/driving/-68.13517,-16.50018;-68.12956,-16.51100?geometries=polyline" | jq .