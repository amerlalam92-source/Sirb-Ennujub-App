package com.example.domain.settings

import kotlinx.coroutines.flow.Flow

interface TermsRepository {
    fun observeTerms(): Flow<CustomTerms>
    suspend fun updateTerms(terms: CustomTerms): Result<Unit>
    fun replaceCustomWords(text: String, terms: CustomTerms): String
}
