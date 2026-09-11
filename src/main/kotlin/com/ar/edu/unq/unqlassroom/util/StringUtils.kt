package com.ar.edu.unq.unqlassroom.util

import java.text.Normalizer

fun String.removerTildes(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalized
        .replace("\\p{M}".toRegex(), "")
        .replace("[´`¨^~]".toRegex(), "")
}
