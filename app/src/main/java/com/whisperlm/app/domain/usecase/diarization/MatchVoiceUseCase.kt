package com.whisperlm.app.domain.usecase.diarization

import com.whisperlm.app.domain.model.Person
import com.whisperlm.app.domain.repository.PersonRepository
import com.whisperlm.app.core.util.EmbeddingUtils
import javax.inject.Inject

/**
 * Given a new speaker embedding, finds the best-matching known person
 * using cosine similarity against all stored voice embeddings.
 *
 * Returns a Pair of (Person, score) if a match above threshold is found,
 * or null if no confident match exists.
 */
class MatchVoiceUseCase @Inject constructor(
    private val personRepository: PersonRepository
) {
    suspend operator fun invoke(
        candidateEmbedding: FloatArray,
        threshold: Float = 0.75f
    ): Pair<Person, Float>? {
        val allEmbeddings = personRepository.getAllVoiceEmbeddings()
        if (allEmbeddings.isEmpty()) return null

        var bestScore = -1f
        var bestPersonId = -1L

        for (stored in allEmbeddings) {
            val score = EmbeddingUtils.cosineSimilarity(candidateEmbedding, stored.embedding)
            if (score > bestScore) {
                bestScore = score
                bestPersonId = stored.personId
            }
        }

        if (bestScore < threshold) return null

        val person = personRepository.getPersonById(bestPersonId) ?: return null
        return Pair(person, bestScore)
    }
}
