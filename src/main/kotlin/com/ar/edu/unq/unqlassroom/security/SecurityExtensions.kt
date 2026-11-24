package com.ar.edu.unq.unqlassroom.security

import org.springframework.security.core.Authentication

fun Authentication.esDocente(): Boolean =
    authorities.any { it.authority == "ROLE_DOCENTE" }
