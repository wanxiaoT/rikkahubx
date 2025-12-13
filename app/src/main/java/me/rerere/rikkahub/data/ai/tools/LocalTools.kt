package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
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

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("file_system")
    data object FileSystem : LocalToolOption()
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
        return tools
    }
}
