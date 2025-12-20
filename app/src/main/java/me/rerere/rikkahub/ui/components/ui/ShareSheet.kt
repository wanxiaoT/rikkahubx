package me.rerere.rikkahub.ui.components.ui

import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share2
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import java.io.FileOutputStream
import kotlin.io.encoding.Base64

@Composable
fun ShareSheet(
    state: ShareSheetState,
) {
    val context = LocalContext.current
    val qrCodeValue = state.currentProvider?.encodeForShare() ?: ""

    // Generate QR code bitmap for sharing
    val qrCodeWriter = remember { QRCodeWriter() }
    val qrCodeBitmap = remember(qrCodeValue) {
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

                    IconButton(
                        onClick = {
                            try {
                                qrCodeBitmap?.let { bitmap ->
                                    // Save bitmap to cache directory
                                    val cachePath = File(context.cacheDir, "shared_images")
                                    cachePath.mkdirs()
                                    val imageFile = File(cachePath, "provider_qrcode.png")
                                    FileOutputStream(imageFile).use { fos ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
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
                            }
                        }
                    ) {
                        Icon(Lucide.Share2, null)
                    }
                }

                QRCode(
                    value = qrCodeValue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
            }
        }
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
