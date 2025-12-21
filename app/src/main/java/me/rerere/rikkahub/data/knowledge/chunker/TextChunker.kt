package me.rerere.rikkahub.data.knowledge.chunker

/**
 * Text chunker for splitting text into overlapping chunks
 */
class TextChunker(
    private val chunkSize: Int = 500,
    private val chunkOverlap: Int = 100,
) {
    init {
        require(chunkSize > 0) { "Chunk size must be positive" }
        require(chunkOverlap >= 0) { "Chunk overlap must be non-negative" }
        require(chunkOverlap < chunkSize) { "Chunk overlap must be less than chunk size" }
    }

    /**
     * Split text into chunks with overlap
     */
    fun chunk(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val cleanedText = text.trim()
        if (cleanedText.length <= chunkSize) {
            return listOf(cleanedText)
        }

        val chunks = mutableListOf<String>()
        var start = 0
        val step = chunkSize - chunkOverlap

        while (start < cleanedText.length) {
            val end = minOf(start + chunkSize, cleanedText.length)
            var chunk = cleanedText.substring(start, end)

            // Try to find a natural break point (paragraph, sentence, or word)
            if (end < cleanedText.length) {
                chunk = findNaturalBreak(chunk)
            }

            if (chunk.isNotBlank()) {
                chunks.add(chunk.trim())
            }

            start += step
        }

        return chunks.distinct()
    }

    /**
     * Try to find a natural break point in the chunk
     */
    private fun findNaturalBreak(chunk: String): String {
        // Try to break at paragraph
        val paragraphBreak = chunk.lastIndexOf("\n\n")
        if (paragraphBreak > chunkSize / 2) {
            return chunk.substring(0, paragraphBreak)
        }

        // Try to break at sentence
        val sentenceBreaks = listOf("。", ".", "！", "!", "？", "?", "；", ";")
        for (delimiter in sentenceBreaks) {
            val lastIndex = chunk.lastIndexOf(delimiter)
            if (lastIndex > chunkSize / 2) {
                return chunk.substring(0, lastIndex + 1)
            }
        }

        // Try to break at line
        val lineBreak = chunk.lastIndexOf("\n")
        if (lineBreak > chunkSize / 2) {
            return chunk.substring(0, lineBreak)
        }

        // Try to break at word (space)
        val spaceBreak = chunk.lastIndexOf(" ")
        if (spaceBreak > chunkSize / 2) {
            return chunk.substring(0, spaceBreak)
        }

        return chunk
    }

    /**
     * Split text by paragraphs first, then chunk each paragraph
     */
    fun chunkByParagraphs(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val paragraphs = text.split(Regex("\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            if (paragraph.length > chunkSize) {
                // Chunk large paragraphs
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                }
                chunks.addAll(chunk(paragraph))
            } else if (currentChunk.length + paragraph.length + 2 <= chunkSize) {
                // Add to current chunk
                if (currentChunk.isNotEmpty()) {
                    currentChunk.append("\n\n")
                }
                currentChunk.append(paragraph)
            } else {
                // Start new chunk
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                }
                currentChunk = StringBuilder(paragraph)
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks.filter { it.isNotBlank() }
    }
}
