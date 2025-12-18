package me.rerere.rikkahub.data.model

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.InstantSerializer
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * 精简版的会话信息，用于列表显示，不包含消息内容以避免 OOM
 */
data class ConversationSummary(
    val id: Uuid,
    val assistantId: Uuid,
    val title: String,
    val isPinned: Boolean = false,
    val createAt: Instant,
    val updateAt: Instant,
)

@Serializable
data class Conversation(
    val id: Uuid = Uuid.Companion.random(),
    val assistantId: Uuid,
    val title: String = "",
    val messageNodes: List<MessageNode>,
    val truncateIndex: Int = -1,
    val chatSuggestions: List<String> = emptyList(),
    val isPinned: Boolean = false,
    @Serializable(with = InstantSerializer::class)
    val createAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updateAt: Instant = Instant.now(),
) {
    val files: List<Uri>
        get() {
            val images = messageNodes
                .flatMap { node -> node.messages.flatMap { it.parts } }
                .filterIsInstance<UIMessagePart.Image>()
                .mapNotNull {
                    it.url.takeIf { it.startsWith("file://") }?.toUri()
                }
            val documents = messageNodes
                .flatMap { node -> node.messages.flatMap { it.parts } }
                .filterIsInstance<UIMessagePart.Document>()
                .mapNotNull {
                    it.url.takeIf { it.startsWith("file://") }?.toUri()
                }
            val videos = messageNodes
                .flatMap { node -> node.messages.flatMap { it.parts } }
                .filterIsInstance<UIMessagePart.Video>()
                .mapNotNull {
                    it.url.takeIf { it.startsWith("file://") }?.toUri()
                }
            val audios = messageNodes
                .flatMap { node -> node.messages.flatMap { it.parts } }
                .filterIsInstance<UIMessagePart.Audio>()
                .mapNotNull {
                    it.url.takeIf { it.startsWith("file://") }?.toUri()
                }
            return images + documents + videos + audios
        }

    /**
     *  当前选中的 message
     */
    val currentMessages
        get(): List<UIMessage> {
            return messageNodes.map { node -> node.safeCurrentMessage }
        }

    fun getMessageNodeByMessage(message: UIMessage): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.contains(message) }
    }

    fun getMessageNodeByMessageId(messageId: Uuid): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.any { it.id == messageId } }
    }

    fun updateCurrentMessages(messages: List<UIMessage>): Conversation {
        val newNodes = this.messageNodes.toMutableList()

        messages.forEachIndexed { index, message ->
            val node = newNodes
                .getOrElse(index) { message.toMessageNode() }

            val newMessages = node.messages.toMutableList()
            var newMessageIndex = node.selectIndex
            if (newMessages.any { it.id == message.id }) {
                newMessages[newMessages.indexOfFirst { it.id == message.id }] = message
            } else {
                newMessages.add(message)
                newMessageIndex = newMessages.lastIndex
            }

            val newNode = node.copy(
                messages = newMessages,
                selectIndex = newMessageIndex
            )

            // 更新newNodes
            if (index > newNodes.lastIndex) {
                newNodes.add(newNode)
            } else {
                newNodes[index] = newNode
            }
        }

        return this.copy(
            messageNodes = newNodes
        )
    }

    companion object {
        fun ofId(
            id: Uuid,
            assistantId: Uuid = DEFAULT_ASSISTANT_ID,
            messages: List<MessageNode> = emptyList(),
        ) = Conversation(
            id = id,
            assistantId = assistantId,
            messageNodes = messages
        )
    }
}

@Serializable
data class MessageNode(
    val id: Uuid = Uuid.random(),
    val messages: List<UIMessage>,
    val selectIndex: Int = 0,
) {
    /**
     * 安全获取当前选中的消息，如果索引无效则返回第一条消息
     * 用于UI渲染等场景，避免因索引越界导致崩溃
     */
    val safeCurrentMessage: UIMessage get() {
        if (messages.isEmpty()) {
            throw IllegalStateException("MessageNode has no messages")
        }
        val safeIndex = selectIndex.coerceIn(messages.indices)
        return messages[safeIndex]
    }

    /**
     * 获取当前选中的消息，如果索引无效则抛出异常
     * 用于需要严格验证的场景
     */
    val currentMessage get() = if (messages.isEmpty() || selectIndex !in messages.indices) {
        throw IllegalStateException("MessageNode has no valid current message: messages.size=${messages.size}, selectIndex=$selectIndex")
    } else {
        messages[selectIndex]
    }

    /**
     * 获取安全的选择索引，确保在有效范围内
     */
    val safeSelectIndex: Int get() = if (messages.isEmpty()) 0 else selectIndex.coerceIn(messages.indices)

    val role get() = messages.firstOrNull()?.role ?: MessageRole.USER

    companion object {
        fun of(message: UIMessage) = MessageNode(
            messages = listOf(message),
            selectIndex = 0
        )
    }
}

fun UIMessage.toMessageNode(): MessageNode {
    return MessageNode(
        messages = listOf(this),
        selectIndex = 0
    )
}
