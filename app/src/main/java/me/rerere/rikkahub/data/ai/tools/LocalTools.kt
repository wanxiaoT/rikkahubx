package me.rerere.rikkahub.data.ai.tools

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("file_system")
    data object FileSystem : LocalToolOption()

    @Serializable
    @SerialName("web_fetch")
    data object WebFetch : LocalToolOption()
}

class LocalTools(private val context: Context) {

    // 获取权限提示信息
    private fun getPermissionHint(): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                "请授予存储权限。您可以：\n" +
                "1. 授予「照片和视频」权限来访问媒体文件\n" +
                "2. 或者在系统设置中授予「所有文件访问权限」来访问所有文件"
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                "请在系统设置中授予「所有文件访问权限」: 设置 -> 应用 -> RikkaHubX -> 权限 -> 文件和媒体 -> 允许管理所有文件"
            }
            else -> {
                "请在系统设置中授予存储权限"
            }
        }
    }

    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            description = "Execute JavaScript code with QuickJS. If use this tool to calculate math, better to add `toFixed` to the code.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "The JavaScript code to execute")
                        })
                    },
                )
            },
            execute = {
                val context = QuickJSContext.create()
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                val result = context.evaluate(code)
                buildJsonObject {
                    put(
                        "result", when (result) {
                            is QuickJSObject -> JsonPrimitive(result.stringify())
                            else -> JsonPrimitive(result.toString())
                        }
                    )
                }
            }
        )
    }

    /**
     * 文件读取工具
     */
    val fileReadTool by lazy {
        Tool(
            name = "read_file",
            description = "Read the content of a file from the device. Returns the file content as text. Supports reading files from app internal storage, external storage, and SD card.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the file to read, e.g., /sdcard/Documents/example.txt")
                        })
                        put("encoding", buildJsonObject {
                            put("type", "string")
                            put("description", "The character encoding to use (default: UTF-8). Options: UTF-8, GBK, ISO-8859-1")
                        })
                        put("returnMode", buildJsonObject {
                            put("type", "string")
                            put("description", "Response detail level: 'minimal' (content only), 'normal' (content + metadata, default)")
                        })
                    },
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val encoding = args.jsonObject["encoding"]?.jsonPrimitive?.contentOrNull ?: "UTF-8"
                val returnMode = args.jsonObject["returnMode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "normal"

                buildJsonObject {
                    if (path.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Path is required"))
                    } else {
                        try {
                            val file = File(path)
                            if (!file.exists()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("File not found: $path"))
                            } else if (!file.isFile) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Path is not a file: $path"))
                            } else if (!file.canRead()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Cannot read file (permission denied): $path. ${getPermissionHint()}"))
                                put("needPermission", JsonPrimitive(true))
                            } else {
                                val content = file.readText(charset(encoding))
                                put("success", JsonPrimitive(true))
                                put("content", JsonPrimitive(content))

                                if (returnMode != "minimal") {
                                    // Normal 模式:返回额外的元数据
                                    put("size", JsonPrimitive(file.length()))
                                    put("lastModified", JsonPrimitive(file.lastModified()))
                                }
                            }
                        } catch (e: Exception) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Failed to read file: ${e.message}"))
                        }
                    }
                }
            }
        )
    }

    /**
     * 文件写入工具
     */
    val fileWriteTool by lazy {
        Tool(
            name = "write_file",
            description = "Write content to a file on the device. Can create new files or overwrite existing ones. Supports writing to app internal storage, external storage, and SD card.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the file to write, e.g., /sdcard/Documents/example.txt")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "The content to write to the file")
                        })
                        put("append", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, append to the file instead of overwriting (default: false)")
                        })
                        put("encoding", buildJsonObject {
                            put("type", "string")
                            put("description", "The character encoding to use (default: UTF-8). Options: UTF-8, GBK, ISO-8859-1")
                        })
                        put("createDirs", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, create parent directories if they don't exist (default: true)")
                        })
                        put("returnMode", buildJsonObject {
                            put("type", "string")
                            put("description", "Response detail level: 'minimal' (filename only), 'normal' (full path + size, default)")
                        })
                    },
                    required = listOf("path", "content")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val content = args.jsonObject["content"]?.jsonPrimitive?.contentOrNull
                val append = args.jsonObject["append"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val encoding = args.jsonObject["encoding"]?.jsonPrimitive?.contentOrNull ?: "UTF-8"
                val createDirs = args.jsonObject["createDirs"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
                val returnMode = args.jsonObject["returnMode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "normal"

                buildJsonObject {
                    if (path.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Path is required"))
                    } else if (content == null) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Content is required"))
                    } else {
                        try {
                            val file = File(path)

                            // 创建父目录
                            if (createDirs && file.parentFile?.exists() == false) {
                                file.parentFile?.mkdirs()
                            }

                            if (append) {
                                file.appendText(content, charset(encoding))
                            } else {
                                file.writeText(content, charset(encoding))
                            }

                            put("success", JsonPrimitive(true))

                            if (returnMode == "minimal") {
                                // Minimal 模式:只返回文件名
                                put("path", JsonPrimitive(file.name))
                            } else {
                                // Normal 模式:返回完整路径和大小
                                put("path", JsonPrimitive(file.absolutePath))
                                put("size", JsonPrimitive(file.length()))
                            }
                        } catch (e: Exception) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Failed to write file: ${e.message}"))
                        }
                    }
                }
            }
        )
    }

    /**
     * 目录列表工具
     */
    val listDirectoryTool by lazy {
        Tool(
            name = "list_directory",
            description = "List files and directories in a specified path. Returns information about each item including name, type, size, and last modified time.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the directory to list, e.g., /sdcard/Documents")
                        })
                        put("recursive", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, list files recursively (default: false)")
                        })
                        put("includeHidden", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, include hidden files (starting with .) (default: false)")
                        })
                        put("returnMode", buildJsonObject {
                            put("type", "string")
                            put("description", "Response detail level: 'minimal' (name + type only), 'normal' (full details, default)")
                        })
                    },
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val recursive = args.jsonObject["recursive"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val includeHidden = args.jsonObject["includeHidden"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val returnMode = args.jsonObject["returnMode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "normal"

                buildJsonObject {
                    if (path.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Path is required"))
                    } else {
                        try {
                            val dir = File(path)
                            if (!dir.exists()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Directory not found: $path"))
                            } else if (!dir.isDirectory) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Path is not a directory: $path"))
                            } else if (!dir.canRead()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Cannot read directory (permission denied): $path. ${getPermissionHint()}"))
                                put("needPermission", JsonPrimitive(true))
                            } else {
                                fun listFiles(directory: File, depth: Int = 0): List<Map<String, Any>> {
                                    val result = mutableListOf<Map<String, Any>>()
                                    val files = directory.listFiles() ?: return result

                                    for (file in files.sortedBy { it.name }) {
                                        if (!includeHidden && file.name.startsWith(".")) continue

                                        val item = mutableMapOf<String, Any>(
                                            "name" to file.name,
                                            "path" to file.absolutePath,
                                            "type" to if (file.isDirectory) "directory" else "file",
                                            "size" to file.length(),
                                            "lastModified" to file.lastModified(),
                                            "canRead" to file.canRead(),
                                            "canWrite" to file.canWrite(),
                                            "depth" to depth
                                        )
                                        result.add(item)

                                        if (recursive && file.isDirectory && file.canRead()) {
                                            result.addAll(listFiles(file, depth + 1))
                                        }
                                    }
                                    return result
                                }

                                val items = listFiles(dir)
                                put("success", JsonPrimitive(true))

                                if (returnMode != "minimal") {
                                    put("path", JsonPrimitive(dir.absolutePath))
                                }

                                put("count", JsonPrimitive(items.size))
                                put("items", buildJsonArray {
                                    items.forEach { item ->
                                        add(buildJsonObject {
                                            put("name", JsonPrimitive(item["name"] as String))
                                            put("type", JsonPrimitive(item["type"] as String))

                                            if (returnMode != "minimal") {
                                                // Normal 模式:包含详细信息
                                                put("path", JsonPrimitive(item["path"] as String))
                                                put("size", JsonPrimitive(item["size"] as Long))
                                                put("lastModified", JsonPrimitive(item["lastModified"] as Long))
                                                put("canRead", JsonPrimitive(item["canRead"] as Boolean))
                                                put("canWrite", JsonPrimitive(item["canWrite"] as Boolean))
                                                put("depth", JsonPrimitive(item["depth"] as Int))
                                            }
                                        })
                                    }
                                })
                            }
                        } catch (e: Exception) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Failed to list directory: ${e.message}"))
                        }
                    }
                }
            }
        )
    }

    /**
     * 文件/目录信息工具
     */
    val fileInfoTool by lazy {
        Tool(
            name = "file_info",
            description = "Get detailed information about a file or directory, including size, permissions, and timestamps.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the file or directory")
                        })
                        put("returnMode", buildJsonObject {
                            put("type", "string")
                            put("description", "Response detail level: 'minimal' (name, type, size only), 'normal' (all details, default)")
                        })
                    },
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val returnMode = args.jsonObject["returnMode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "normal"

                buildJsonObject {
                    if (path.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Path is required"))
                    } else {
                        try {
                            val file = File(path)
                            if (!file.exists()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("File or directory not found: $path"))
                            } else {
                                put("success", JsonPrimitive(true))
                                put("name", JsonPrimitive(file.name))
                                put("type", JsonPrimitive(if (file.isDirectory) "directory" else "file"))
                                put("size", JsonPrimitive(file.length()))

                                if (returnMode != "minimal") {
                                    // Normal 模式:返回完整信息
                                    put("path", JsonPrimitive(file.absolutePath))
                                    put("canonicalPath", JsonPrimitive(file.canonicalPath))
                                    put("lastModified", JsonPrimitive(file.lastModified()))
                                    put("canRead", JsonPrimitive(file.canRead()))
                                    put("canWrite", JsonPrimitive(file.canWrite()))
                                    put("canExecute", JsonPrimitive(file.canExecute()))
                                    put("isHidden", JsonPrimitive(file.isHidden))
                                    put("parent", JsonPrimitive(file.parent ?: ""))

                                    if (file.isFile) {
                                        val extension = file.extension
                                        put("extension", JsonPrimitive(extension))
                                        put("nameWithoutExtension", JsonPrimitive(file.nameWithoutExtension))
                                    }

                                    if (file.isDirectory) {
                                        val children = file.listFiles()
                                        put("childCount", JsonPrimitive(children?.size ?: 0))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Failed to get file info: ${e.message}"))
                        }
                    }
                }
            }
        )
    }

    /**
     * 文件删除工具
     */
    val fileDeleteTool by lazy {
        Tool(
            name = "delete_file",
            description = "Delete a file or empty directory from the device. For safety, this tool cannot delete non-empty directories by default.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the file or directory to delete")
                        })
                        put("recursive", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, delete directory and all its contents recursively (default: false, use with caution!)")
                        })
                        put("returnMode", buildJsonObject {
                            put("type", "string")
                            put("description", "Response detail level: 'minimal' (success only), 'normal' (success + message, default)")
                        })
                    },
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val recursive = args.jsonObject["recursive"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val returnMode = args.jsonObject["returnMode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "normal"

                buildJsonObject {
                    if (path.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Path is required"))
                    } else {
                        try {
                            val file = File(path)
                            if (!file.exists()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("File or directory not found: $path"))
                            } else if (!file.canWrite()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Cannot delete (permission denied): $path"))
                            } else {
                                val deleted = if (recursive && file.isDirectory) {
                                    file.deleteRecursively()
                                } else {
                                    file.delete()
                                }

                                if (deleted) {
                                    put("success", JsonPrimitive(true))
                                    if (returnMode != "minimal") {
                                        put("message", JsonPrimitive("Successfully deleted: $path"))
                                    }
                                } else {
                                    put("success", JsonPrimitive(false))
                                    put("error", JsonPrimitive("Failed to delete: $path (directory may not be empty, use recursive=true to delete non-empty directories)"))
                                }
                            }
                        } catch (e: Exception) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Failed to delete: ${e.message}"))
                        }
                    }
                }
            }
        )
    }

    /**
     * 文件/目录移动/重命名工具
     */
    val fileMoveRenameTool by lazy {
        Tool(
            name = "move_file",
            description = "Move or rename a file or directory. Can be used to move files between directories or simply rename them.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("source", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the source file or directory")
                        })
                        put("destination", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the destination")
                        })
                        put("overwrite", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, overwrite the destination if it exists (default: false)")
                        })
                        put("returnMode", buildJsonObject {
                            put("type", "string")
                            put("description", "Response detail level: 'minimal' (success only), 'normal' (success + paths, default)")
                        })
                    },
                    required = listOf("source", "destination")
                )
            },
            execute = { args ->
                val source = args.jsonObject["source"]?.jsonPrimitive?.contentOrNull
                val destination = args.jsonObject["destination"]?.jsonPrimitive?.contentOrNull
                val overwrite = args.jsonObject["overwrite"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val returnMode = args.jsonObject["returnMode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "normal"

                buildJsonObject {
                    if (source.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Source path is required"))
                    } else if (destination.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Destination path is required"))
                    } else {
                        try {
                            val sourceFile = File(source)
                            val destFile = File(destination)

                            if (!sourceFile.exists()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Source not found: $source"))
                            } else if (destFile.exists() && !overwrite) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Destination already exists: $destination (use overwrite=true to replace)"))
                            } else {
                                // 创建目标目录
                                destFile.parentFile?.mkdirs()

                                // 如果目标存在且允许覆盖，先删除
                                if (destFile.exists() && overwrite) {
                                    if (destFile.isDirectory) {
                                        destFile.deleteRecursively()
                                    } else {
                                        destFile.delete()
                                    }
                                }

                                val success = sourceFile.renameTo(destFile)
                                if (success) {
                                    put("success", JsonPrimitive(true))
                                    if (returnMode != "minimal") {
                                        put("source", JsonPrimitive(source))
                                        put("destination", JsonPrimitive(destFile.absolutePath))
                                    }
                                } else {
                                    put("success", JsonPrimitive(false))
                                    put("error", JsonPrimitive("Failed to move/rename file"))
                                }
                            }
                        } catch (e: Exception) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Failed to move/rename: ${e.message}"))
                        }
                    }
                }
            }
        )
    }

    /**
     * 创建目录工具
     */
    val createDirectoryTool by lazy {
        Tool(
            name = "create_directory",
            description = "Create a new directory. Can create nested directories if they don't exist.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the directory to create")
                        })
                        put("returnMode", buildJsonObject {
                            put("type", "string")
                            put("description", "Response detail level: 'minimal' (directory name only), 'normal' (full path + message, default)")
                        })
                    },
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val returnMode = args.jsonObject["returnMode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "normal"

                buildJsonObject {
                    if (path.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Path is required"))
                    } else {
                        try {
                            val dir = File(path)
                            if (dir.exists()) {
                                if (dir.isDirectory) {
                                    put("success", JsonPrimitive(true))
                                    if (returnMode == "minimal") {
                                        put("path", JsonPrimitive(dir.name))
                                    } else {
                                        put("message", JsonPrimitive("Directory already exists: $path"))
                                        put("path", JsonPrimitive(dir.absolutePath))
                                    }
                                } else {
                                    put("success", JsonPrimitive(false))
                                    put("error", JsonPrimitive("Path exists but is not a directory: $path"))
                                }
                            } else {
                                val created = dir.mkdirs()
                                if (created) {
                                    put("success", JsonPrimitive(true))
                                    if (returnMode == "minimal") {
                                        put("path", JsonPrimitive(dir.name))
                                    } else {
                                        put("message", JsonPrimitive("Directory created: $path"))
                                        put("path", JsonPrimitive(dir.absolutePath))
                                    }
                                } else {
                                    put("success", JsonPrimitive(false))
                                    put("error", JsonPrimitive("Failed to create directory: $path"))
                                }
                            }
                        } catch (e: Exception) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Failed to create directory: ${e.message}"))
                        }
                    }
                }
            }
        )
    }

    /**
     * 编辑文件行工具 - 精准修改文件的特定行
     */
    val editFileLinesTool by lazy {
        Tool(
            name = "edit_file_lines",
            description = "Precisely edit specific lines in a file. Supports replacing, inserting, or deleting lines by line number or pattern matching. More efficient than rewriting the entire file.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the file to edit")
                        })
                        put("operation", buildJsonObject {
                            put("type", "string")
                            put("description", "The operation to perform: 'replace' (replace lines), 'insert' (insert new lines), 'delete' (delete lines)")
                        })
                        put("lineNumber", buildJsonObject {
                            put("type", "integer")
                            put("description", "The line number to operate on (1-indexed). For replace/delete, can be used with lineEnd to specify a range")
                        })
                        put("lineEnd", buildJsonObject {
                            put("type", "integer")
                            put("description", "For range operations: the ending line number (1-indexed, inclusive). If not specified, only lineNumber is affected")
                        })
                        put("pattern", buildJsonObject {
                            put("type", "string")
                            put("description", "Instead of line number, use a regex pattern to match lines. All matching lines will be affected")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "For replace/insert: the new content. For multiple lines, use \\n to separate them")
                        })
                        put("insertPosition", buildJsonObject {
                            put("type", "string")
                            put("description", "For insert operation: 'before' or 'after' the specified line (default: before)")
                        })
                        put("encoding", buildJsonObject {
                            put("type", "string")
                            put("description", "Character encoding (default: UTF-8). Options: UTF-8, GBK, ISO-8859-1")
                        })
                        put("returnMode", buildJsonObject {
                            put("type", "string")
                            put("description", "Response detail level: 'minimal' (only essential info), 'normal' (all details, default)")
                        })
                    },
                    required = listOf("path", "operation")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val operation = args.jsonObject["operation"]?.jsonPrimitive?.contentOrNull?.lowercase()
                val lineNumber = args.jsonObject["lineNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val lineEnd = args.jsonObject["lineEnd"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val pattern = args.jsonObject["pattern"]?.jsonPrimitive?.contentOrNull
                val content = args.jsonObject["content"]?.jsonPrimitive?.contentOrNull
                val insertPosition = args.jsonObject["insertPosition"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "before"
                val encoding = args.jsonObject["encoding"]?.jsonPrimitive?.contentOrNull ?: "UTF-8"
                val returnMode = args.jsonObject["returnMode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "normal"

                buildJsonObject {
                    if (path.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Path is required"))
                    } else if (operation.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Operation is required (replace, insert, or delete)"))
                    } else if (operation !in listOf("replace", "insert", "delete")) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Invalid operation: $operation. Must be 'replace', 'insert', or 'delete'"))
                    } else if (lineNumber == null && pattern.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Either lineNumber or pattern must be specified"))
                    } else if (operation in listOf("replace", "insert") && content == null) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Content is required for $operation operation"))
                    } else {
                        try {
                            val file = File(path)
                            if (!file.exists()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("File not found: $path"))
                            } else if (!file.isFile) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Path is not a file: $path"))
                            } else if (!file.canRead()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Cannot read file (permission denied): $path"))
                                put("needPermission", JsonPrimitive(true))
                            } else if (!file.canWrite()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Cannot write to file (permission denied): $path"))
                                put("needPermission", JsonPrimitive(true))
                            } else {
                                // 读取文件所有行
                                val lines = file.readLines(charset(encoding)).toMutableList()
                                val totalLines = lines.size
                                val newContentLines = content?.split("\\n") ?: emptyList()
                                var linesAffected = 0

                                // 根据操作类型修改行
                                when (operation) {
                                    "replace" -> {
                                        if (lineNumber != null) {
                                            // 按行号替换
                                            if (lineNumber !in 1..totalLines) {
                                                put("success", JsonPrimitive(false))
                                                put("error", JsonPrimitive("Line number $lineNumber out of range (1-$totalLines)"))
                                                return@buildJsonObject
                                            }
                                            val endLine = lineEnd?.coerceIn(lineNumber, totalLines) ?: lineNumber

                                            // 删除旧行并插入新内容
                                            val startIndex = lineNumber - 1
                                            val deleteCount = endLine - lineNumber + 1
                                            repeat(deleteCount) { lines.removeAt(startIndex) }
                                            lines.addAll(startIndex, newContentLines)
                                            linesAffected = deleteCount
                                        } else {
                                            // 按模式匹配替换
                                            val regex = Regex(pattern!!)
                                            val matchedIndices = lines.indices.filter { regex.containsMatchIn(lines[it]) }.reversed()

                                            for (index in matchedIndices) {
                                                lines[index] = newContentLines.joinToString("\n")
                                                linesAffected++
                                            }
                                        }
                                    }
                                    "insert" -> {
                                        if (lineNumber != null) {
                                            // 按行号插入
                                            if (lineNumber < 1 || lineNumber > totalLines + 1) {
                                                put("success", JsonPrimitive(false))
                                                put("error", JsonPrimitive("Line number $lineNumber out of range for insert (1-${totalLines + 1})"))
                                                return@buildJsonObject
                                            }
                                            val insertIndex = if (insertPosition == "after") lineNumber else lineNumber - 1
                                            lines.addAll(insertIndex, newContentLines)
                                            linesAffected = newContentLines.size
                                        } else {
                                            // 按模式匹配插入
                                            val regex = Regex(pattern!!)
                                            val matchedIndices = lines.indices.filter { regex.containsMatchIn(lines[it]) }

                                            // 从后往前插入,避免索引变化
                                            for (index in matchedIndices.reversed()) {
                                                val insertIndex = if (insertPosition == "after") index + 1 else index
                                                lines.addAll(insertIndex, newContentLines)
                                                linesAffected++
                                            }
                                        }
                                    }
                                    "delete" -> {
                                        if (lineNumber != null) {
                                            // 按行号删除
                                            if (lineNumber !in 1..totalLines) {
                                                put("success", JsonPrimitive(false))
                                                put("error", JsonPrimitive("Line number $lineNumber out of range (1-$totalLines)"))
                                                return@buildJsonObject
                                            }
                                            val endLine = lineEnd?.coerceIn(lineNumber, totalLines) ?: lineNumber
                                            val deleteCount = endLine - lineNumber + 1
                                            repeat(deleteCount) { lines.removeAt(lineNumber - 1) }
                                            linesAffected = deleteCount
                                        } else {
                                            // 按模式匹配删除
                                            val regex = Regex(pattern!!)
                                            val toRemove = lines.filter { regex.containsMatchIn(it) }
                                            lines.removeAll(toRemove.toSet())
                                            linesAffected = toRemove.size
                                        }
                                    }
                                }

                                // 写回文件
                                file.writeText(lines.joinToString("\n"), charset(encoding))

                                // 构建响应
                                put("success", JsonPrimitive(true))

                                if (returnMode == "minimal") {
                                    // Minimal 模式:只返回基本信息
                                    put("path", JsonPrimitive(file.name))
                                    put("linesAffected", JsonPrimitive(linesAffected))
                                } else {
                                    // Normal 模式:返回详细信息
                                    put("path", JsonPrimitive(file.absolutePath))
                                    put("operation", JsonPrimitive(operation))
                                    put("linesAffected", JsonPrimitive(linesAffected))
                                    put("totalLinesAfter", JsonPrimitive(lines.size))
                                    put("totalLinesBefore", JsonPrimitive(totalLines))
                                    put("size", JsonPrimitive(file.length()))
                                }
                            }
                        } catch (e: Exception) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Failed to edit file: ${e.message}"))
                        }
                    }
                }
            }
        )
    }

    /**
     * Web Fetch 工具 - 获取 URL 内容
     */
    val webFetchTool by lazy {
        Tool(
            name = "web_fetch",
            description = "Fetch content from a URL. Returns the HTML content, status code, and response headers. Useful for retrieving web page content, API responses, or checking URL availability.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("url", buildJsonObject {
                            put("type", "string")
                            put("description", "The URL to fetch, must start with http:// or https://")
                        })
                        put("method", buildJsonObject {
                            put("type", "string")
                            put("description", "HTTP method to use (GET or POST, default: GET)")
                        })
                        put("headers", buildJsonObject {
                            put("type", "string")
                            put("description", "Custom headers in JSON format, e.g., {\"Authorization\": \"Bearer token\"}")
                        })
                        put("body", buildJsonObject {
                            put("type", "string")
                            put("description", "Request body for POST requests")
                        })
                        put("timeout", buildJsonObject {
                            put("type", "integer")
                            put("description", "Connection timeout in milliseconds (default: 10000)")
                        })
                        put("extractText", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, extract plain text from HTML by removing tags (default: false)")
                        })
                        put("maxLength", buildJsonObject {
                            put("type", "integer")
                            put("description", "Maximum content length to return in characters (default: 50000, max: 100000)")
                        })
                    },
                    required = listOf("url")
                )
            },
            execute = { args ->
                val urlString = args.jsonObject["url"]?.jsonPrimitive?.contentOrNull
                val method = args.jsonObject["method"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "GET"
                val headersJson = args.jsonObject["headers"]?.jsonPrimitive?.contentOrNull
                val body = args.jsonObject["body"]?.jsonPrimitive?.contentOrNull
                val timeout = args.jsonObject["timeout"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10000
                val extractText = args.jsonObject["extractText"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val maxLength = (args.jsonObject["maxLength"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 50000).coerceIn(1, 100000)

                buildJsonObject {
                    if (urlString.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("URL is required"))
                    } else if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("URL must start with http:// or https://"))
                    } else if (method != "GET" && method != "POST") {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Only GET and POST methods are supported"))
                    } else {
                        try {
                            val url = URL(urlString)
                            val connection = url.openConnection() as HttpURLConnection

                            connection.requestMethod = method
                            connection.connectTimeout = timeout
                            connection.readTimeout = timeout
                            connection.setRequestProperty("User-Agent", "RikkaHub/1.0 (Android)")
                            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

                            // 解析并设置自定义 headers
                            if (!headersJson.isNullOrBlank()) {
                                try {
                                    val customHeaders = kotlinx.serialization.json.Json.parseToJsonElement(headersJson).jsonObject
                                    customHeaders.forEach { (key, value) ->
                                        connection.setRequestProperty(key, value.jsonPrimitive.contentOrNull ?: "")
                                    }
                                } catch (_: Exception) {
                                    // 忽略无效的 headers JSON
                                }
                            }

                            // 处理 POST 请求体
                            if (method == "POST" && !body.isNullOrBlank()) {
                                connection.doOutput = true
                                connection.outputStream.use { os ->
                                    os.write(body.toByteArray(Charsets.UTF_8))
                                }
                            }

                            val responseCode = connection.responseCode
                            val responseMessage = connection.responseMessage
                            val contentType = connection.contentType ?: "unknown"
                            val contentLength = connection.contentLength

                            // 获取响应头
                            val responseHeaders = buildJsonObject {
                                connection.headerFields.forEach { (key, values) ->
                                    if (key != null && values.isNotEmpty()) {
                                        put(key, JsonPrimitive(values.joinToString(", ")))
                                    }
                                }
                            }

                            // 读取响应内容
                            val inputStream = if (responseCode >= 400) {
                                connection.errorStream
                            } else {
                                connection.inputStream
                            }

                            var content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

                            // 提取纯文本（移除 HTML 标签）
                            if (extractText && content.isNotEmpty()) {
                                content = content
                                    .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                                    .replace(Regex("<[^>]+>"), " ")
                                    .replace(Regex("&nbsp;"), " ")
                                    .replace(Regex("&amp;"), "&")
                                    .replace(Regex("&lt;"), "<")
                                    .replace(Regex("&gt;"), ">")
                                    .replace(Regex("&quot;"), "\"")
                                    .replace(Regex("&#\\d+;")) { match ->
                                        try {
                                            val code = match.value.substring(2, match.value.length - 1).toInt()
                                            code.toChar().toString()
                                        } catch (_: Exception) {
                                            match.value
                                        }
                                    }
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                            }

                            // 截断过长的内容
                            val truncated = content.length > maxLength
                            if (truncated) {
                                content = content.take(maxLength) + "...[truncated]"
                            }

                            connection.disconnect()

                            put("success", JsonPrimitive(true))
                            put("url", JsonPrimitive(urlString))
                            put("statusCode", JsonPrimitive(responseCode))
                            put("statusMessage", JsonPrimitive(responseMessage ?: ""))
                            put("contentType", JsonPrimitive(contentType))
                            put("contentLength", JsonPrimitive(contentLength))
                            put("headers", responseHeaders)
                            put("content", JsonPrimitive(content))
                            put("truncated", JsonPrimitive(truncated))
                            put("extractedText", JsonPrimitive(extractText))

                        } catch (e: java.net.SocketTimeoutException) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Connection timeout: ${e.message}"))
                        } catch (e: java.net.UnknownHostException) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Unknown host: ${e.message}"))
                        } catch (e: java.io.IOException) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("IO error: ${e.message}"))
                        } catch (e: Exception) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Failed to fetch URL: ${e.message}"))
                        }
                    }
                }
            }
        )
    }

    /**
     * 文件复制工具
     */
    val fileCopyTool by lazy {
        Tool(
            name = "copy_file",
            description = "Copy a file or directory to a new location. Can recursively copy entire directory trees.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("source", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the source file or directory to copy")
                        })
                        put("destination", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the destination")
                        })
                        put("overwrite", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, overwrite the destination if it exists (default: false)")
                        })
                        put("recursive", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, copy directories recursively (default: true)")
                        })
                        put("returnMode", buildJsonObject {
                            put("type", "string")
                            put("description", "Response detail level: 'minimal' (success only), 'normal' (success + paths + size, default)")
                        })
                    },
                    required = listOf("source", "destination")
                )
            },
            execute = { args ->
                val source = args.jsonObject["source"]?.jsonPrimitive?.contentOrNull
                val destination = args.jsonObject["destination"]?.jsonPrimitive?.contentOrNull
                val overwrite = args.jsonObject["overwrite"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val recursive = args.jsonObject["recursive"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
                val returnMode = args.jsonObject["returnMode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "normal"

                buildJsonObject {
                    if (source.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Source path is required"))
                    } else if (destination.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Destination path is required"))
                    } else {
                        try {
                            val sourceFile = File(source)
                            val destFile = File(destination)

                            if (!sourceFile.exists()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Source not found: $source"))
                            } else if (!sourceFile.canRead()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Cannot read source (permission denied): $source"))
                                put("needPermission", JsonPrimitive(true))
                            } else if (destFile.exists() && !overwrite) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Destination already exists: $destination (use overwrite=true to replace)"))
                            } else {
                                // 创建目标目录
                                destFile.parentFile?.mkdirs()

                                // 如果目标存在且允许覆盖，先删除
                                if (destFile.exists() && overwrite) {
                                    if (destFile.isDirectory) {
                                        destFile.deleteRecursively()
                                    } else {
                                        destFile.delete()
                                    }
                                }

                                // 执行复制
                                if (sourceFile.isDirectory) {
                                    if (recursive) {
                                        sourceFile.copyRecursively(destFile, overwrite)
                                    } else {
                                        put("success", JsonPrimitive(false))
                                        put("error", JsonPrimitive("Source is a directory, set recursive=true to copy it"))
                                        return@buildJsonObject
                                    }
                                } else {
                                    sourceFile.copyTo(destFile, overwrite)
                                }

                                put("success", JsonPrimitive(true))
                                if (returnMode == "minimal") {
                                    // Minimal 模式
                                    put("path", JsonPrimitive(destFile.name))
                                } else {
                                    // Normal 模式
                                    put("source", JsonPrimitive(sourceFile.absolutePath))
                                    put("destination", JsonPrimitive(destFile.absolutePath))
                                    put("size", JsonPrimitive(destFile.length()))
                                    put("isDirectory", JsonPrimitive(destFile.isDirectory))
                                }
                            }
                        } catch (e: Exception) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Failed to copy: ${e.message}"))
                        }
                    }
                }
            }
        )
    }

    /**
     * 文件搜索工具 - 在文件内容中搜索关键词
     */
    val fileSearchTool by lazy {
        Tool(
            name = "search_files",
            description = "Search for text patterns in files within a directory. Can search file content using keywords or regex patterns. Returns matching files with line numbers and context.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute path of the directory or file to search in")
                        })
                        put("pattern", buildJsonObject {
                            put("type", "string")
                            put("description", "The text pattern or regex to search for")
                        })
                        put("useRegex", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, treat pattern as a regular expression (default: false)")
                        })
                        put("caseSensitive", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, search is case-sensitive (default: false)")
                        })
                        put("recursive", buildJsonObject {
                            put("type", "boolean")
                            put("description", "If true, search in subdirectories recursively (default: true)")
                        })
                        put("fileExtensions", buildJsonObject {
                            put("type", "string")
                            put("description", "Comma-separated list of file extensions to search (e.g., 'txt,log,md'). If empty, search all files")
                        })
                        put("maxResults", buildJsonObject {
                            put("type", "integer")
                            put("description", "Maximum number of matching files to return (default: 50, max: 200)")
                        })
                        put("contextLines", buildJsonObject {
                            put("type", "integer")
                            put("description", "Number of lines to show before and after each match for context (default: 1, max: 5)")
                        })
                        put("returnMode", buildJsonObject {
                            put("type", "string")
                            put("description", "Response detail level: 'minimal' (file paths only), 'normal' (paths + match count), 'detailed' (paths + matched lines with context, default)")
                        })
                    },
                    required = listOf("path", "pattern")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val pattern = args.jsonObject["pattern"]?.jsonPrimitive?.contentOrNull
                val useRegex = args.jsonObject["useRegex"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val caseSensitive = args.jsonObject["caseSensitive"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val recursive = args.jsonObject["recursive"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
                val fileExtensions = args.jsonObject["fileExtensions"]?.jsonPrimitive?.contentOrNull
                val maxResults = (args.jsonObject["maxResults"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 50).coerceIn(1, 200)
                val contextLines = (args.jsonObject["contextLines"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1).coerceIn(0, 5)
                val returnMode = args.jsonObject["returnMode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "detailed"

                buildJsonObject {
                    if (path.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Path is required"))
                    } else if (pattern.isNullOrBlank()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Pattern is required"))
                    } else {
                        try {
                            val searchRoot = File(path)
                            if (!searchRoot.exists()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Path not found: $path"))
                            } else if (!searchRoot.canRead()) {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Cannot read path (permission denied): $path. ${getPermissionHint()}"))
                                put("needPermission", JsonPrimitive(true))
                            } else {
                                // 准备搜索模式
                                val searchRegex = if (useRegex) {
                                    if (caseSensitive) Regex(pattern) else Regex(pattern, RegexOption.IGNORE_CASE)
                                } else {
                                    if (caseSensitive) Regex(Regex.escape(pattern)) else Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
                                }

                                // 解析文件扩展名过滤
                                val extensions = fileExtensions?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

                                // 收集要搜索的文件
                                val filesToSearch = mutableListOf<File>()
                                fun collectFiles(dir: File) {
                                    val files = dir.listFiles() ?: return
                                    for (file in files) {
                                        if (file.isFile && file.canRead()) {
                                            if (extensions.isEmpty() || extensions.any { file.extension.equals(it, ignoreCase = true) }) {
                                                filesToSearch.add(file)
                                            }
                                        } else if (file.isDirectory && recursive && file.canRead()) {
                                            collectFiles(file)
                                        }
                                    }
                                }

                                if (searchRoot.isFile) {
                                    filesToSearch.add(searchRoot)
                                } else {
                                    collectFiles(searchRoot)
                                }

                                // 搜索文件内容
                                val results = mutableListOf<Map<String, Any>>()
                                for (file in filesToSearch) {
                                    if (results.size >= maxResults) break

                                    try {
                                        val lines = file.readLines()
                                        val matches = mutableListOf<Map<String, Any>>()

                                        lines.forEachIndexed { index, line ->
                                            if (searchRegex.containsMatchIn(line)) {
                                                val lineNumber = index + 1
                                                val contextStart = (index - contextLines).coerceAtLeast(0)
                                                val contextEnd = (index + contextLines).coerceAtMost(lines.size - 1)

                                                val contextMap = mutableMapOf<String, Any>(
                                                    "lineNumber" to lineNumber,
                                                    "line" to line
                                                )

                                                if (returnMode == "detailed" && contextLines > 0) {
                                                    val contextBefore = mutableListOf<String>()
                                                    val contextAfter = mutableListOf<String>()

                                                    for (i in contextStart until index) {
                                                        contextBefore.add(lines[i])
                                                    }
                                                    for (i in (index + 1)..contextEnd) {
                                                        contextAfter.add(lines[i])
                                                    }

                                                    contextMap["before"] = contextBefore
                                                    contextMap["after"] = contextAfter
                                                }

                                                matches.add(contextMap)
                                            }
                                        }

                                        if (matches.isNotEmpty()) {
                                            results.add(mapOf(
                                                "path" to file.absolutePath,
                                                "name" to file.name,
                                                "matchCount" to matches.size,
                                                "matches" to matches
                                            ))
                                        }
                                    } catch (_: Exception) {
                                        // 跳过无法读取的文件
                                        continue
                                    }
                                }

                                put("success", JsonPrimitive(true))
                                put("pattern", JsonPrimitive(pattern))
                                put("filesSearched", JsonPrimitive(filesToSearch.size))
                                put("filesMatched", JsonPrimitive(results.size))

                                put("results", buildJsonArray {
                                    results.forEach { result ->
                                        add(buildJsonObject {
                                            when (returnMode) {
                                                "minimal" -> {
                                                    // Minimal: 只返回文件路径
                                                    put("path", JsonPrimitive(result["path"] as String))
                                                }
                                                "normal" -> {
                                                    // Normal: 路径 + 匹配数
                                                    put("path", JsonPrimitive(result["path"] as String))
                                                    put("name", JsonPrimitive(result["name"] as String))
                                                    put("matchCount", JsonPrimitive(result["matchCount"] as Int))
                                                }
                                                else -> {
                                                    // Detailed: 完整信息包括匹配行
                                                    put("path", JsonPrimitive(result["path"] as String))
                                                    put("name", JsonPrimitive(result["name"] as String))
                                                    put("matchCount", JsonPrimitive(result["matchCount"] as Int))

                                                    @Suppress("UNCHECKED_CAST")
                                                    val matches = result["matches"] as List<Map<String, Any>>
                                                    put("matches", buildJsonArray {
                                                        matches.forEach { match ->
                                                            add(buildJsonObject {
                                                                put("lineNumber", JsonPrimitive(match["lineNumber"] as Int))
                                                                put("line", JsonPrimitive(match["line"] as String))

                                                                if (match.containsKey("before")) {
                                                                    @Suppress("UNCHECKED_CAST")
                                                                    val before = match["before"] as List<String>
                                                                    put("before", buildJsonArray {
                                                                        before.forEach { add(JsonPrimitive(it)) }
                                                                    })
                                                                }

                                                                if (match.containsKey("after")) {
                                                                    @Suppress("UNCHECKED_CAST")
                                                                    val after = match["after"] as List<String>
                                                                    put("after", buildJsonArray {
                                                                        after.forEach { add(JsonPrimitive(it)) }
                                                                    })
                                                                }
                                                            })
                                                        }
                                                    })
                                                }
                                            }
                                        })
                                    }
                                })
                            }
                        } catch (e: Exception) {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Failed to search files: ${e.message}"))
                        }
                    }
                }
            }
        )
    }

    /**
     * 系统信息工具 - 获取设备和系统信息
     */
    val systemInfoTool by lazy {
        Tool(
            name = "get_system_info",
            description = "Get device and system information including device model, Android version, storage space, memory usage, and app information.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("infoType", buildJsonObject {
                            put("type", "string")
                            put("description", "Type of information to retrieve: 'all' (default), 'device' (device info only), 'storage' (storage info only), 'memory' (memory info only), 'app' (app info only)")
                        })
                        put("returnMode", buildJsonObject {
                            put("type", "string")
                            put("description", "Response detail level: 'minimal' (essential info only), 'normal' (standard details, default), 'detailed' (all available information)")
                        })
                    }
                )
            },
            execute = { args ->
                val infoType = args.jsonObject["infoType"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "all"
                val returnMode = args.jsonObject["returnMode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "normal"

                buildJsonObject {
                    try {
                        put("success", JsonPrimitive(true))

                        // 设备信息
                        if (infoType == "all" || infoType == "device") {
                            put("device", buildJsonObject {
                                put("manufacturer", JsonPrimitive(Build.MANUFACTURER))
                                put("model", JsonPrimitive(Build.MODEL))
                                put("brand", JsonPrimitive(Build.BRAND))

                                if (returnMode != "minimal") {
                                    put("device", JsonPrimitive(Build.DEVICE))
                                    put("product", JsonPrimitive(Build.PRODUCT))
                                    put("hardware", JsonPrimitive(Build.HARDWARE))
                                    put("board", JsonPrimitive(Build.BOARD))
                                }

                                if (returnMode == "detailed") {
                                    put("fingerprint", JsonPrimitive(Build.FINGERPRINT))
                                    put("id", JsonPrimitive(Build.ID))
                                    put("display", JsonPrimitive(Build.DISPLAY))
                                    put("tags", JsonPrimitive(Build.TAGS))
                                    put("type", JsonPrimitive(Build.TYPE))
                                    put("user", JsonPrimitive(Build.USER))
                                    put("host", JsonPrimitive(Build.HOST))
                                    put("time", JsonPrimitive(Build.TIME))
                                }
                            })

                            put("android", buildJsonObject {
                                put("version", JsonPrimitive(Build.VERSION.RELEASE))
                                put("sdkInt", JsonPrimitive(Build.VERSION.SDK_INT))

                                if (returnMode != "minimal") {
                                    put("codename", JsonPrimitive(Build.VERSION.CODENAME))
                                    put("incremental", JsonPrimitive(Build.VERSION.INCREMENTAL))
                                }

                                if (returnMode == "detailed") {
                                    put("baseOs", JsonPrimitive(Build.VERSION.BASE_OS))
                                    put("securityPatch", JsonPrimitive(Build.VERSION.SECURITY_PATCH))
                                    put("previewSdkInt", JsonPrimitive(Build.VERSION.PREVIEW_SDK_INT))
                                }
                            })

                            if (returnMode != "minimal") {
                                put("abi", buildJsonObject {
                                    put("supportedAbis", buildJsonArray {
                                        Build.SUPPORTED_ABIS.forEach { add(JsonPrimitive(it)) }
                                    })
                                    if (returnMode == "detailed") {
                                        put("supported32BitAbis", buildJsonArray {
                                            Build.SUPPORTED_32_BIT_ABIS.forEach { add(JsonPrimitive(it)) }
                                        })
                                        put("supported64BitAbis", buildJsonArray {
                                            Build.SUPPORTED_64_BIT_ABIS.forEach { add(JsonPrimitive(it)) }
                                        })
                                    }
                                })
                            }
                        }

                        // 存储信息
                        if (infoType == "all" || infoType == "storage") {
                            put("storage", buildJsonObject {
                                // 内部存储
                                val internalPath = Environment.getDataDirectory()
                                val internalStat = StatFs(internalPath.path)
                                val internalBlockSize = internalStat.blockSizeLong
                                val internalTotalBlocks = internalStat.blockCountLong
                                val internalAvailableBlocks = internalStat.availableBlocksLong
                                val internalFreeBlocks = internalStat.freeBlocksLong

                                put("internal", buildJsonObject {
                                    put("totalBytes", JsonPrimitive(internalTotalBlocks * internalBlockSize))
                                    put("availableBytes", JsonPrimitive(internalAvailableBlocks * internalBlockSize))
                                    put("freeBytes", JsonPrimitive(internalFreeBlocks * internalBlockSize))
                                    put("usedBytes", JsonPrimitive((internalTotalBlocks - internalFreeBlocks) * internalBlockSize))

                                    if (returnMode != "minimal") {
                                        val totalGB = (internalTotalBlocks * internalBlockSize) / (1024.0 * 1024.0 * 1024.0)
                                        val availableGB = (internalAvailableBlocks * internalBlockSize) / (1024.0 * 1024.0 * 1024.0)
                                        val usedGB = ((internalTotalBlocks - internalFreeBlocks) * internalBlockSize) / (1024.0 * 1024.0 * 1024.0)
                                        val usedPercent = if (internalTotalBlocks > 0) ((internalTotalBlocks - internalFreeBlocks) * 100.0 / internalTotalBlocks) else 0.0

                                        put("totalGB", JsonPrimitive("%.2f".format(totalGB)))
                                        put("availableGB", JsonPrimitive("%.2f".format(availableGB)))
                                        put("usedGB", JsonPrimitive("%.2f".format(usedGB)))
                                        put("usedPercent", JsonPrimitive("%.1f".format(usedPercent)))
                                    }
                                })

                                // 外部存储
                                if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                                    val externalPath = Environment.getExternalStorageDirectory()
                                    val externalStat = StatFs(externalPath.path)
                                    val externalBlockSize = externalStat.blockSizeLong
                                    val externalTotalBlocks = externalStat.blockCountLong
                                    val externalAvailableBlocks = externalStat.availableBlocksLong
                                    val externalFreeBlocks = externalStat.freeBlocksLong

                                    put("external", buildJsonObject {
                                        put("totalBytes", JsonPrimitive(externalTotalBlocks * externalBlockSize))
                                        put("availableBytes", JsonPrimitive(externalAvailableBlocks * externalBlockSize))
                                        put("freeBytes", JsonPrimitive(externalFreeBlocks * externalBlockSize))
                                        put("usedBytes", JsonPrimitive((externalTotalBlocks - externalFreeBlocks) * externalBlockSize))

                                        if (returnMode != "minimal") {
                                            val totalGB = (externalTotalBlocks * externalBlockSize) / (1024.0 * 1024.0 * 1024.0)
                                            val availableGB = (externalAvailableBlocks * externalBlockSize) / (1024.0 * 1024.0 * 1024.0)
                                            val usedGB = ((externalTotalBlocks - externalFreeBlocks) * externalBlockSize) / (1024.0 * 1024.0 * 1024.0)
                                            val usedPercent = if (externalTotalBlocks > 0) ((externalTotalBlocks - externalFreeBlocks) * 100.0 / externalTotalBlocks) else 0.0

                                            put("totalGB", JsonPrimitive("%.2f".format(totalGB)))
                                            put("availableGB", JsonPrimitive("%.2f".format(availableGB)))
                                            put("usedGB", JsonPrimitive("%.2f".format(usedGB)))
                                            put("usedPercent", JsonPrimitive("%.1f".format(usedPercent)))
                                        }
                                    })
                                } else {
                                    put("external", buildJsonObject {
                                        put("available", JsonPrimitive(false))
                                        put("state", JsonPrimitive(Environment.getExternalStorageState()))
                                    })
                                }
                            })
                        }

                        // 内存信息
                        if (infoType == "all" || infoType == "memory") {
                            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                            val memoryInfo = ActivityManager.MemoryInfo()
                            activityManager.getMemoryInfo(memoryInfo)

                            put("memory", buildJsonObject {
                                put("totalBytes", JsonPrimitive(memoryInfo.totalMem))
                                put("availableBytes", JsonPrimitive(memoryInfo.availMem))
                                put("usedBytes", JsonPrimitive(memoryInfo.totalMem - memoryInfo.availMem))
                                put("threshold", JsonPrimitive(memoryInfo.threshold))
                                put("lowMemory", JsonPrimitive(memoryInfo.lowMemory))

                                if (returnMode != "minimal") {
                                    val totalMB = memoryInfo.totalMem / (1024.0 * 1024.0)
                                    val availableMB = memoryInfo.availMem / (1024.0 * 1024.0)
                                    val usedMB = (memoryInfo.totalMem - memoryInfo.availMem) / (1024.0 * 1024.0)
                                    val usedPercent = if (memoryInfo.totalMem > 0) ((memoryInfo.totalMem - memoryInfo.availMem) * 100.0 / memoryInfo.totalMem) else 0.0

                                    put("totalMB", JsonPrimitive("%.0f".format(totalMB)))
                                    put("availableMB", JsonPrimitive("%.0f".format(availableMB)))
                                    put("usedMB", JsonPrimitive("%.0f".format(usedMB)))
                                    put("usedPercent", JsonPrimitive("%.1f".format(usedPercent)))
                                }
                            })
                        }

                        // 应用信息
                        if (infoType == "all" || infoType == "app") {
                            val packageManager = context.packageManager
                            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)

                            put("app", buildJsonObject {
                                put("packageName", JsonPrimitive(context.packageName))
                                put("versionName", JsonPrimitive(packageInfo.versionName ?: "unknown"))
                                @Suppress("DEPRECATION")
                                put("versionCode", JsonPrimitive(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()))

                                if (returnMode != "minimal") {
                                    val appInfo = packageInfo.applicationInfo
                                    if (appInfo != null) {
                                        put("appName", JsonPrimitive(packageManager.getApplicationLabel(appInfo).toString()))
                                        put("targetSdk", JsonPrimitive(appInfo.targetSdkVersion))
                                        put("minSdk", JsonPrimitive(appInfo.minSdkVersion))
                                    }
                                }

                                if (returnMode == "detailed") {
                                    put("firstInstallTime", JsonPrimitive(packageInfo.firstInstallTime))
                                    put("lastUpdateTime", JsonPrimitive(packageInfo.lastUpdateTime))

                                    // 应用数据目录大小
                                    val dataDir = context.filesDir
                                    val cacheDir = context.cacheDir

                                    fun getDirSize(dir: File): Long {
                                        var size = 0L
                                        if (dir.exists()) {
                                            dir.walkTopDown().forEach { file ->
                                                if (file.isFile) size += file.length()
                                            }
                                        }
                                        return size
                                    }

                                    val dataDirSize = getDirSize(dataDir.parentFile ?: dataDir)
                                    val cacheDirSize = getDirSize(cacheDir)

                                    put("dataDirectorySize", JsonPrimitive(dataDirSize))
                                    put("cacheDirectorySize", JsonPrimitive(cacheDirSize))
                                    put("dataDirectorySizeMB", JsonPrimitive("%.2f".format(dataDirSize / (1024.0 * 1024.0))))
                                    put("cacheDirectorySizeMB", JsonPrimitive("%.2f".format(cacheDirSize / (1024.0 * 1024.0))))
                                }
                            })
                        }

                    } catch (e: Exception) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Failed to get system info: ${e.message}"))
                    }
                }
            }
        )
    }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
            tools.add(systemInfoTool)
        }
        if (options.contains(LocalToolOption.FileSystem)) {
            tools.add(fileReadTool)
            tools.add(fileWriteTool)
            tools.add(editFileLinesTool)
            tools.add(listDirectoryTool)
            tools.add(fileInfoTool)
            tools.add(fileDeleteTool)
            tools.add(fileMoveRenameTool)
            tools.add(createDirectoryTool)
            tools.add(fileCopyTool)
            tools.add(fileSearchTool)
        }
        if (options.contains(LocalToolOption.WebFetch)) {
            tools.add(webFetchTool)
        }
        return tools
    }
}
