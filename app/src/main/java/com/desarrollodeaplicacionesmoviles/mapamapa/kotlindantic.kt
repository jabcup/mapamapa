package com.desarrollodeaplicacionesmoviles.mapamapa


data class Tipo(
    val id: Int,
    val nombre: String,
    val color: String
)

data class Delimitador(
    val id: Int,
    val latitud: String,
    val longitud: String,
    val barrio_id: Int
)

data class Barrio(
    val id: Int,
    val nombre: String,
    val tipo_id: Int,
    val tipo: Tipo,
    val delimitadores: List<Delimitador>
)

data class Punto(
    val id: Int,
    val nombre: String,
    val descripcion: String,
    val latitud: String,
    val longitud: String,
    val tipo_id: Int,
    val tipo: Tipo
)