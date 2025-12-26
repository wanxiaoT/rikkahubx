package me.rerere.rikkahub.ui.components.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share2
import com.dokar.sonner.ToastType
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.getActivity
import me.rerere.rikkahub.utils.saveImageToDCIM
import me.rerere.rikkahub.utils.ExportOptions
import me.rerere.rikkahub.utils.prepareForExport
import me.rerere.rikkahub.utils.compressData
import me.rerere.rikkahub.utils.decompressData
import me.rerere.rikkahub.utils.generateVersionFlags
import me.rerere.rikkahub.utils.parseVersionFlags
import me.rerere.rikkahub.utils.estimateQRCodeSize
import me.rerere.rikkahub.utils.checkQRCodeSize
import java.io.File
import java.io.FileOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun ShareSheet(
    state: ShareSheetState,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val activity = context.getActivity()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 导出选项状态
    var includeModels by remember { mutableStateOf(false) }
    var includeMultiKey by remember { mutableStateOf(false) }

    // 根据当前状态计算导出选项
    val exportOptions = remember(includeModels, includeMultiKey) {
        ExportOptions(
            includeModels = includeModels,
            includeMultiKey = includeMultiKey,
            useCompression = true
        )
    }

    // 生成二维码值
    val qrCodeValue by remember(state.currentProvider, exportOptions) {
        derivedStateOf {
            state.currentProvider?.encodeForShare(exportOptions) ?: ""
        }
    }

    // 估算数据大小
    val sizeInfo = remember(qrCodeValue) {
        derivedStateOf {
            if (qrCodeValue.isEmpty()) {
                Pair(false, "0B")
            } else {
                checkQRCodeSize(qrCodeValue.length)
            }
        }
    }
    val isTooLarge = sizeInfo.value.first
    val sizeStr = sizeInfo.value.second

    // Generate QR code bitmap for preview (simple version)
    val qrCodeWriter = remember { QRCodeWriter() }
    val qrCodeBitmapPreview = remember(qrCodeValue) {
        if (qrCodeValue.isEmpty()) return@remember null
        val size = 512
        val bitMatrix = qrCodeWriter.encode(qrCodeValue, BarcodeFormat.QR_CODE, size, size)
        createBitmap(size, size).apply {
            val blackColor = Color.Black.toArgb()
            val whiteColor = Color.White.toArgb()
            for (x in 0 until size) {
                for (y in 0 until size) {
                    this[x, y] = if (bitMatrix[x, y]) blackColor else whiteColor
                }
            }
        }
    }

    // Generate full QR code bitmap with provider info for sharing
    suspend fun generateFullQRCodeBitmap(): Bitmap? {
        if (activity == null) return null

        val provider = state.currentProvider ?: return null

        val composer = BitmapComposer(scope)
        return composer.composableToBitmap(
            activity = activity,
            width = 600.dp,
            screenDensity = density,
            content = {
                QRCodeWithProviderInfo(
                    providerName = provider.name,
                    baseUrl = when (provider) {
                        is ProviderSetting.OpenAI -> provider.baseUrl
                        is ProviderSetting.Claude -> provider.baseUrl
                        is ProviderSetting.Google -> if (provider.vertexAI) "Vertex AI" else provider.baseUrl
                    },
                    qrValue = qrCodeValue
                )
            }
        )
    }

    if (state.isShow) {
        ModalBottomSheet(
            onDismissRequest = {
                state.dismiss()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("共享你的LLM模型", style = MaterialTheme.typography.titleLarge)

                    // 保存到本地相册按钮
                    IconButton(
                        onClick = {
                            if (activity == null) {
                                toaster.show(
                                    "无法获取Activity",
                                    type = ToastType.Error
                                )
                                return@IconButton
                            }

                            scope.launch {
                                try {
                                    val bitmap = generateFullQRCodeBitmap()
                                    bitmap?.let {
                                        val providerName = state.currentProvider?.name ?: "provider"
                                        val fileName = "LLM_QRCode_${providerName}_${System.currentTimeMillis()}.png"

                                        val success = context.saveImageToDCIM(activity, it, fileName)
                                        if (success) {
                                            toaster.show(
                                                context.getString(R.string.chat_page_save_to_gallery_success),
                                                type = ToastType.Success
                                            )
                                        } else {
                                            toaster.show(
                                                "保存失败",
                                                type = ToastType.Error
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    toaster.show(
                                        "保存失败: ${e.message}",
                                        type = ToastType.Error
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(Lucide.Download, contentDescription = "保存到相册")
                    }

                    // 分享按钮
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val bitmap = generateFullQRCodeBitmap()
                                    bitmap?.let {
                                        // Save bitmap to cache directory
                                        val cachePath = File(context.cacheDir, "shared_images")
                                        cachePath.mkdirs()
                                        val imageFile = File(cachePath, "provider_qrcode.png")
                                        FileOutputStream(imageFile).use { fos ->
                                            it.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                        }

                                        // Get content URI using FileProvider
                                        val contentUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            imageFile
                                        )

                                        // Share image
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, contentUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, null))
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    toaster.show(
                                        "分享失败: ${e.message}",
                                        type = ToastType.Error
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(Lucide.Share2, contentDescription = "分享二维码")
                    }
                }

                // 导出选项
                state.currentProvider?.let { provider ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.share_sheet_export_options),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 包含模型列表选项
                        if (provider.models.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = includeModels,
                                    onCheckedChange = { includeModels = it }
                                )
                                Text(
                                    text = stringResource(
                                        R.string.share_sheet_include_models,
                                        provider.models.size
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // 包含多 Key 配置选项
                        if (provider.multiKeyEnabled && !provider.apiKeys.isNullOrEmpty()) {
                            val apiKeysCount = provider.apiKeys?.size ?: 0
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = includeMultiKey,
                                    onCheckedChange = { includeMultiKey = it }
                                )
                                Text(
                                    text = stringResource(
                                        R.string.share_sheet_include_multi_key,
                                        apiKeysCount
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // 数据大小显示
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.share_sheet_estimated_size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = sizeStr,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isTooLarge) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }

                        // 大小警告
                        if (isTooLarge) {
                            Text(
                                text = stringResource(R.string.share_sheet_size_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 48.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 显示带文字的二维码预览
                state.currentProvider?.let { provider ->
                    QRCodeWithProviderInfo(
                        providerName = provider.name,
                        baseUrl = when (provider) {
                            is ProviderSetting.OpenAI -> provider.baseUrl
                            is ProviderSetting.Claude -> provider.baseUrl
                            is ProviderSetting.Google -> if (provider.vertexAI) "Vertex AI" else provider.baseUrl
                        },
                        qrValue = qrCodeValue
                    )
                }
            }
        }
    }
}

/**
 * QR Code with Provider Info - Composable for generating shareable image
 */
@Composable
private fun QRCodeWithProviderInfo(
    providerName: String,
    baseUrl: String,
    qrValue: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Provider Name
        Text(
            text = providerName,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Black
            ),
            textAlign = TextAlign.Center
        )

        // Base URL
        Text(
            text = baseUrl,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.DarkGray,
                fontSize = 14.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // QR Code
        QRCode(
            value = qrValue,
            color = Color.Black,
            backgroundColor = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        // Footer text
        Text(
            text = "由 RikkaHubX - By RE（原作者） & wanxiaoT（修改增强者） 生成",
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.Gray,
                fontSize = 12.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun ProviderSetting.encodeForShare(options: ExportOptions = ExportOptions()): String {
    return buildString {
        append("ai-provider:")
        append("v2:")

        // 生成版本标志
        val flags = generateVersionFlags(options)
        append(flags)
        append(":")

        // 准备导出数据
        val preparedProvider = this@encodeForShare.prepareForExport(options)
        val jsonString = JsonInstant.encodeToString(preparedProvider)
        val jsonBytes = jsonString.encodeToByteArray()

        // 压缩或不压缩
        val dataBytes = if (options.useCompression) {
            compressData(jsonBytes)
        } else {
            jsonBytes
        }

        // Base64 编码
        append(Base64.encode(dataBytes))
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun decodeProviderSetting(value: String): ProviderSetting {
    // 检查是否为 v1 格式（旧格式，向后兼容）
    if (value.startsWith("ai-provider:v1:")) {
        val base64Str = value.removePrefix("ai-provider:v1:")
        val jsonBytes = Base64.decode(base64Str)
        val jsonStr = jsonBytes.decodeToString()
        return JsonInstant.decodeFromString<ProviderSetting>(jsonStr)
    }

    // 检查是否为 v2 格式（新格式）
    if (value.startsWith("ai-provider:v2:")) {
        val parts = value.removePrefix("ai-provider:v2:").split(":", limit = 2)

        require(parts.size == 2) { "Invalid v2 provider setting format" }

        val flags = parts[0]
        val base64Data = parts[1]

        // 解析标志
        val options = parseVersionFlags(flags)

        // Base64 解码
        val dataBytes = Base64.decode(base64Data)

        // 解压或不解压
        val jsonBytes = if (options.useCompression) {
            decompressData(dataBytes)
        } else {
            dataBytes
        }

        val jsonStr = jsonBytes.decodeToString()
        return JsonInstant.decodeFromString<ProviderSetting>(jsonStr)
    }

    throw IllegalArgumentException("Unsupported provider setting format")
}

class ShareSheetState {
    private var show by mutableStateOf(false)
    val isShow get() = show

    private var provider by mutableStateOf<ProviderSetting?>(null)
    val currentProvider get() = provider

    fun show(provider: ProviderSetting) {
        this.show = true
        this.provider = provider
    }

    fun dismiss() {
        this.show = false
    }
}

@Composable
fun rememberShareSheetState(): ShareSheetState {
    return ShareSheetState()
}
