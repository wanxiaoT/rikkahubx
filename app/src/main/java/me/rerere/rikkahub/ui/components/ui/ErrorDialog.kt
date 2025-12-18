package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.R

/**
 * 可复制错误信息的对话框
 * 用于显示详细的错误信息，并允许用户复制错误内容
 */
@Composable
fun ErrorDialog(
    error: Throwable,
    onDismiss: () -> Unit,
    onDeleteConversation: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    val errorMessage = buildString {
        appendLine("Error: ${error::class.simpleName}")
        appendLine("Message: ${error.message}")
        appendLine()
        appendLine("Stack Trace:")
        appendLine(error.stackTraceToString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.error_dialog_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.error_dialog_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(errorMessage))
                    }
                ) {
                    Text(stringResource(R.string.error_dialog_copy))
                }
                
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.error_dialog_close))
                }
            }
        },
        dismissButton = {
            if (onDeleteConversation != null) {
                OutlinedButton(
                    onClick = {
                        onDeleteConversation()
                        onDismiss()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.error_dialog_delete_conversation),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

/**
 * 检查是否是CursorWindow相关的错误
 */
fun Throwable.isCursorWindowError(): Boolean {
    val message = this.message ?: ""
    return message.contains("CursorWindow") || 
           message.contains("Row too big") ||
           message.contains("Couldn't read row") ||
           this.cause?.isCursorWindowError() == true
}

/**
 * 获取用户友好的错误描述
 */
fun Throwable.getUserFriendlyMessage(): String {
    return when {
        isCursorWindowError() -> "数据库行数据过大，可能是因为对话中包含了过多的图片数据。建议删除该对话。"
        else -> message ?: "未知错误"
    }
}