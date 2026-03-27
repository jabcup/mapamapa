package com.desarrollodeaplicacionesmoviles.mapamapa.ui.theme


import retrofit2.http.GET
import retrofit2.http.Path
import com.desarrollodeaplicacionesmoviles.mapamapa.Barrio
import com.desarrollodeaplicacionesmoviles.mapamapa.Delimitador
import com.desarrollodeaplicacionesmoviles.mapamapa.Punto
import com.desarrollodeaplicacionesmoviles.mapamapa.Tipo

interface ApiService {

    @GET("puntos")
    suspend fun listarPuntos(): List<Punto>

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
}