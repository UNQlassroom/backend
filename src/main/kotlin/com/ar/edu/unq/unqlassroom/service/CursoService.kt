package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO

interface CursoService {

    fun crear(dto: CursoRequestDTO): CursoResponseDTO

}