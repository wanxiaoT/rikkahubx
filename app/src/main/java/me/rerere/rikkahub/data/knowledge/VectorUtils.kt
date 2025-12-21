package me.rerere.rikkahub.data.knowledge

import kotlin.math.sqrt

object VectorUtils {
    /**
     * Calculate cosine similarity between two vectors
     * @return similarity score between -1 and 1
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension" }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }

    /**
     * Calculate dot product between two vectors
     */
    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension" }

        var result = 0f
        for (i in a.indices) {
            result += a[i] * b[i]
        }
        return result
    }

    /**
     * Calculate Euclidean distance between two vectors
     */
    fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension" }

        var sumSquared = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sumSquared += diff * diff
        }
        return sqrt(sumSquared)
    }

    /**
     * Normalize a vector to unit length
     */
    fun normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) {
            norm += v * v
        }
        norm = sqrt(norm)

        return if (norm == 0f) {
            vector.copyOf()
        } else {
            FloatArray(vector.size) { i -> vector[i] / norm }
        }
    }
}
