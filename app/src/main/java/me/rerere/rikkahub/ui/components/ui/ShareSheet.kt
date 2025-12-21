package me.rerere.rikkahub.ui.components.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import java.io.File
import java.io.FileOutputStream
import kotlin.io.encoding.Base64

@Composable
fun ShareSheet(
    state: ShareSheetState,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val activity = context.getActivity()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val qrCodeValue = state.currentProvider?.encodeForShare() ?: ""

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

fun ProviderSetting.encodeForShare(): String {
    return buildString {
        append("ai-provider:")
        append("v1:")

        val value = JsonInstant.encodeToString(this@encodeForShare.copyProvider(models = emptyList()))
        append(Base64.encode(value.encodeToByteArray()))
    }
}

fun decodeProviderSetting(value: String): ProviderSetting {
    require(value.startsWith("ai-provider:v1:")) { "Invalid provider setting string" }

    // 去掉前缀
    val base64Str = value.removePrefix("ai-provider:v1:")

    // Base64解码
    val jsonBytes = Base64.decode(base64Str)
    val jsonStr = jsonBytes.decodeToString()

    return JsonInstant.decodeFromString<ProviderSetting>(jsonStr)
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
