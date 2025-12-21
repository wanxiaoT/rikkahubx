package me.rerere.rikkahub.data.knowledge.loader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.document.DocxParser
import me.rerere.document.PdfParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class DocumentChunk(
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Base interface for document loaders
 */
sealed interface DocumentLoader {
    suspend fun load(): List<DocumentChunk>
}

/**
 * Loader for plain text files (.txt)
 */
class TextFileLoader(
    private val file: File,
) : DocumentLoader {
    override suspend fun load(): List<DocumentChunk> {
        if (!file.exists() || !file.canRead()) {
            return emptyList()
        }

        val content = file.readText()
        return listOf(
            DocumentChunk(
                content = content,
                metadata = mapOf(
                    "source" to file.absolutePath,
                    "type" to "text",
                    "filename" to file.name,
                )
            )
        )
    }
}

/**
 * Loader for Markdown files (.md)
 */
class MarkdownLoader(
    private val file: File,
) : DocumentLoader {
    override suspend fun load(): List<DocumentChunk> {
        if (!file.exists() || !file.canRead()) {
            return emptyList()
        }

        val content = file.readText()
        // Remove markdown syntax but keep the text
        val cleanedContent = cleanMarkdown(content)

        return listOf(
            DocumentChunk(
                content = cleanedContent,
                metadata = mapOf(
                    "source" to file.absolutePath,
                    "type" to "markdown",
                    "filename" to file.name,
                )
            )
        )
    }

    private fun cleanMarkdown(markdown: String): String {
        return markdown
            // Remove code blocks
            .replace(Regex("```[\\s\\S]*?```"), "")
            // Remove inline code
            .replace(Regex("`[^`]+`"), "")
            // Remove images
            .replace(Regex("!\\[.*?]\\(.*?\\)"), "")
            // Remove links but keep text
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            // Remove headers markers
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            // Remove emphasis markers
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("__([^_]+)__"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("_([^_]+)_"), "$1")
            // Remove horizontal rules
            .replace(Regex("^[-*_]{3,}$", RegexOption.MULTILINE), "")
            // Remove list markers
            .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "")
            // Clean up extra whitespace
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}

/**
 * Loader for note content (plain text string)
 */
class NoteLoader(
    private val content: String,
    private val noteName: String = "Note",
) : DocumentLoader {
    override suspend fun load(): List<DocumentChunk> {
        if (content.isBlank()) {
            return emptyList()
        }

        return listOf(
            DocumentChunk(
                content = content,
                metadata = mapOf(
                    "type" to "note",
                    "name" to noteName,
                )
            )
        )
    }
}

/**
 * Loader for PDF files (.pdf)
 */
class PdfLoader(
    private val file: File,
) : DocumentLoader {
    override suspend fun load(): List<DocumentChunk> = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) {
            return@withContext emptyList()
        }

        try {
            val content = PdfParser.parserPdf(file)
            listOf(
                DocumentChunk(
                    content = content,
                    metadata = mapOf(
                        "source" to file.absolutePath,
                        "type" to "pdf",
                        "filename" to file.name,
                    )
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Loader for DOCX files (.docx)
 */
class DocxLoader(
    private val file: File,
) : DocumentLoader {
    override suspend fun load(): List<DocumentChunk> = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) {
            return@withContext emptyList()
        }

        try {
            val content = DocxParser.parse(file)
            listOf(
                DocumentChunk(
                    content = content,
                    metadata = mapOf(
                        "source" to file.absolutePath,
                        "type" to "docx",
                        "filename" to file.name,
                    )
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Loader for URL content
 */
class UrlLoader(
    private val url: String,
    private val client: OkHttpClient,
) : DocumentLoader {
    override suspend fun load(): List<DocumentChunk> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; RikkaHub/1.0)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            val contentType = response.header("Content-Type") ?: ""

            val content = if (contentType.contains("text/html")) {
                // Extract text from HTML
                extractTextFromHtml(body)
            } else {
                // Plain text
                body
            }

            listOf(
                DocumentChunk(
                    content = content,
                    metadata = mapOf(
                        "source" to url,
                        "type" to "url",
                        "content_type" to contentType,
                    )
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractTextFromHtml(html: String): String {
        return html
            // Remove script tags
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            // Remove style tags
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            // Remove HTML comments
            .replace(Regex("<!--[\\s\\S]*?-->"), "")
            // Replace br and p tags with newlines
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            // Remove all remaining HTML tags
            .replace(Regex("<[^>]+>"), "")
            // Decode common HTML entities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            // Clean up extra whitespace
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}

/**
 * Factory for creating document loaders based on file type
 */
object DocumentLoaderFactory {
    fun createLoader(file: File): DocumentLoader? {
        return when (file.extension.lowercase()) {
            "txt" -> TextFileLoader(file)
            "md", "markdown" -> MarkdownLoader(file)
            "pdf" -> PdfLoader(file)
            "docx" -> DocxLoader(file)
            else -> null
        }
    }

    fun createNoteLoader(content: String, name: String = "Note"): DocumentLoader {
        return NoteLoader(content, name)
    }

    fun createUrlLoader(url: String, client: OkHttpClient): DocumentLoader {
        return UrlLoader(url, client)
    }

    fun getSupportedExtensions(): List<String> {
        return listOf("txt", "md", "markdown", "pdf", "docx")
    }
}
