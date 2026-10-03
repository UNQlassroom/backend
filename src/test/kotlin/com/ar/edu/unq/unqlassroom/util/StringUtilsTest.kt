package com.ar.edu.unq.unqlassroom.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringUtilsTest {

    @Test
    fun `removerTildes removes acute accents from vowels`() {
        assertEquals("aeiouAEIOU", "áéíóúÁÉÍÓÚ".removerTildes())
    }

    @Test
    fun `removerTildes removes diaeresis`() {
        assertEquals("linguistica", "lingüística".removerTildes())
        assertEquals("Verguenza", "Vergüenza".removerTildes())
    }

    @Test
    fun `removerTildes converts enie to n`() {
        assertEquals("ano", "año".removerTildes())
        assertEquals("DISENO", "DISEÑO".removerTildes())
    }

    @Test
    fun `removerTildes removes grave, circumflex, and tilde accents`() {
        assertEquals("aeiou", "àèìòù".removerTildes())
        assertEquals("aeiou", "âêîôû".removerTildes())
        assertEquals("aoAO", "ãõÃÕ".removerTildes())
    }

    @Test
    fun `removerTildes removes standalone accents`() {
        assertEquals("Programacion", "Programacio´n".removerTildes())
        assertEquals("Materia", "´Materia`".removerTildes())
    }

    @Test
    fun `removerTildes works on common course names in Spanish`() {
        assertEquals("Programacion Funcional", "Programación Funcional".removerTildes())
        assertEquals("Matematica Discreta", "Matemática Discreta".removerTildes())
        assertEquals("Introduccion a la Programacion", "Introducción a la Programación".removerTildes())
        assertEquals("Ingenieria de Software", "Ingeniería de Software".removerTildes())
        assertEquals("Quimica Organica", "Química Orgánica".removerTildes())
        assertEquals("Fisica I", "Física I".removerTildes())
    }

    @Test
    fun `toRepoSlug sanitizes string for github repositories`() {
        assertEquals("estructuras_de_datos", "Estructuras de Datos".toRepoSlug())
        assertEquals("programacion_funcional", "  Programación  Funcional  ".toRepoSlug())
        assertEquals("tp_1_recursion", "TP-1: Recursión!".toRepoSlug())
        assertEquals("grupo_alpha", "Grupo - Alpha".toRepoSlug())
        assertEquals("agustin", "agustín".toRepoSlug())
    }
}
