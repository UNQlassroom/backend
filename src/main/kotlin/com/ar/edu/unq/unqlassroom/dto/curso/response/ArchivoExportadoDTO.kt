package com.ar.edu.unq.unqlassroom.dto.curso.response

data class ArchivoExportadoDTO(
    val nombreArchivo: String,
    val contenido: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArchivoExportadoDTO

        if (nombreArchivo != other.nombreArchivo) return false
        if (!contenido.contentEquals(other.contenido)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nombreArchivo.hashCode()
        result = 31 * result + contenido.contentHashCode()
        return result
    }
}
