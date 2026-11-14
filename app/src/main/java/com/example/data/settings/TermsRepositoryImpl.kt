package com.example.data.settings

import com.example.domain.settings.CustomTerms
import com.example.domain.settings.TermsRepository
import kotlinx.coroutines.flow.Flow

class TermsRepositoryImpl(
    private val dataSource: TermsFirestoreDataSource
) : TermsRepository {

    override fun observeTerms(): Flow<CustomTerms> {
        return dataSource.observeTerms()
    }

    override suspend fun updateTerms(terms: CustomTerms): Result<Unit> {
        return dataSource.updateTerms(terms)
    }

    override fun replaceCustomWords(text: String, terms: CustomTerms): String {
        if (text.isEmpty() || terms.customWords.isEmpty()) return text
        var result = text
        terms.customWords.forEach { (original, replacement) ->
            if (original.isNotEmpty()) {
                result = result.replace(original, replacement)
            }
        }
        return result
    }
}
