package me.rerere.ai.provider

import kotlinx.coroutines.flow.Flow

/**
 * Embedding provider interface for generating text embeddings
 */
interface EmbeddingProvider<T : ProviderSetting> {
    /**
     * Generate embeddings for multiple texts
     * @param providerSetting Provider configuration
     * @param texts List of texts to embed
     * @param model Model ID for embedding
     * @return List of embedding vectors
     */
    suspend fun embed(
        providerSetting: T,
        texts: List<String>,
        model: String,
    ): List<FloatArray>

    /**
     * Generate embedding for a single query text
     * @param providerSetting Provider configuration
     * @param text Text to embed
     * @param model Model ID for embedding
     * @return Embedding vector
     */
    suspend fun embedQuery(
        providerSetting: T,
        text: String,
        model: String,
    ): FloatArray {
        return embed(providerSetting, listOf(text), model).first()
    }
}

data class EmbeddingParams(
    val model: String,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBody: List<CustomBody> = emptyList(),
)
