package com.ar.edu.unq.unqlassroom.util

import java.text.Normalizer

fun String.removerTildes(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalized
        .replace("\\p{M}".toRegex(), "")
        .replace("[´`¨^~]".toRegex(), "")
}

fun String.toRepoSlug(): String {
    return this.removerTildes()
        .trim()
        .lowercase()
        .replace(Regex("[\\s-]+"), "_")
        .replace(Regex("[^a-z0-9_]"), "")
        .replace(Regex("_+"), "_")
        .trim('_')
}
