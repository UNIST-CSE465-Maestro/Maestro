package com.maestro.app.domain.repository

import com.maestro.app.domain.model.KnowledgeDashboard

interface KnowledgeRepository {
    suspend fun loadDashboard(): KnowledgeDashboard
}
