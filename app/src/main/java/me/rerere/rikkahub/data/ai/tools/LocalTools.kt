package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    
    // 允许访问的目录列表（安全限制）
    private val allowedPaths: List<File> by lazy {
        listOf(
            context.filesDir,                                    // 应用内部文件目录
            context.cacheDir,                                    // 应用缓存目录
            context.getExternalFilesDir(null),                   // 应用外部文件目录
            Environment.getExternalStorageDirectory(),           // 外部存储根目录
        ).filterNotNull()
    }
    
    // 检查路径是否在允许的范围内
    private fun isPathAllowed(path: String): Boolean {
        val file = File(path).canonicalFile
        return allowedPaths.any { allowedPath ->
            file.canonicalPath.startsWith(allowedPath.canonicalPath)
        }
    }
    
    // 检查是否有存储权限
    // 注意：这里采用宽松策略，只要有任何一种存储相关权限就返回true
    // 实际的文件访问能力取决于具体的权限和文件位置
    private fun hasStoragePermission(): Boolean {
        return when {
            // Android 11+ (API 30+): 检查是否有完全文件访问权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // 如果有 MANAGE_EXTERNAL_STORAGE 权限，直接返回 true
                if (Environment.isExternalStorageManager()) {
                    return true
                }
                // Android 13+ (API 33+): 检查媒体权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hasImagePermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_MEDIA_IMAGES
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasVideoPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_MEDIA_VIDEO
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasAudioPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_MEDIA_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    // 只要有任何一种媒体权限就允许尝试访问
                    if (hasImagePermission || hasVideoPermission || hasAudioPermission) {
                        return true
                    }
                }
                // Android 11-12: 检查传统存储权限
                val hasReadPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                hasReadPermission
            }
            // Android 10 及以下: 检查传统存储权限
            else -> {
                val hasReadPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                hasReadPermission
            }
        }
    }
    
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
                    },
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val encoding = args.jsonObject["encoding"]?.jsonPrimitive?.contentOrNull ?: "UTF-8"
                
                buildJsonObject {
                    // 首先检查存储权限
                    if (!hasStoragePermission()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("没有文件访问权限。${getPermissionHint()}"))
                        put("needPermission", JsonPrimitive(true))
                    } else if (path.isNullOrBlank()) {
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
                                put("size", JsonPrimitive(file.length()))
                                put("lastModified", JsonPrimitive(file.lastModified()))
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
                
                buildJsonObject {
                    // 首先检查存储权限
                    if (!hasStoragePermission()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("没有文件访问权限。${getPermissionHint()}"))
                        put("needPermission", JsonPrimitive(true))
                    } else if (path.isNullOrBlank()) {
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
                            put("path", JsonPrimitive(file.absolutePath))
                            put("size", JsonPrimitive(file.length()))
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
                    },
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val recursive = args.jsonObject["recursive"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val includeHidden = args.jsonObject["includeHidden"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                
                buildJsonObject {
                    // 首先检查存储权限
                    if (!hasStoragePermission()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("没有文件访问权限。${getPermissionHint()}"))
                        put("needPermission", JsonPrimitive(true))
                    } else if (path.isNullOrBlank()) {
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
                                put("path", JsonPrimitive(dir.absolutePath))
                                put("count", JsonPrimitive(items.size))
                                put("items", buildJsonArray {
                                    items.forEach { item ->
                                        add(buildJsonObject {
                                            put("name", JsonPrimitive(item["name"] as String))
                                            put("path", JsonPrimitive(item["path"] as String))
                                            put("type", JsonPrimitive(item["type"] as String))
                                            put("size", JsonPrimitive(item["size"] as Long))
                                            put("lastModified", JsonPrimitive(item["lastModified"] as Long))
                                            put("canRead", JsonPrimitive(item["canRead"] as Boolean))
                                            put("canWrite", JsonPrimitive(item["canWrite"] as Boolean))
                                            put("depth", JsonPrimitive(item["depth"] as Int))
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
                    },
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                
                buildJsonObject {
                    // 首先检查存储权限
                    if (!hasStoragePermission()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("没有文件访问权限。${getPermissionHint()}"))
                        put("needPermission", JsonPrimitive(true))
                    } else if (path.isNullOrBlank()) {
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
                                put("path", JsonPrimitive(file.absolutePath))
                                put("canonicalPath", JsonPrimitive(file.canonicalPath))
                                put("type", JsonPrimitive(if (file.isDirectory) "directory" else "file"))
                                put("size", JsonPrimitive(file.length()))
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
                    },
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val recursive = args.jsonObject["recursive"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                
                buildJsonObject {
                    // 首先检查存储权限
                    if (!hasStoragePermission()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("没有文件访问权限。${getPermissionHint()}"))
                        put("needPermission", JsonPrimitive(true))
                    } else if (path.isNullOrBlank()) {
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
                                    put("message", JsonPrimitive("Successfully deleted: $path"))
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
                    },
                    required = listOf("source", "destination")
                )
            },
            execute = { args ->
                val source = args.jsonObject["source"]?.jsonPrimitive?.contentOrNull
                val destination = args.jsonObject["destination"]?.jsonPrimitive?.contentOrNull
                val overwrite = args.jsonObject["overwrite"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                
                buildJsonObject {
                    // 首先检查存储权限
                    if (!hasStoragePermission()) {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("没有文件访问权限。${getPermissionHint()}"))
                        put("needPermission", JsonPrimitive(true))
                    } else if (source.isNullOrBlank()) {
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
                                    put("source", JsonPrimitive(source))
                                    put("destination", JsonPrimitive(destFile.absolutePath))
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
                    },
                    required = listOf("path")
                )
            },
            execute = { args ->
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                
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
                                    put("message", JsonPrimitive("Directory already exists: $path"))
                                    put("path", JsonPrimitive(dir.absolutePath))
                                } else {
                                    put("success", JsonPrimitive(false))
                                    put("error", JsonPrimitive("Path exists but is not a directory: $path"))
                                }
                            } else {
                                val created = dir.mkdirs()
                                if (created) {
                                    put("success", JsonPrimitive(true))
                                    put("message", JsonPrimitive("Directory created: $path"))
                                    put("path", JsonPrimitive(dir.absolutePath))
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
                                } catch (e: Exception) {
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
                                        } catch (e: Exception) {
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

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.FileSystem)) {
            tools.add(fileReadTool)
            tools.add(fileWriteTool)
            tools.add(listDirectoryTool)
            tools.add(fileInfoTool)
            tools.add(fileDeleteTool)
            tools.add(fileMoveRenameTool)
            tools.add(createDirectoryTool)
        }
        if (options.contains(LocalToolOption.WebFetch)) {
            tools.add(webFetchTool)
        }
        return tools
    }
}
