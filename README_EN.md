[简体中文](https://github.com/wanxiaoT/rikkahubx/blob/main/README_EN.md) | [繁體中文](https://github.com/wanxiaoT/rikkahubx/blob/main/README_ZH_TW.md) | English

<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>RikkaHubX</h1>

> [!WARNING] This is NOT an officially supported version by RikkaHub!

For content related to the original project, please visit [RikkaHub-Github](https://github.com/rikkahub/rikkahub)

A native Android LLM chat client that supports switching between different providers for conversations 🤖💬




This project is forked from RikkaHub 1.16.5. PRs for this project are welcome (just explain clearly and no viruses, because I'm lazy)

The author of RikkaHubX is from mainland China, so communication is best in Simplified Chinese



RikkaHubX is an independent branch of RikkaHub, with some modified features added on top of RikkaHub. PRs will not be submitted to the original project

[RikkaHubX](https://github.com/wanxiaoT/rikkahubx) By [wanxiaoT](https://github.com/wanxiaoT)

# v1.6.18

Thanks to [Kelivo](https://github.com/Chevey339/kelivo/) for providing the multi-key management feature sample code

File Path | Description
-------|--------------------
kelivo-master\lib\core\models\api_keys.dart | Data Model - Defines core data structures
kelivo-master\lib\core\services\api_key_manager.dart | Core Service - Key selection logic and status updates
kelivo-master\lib\features\provider\pages\multi_key_manager_page.dart | UI Page - Multi-key management interface

Multi-key polling cannot be correctly used in chat in this version. The key health detection in this version is also single-threaded. Please check version 1.6.22


# v1.6.19

| No. | Update Content |
|:---:|----------|
| 1 | Extracted token/tps recording feature from rikkahub official v1.6.16 and inserted into new version source code |
| 2 | Fixed problematic base64 image transmission, switched to file storage. The original version stored images locally in SQL database. I'm not sure if it's really fixed, users need to test themselves because I don't have an image generation API |



# v1.6.20
Thanks to [CherryStudio v1.7.5](https://github.com/Chevey339/kelivo/) for providing the multi-key management feature sample code

1. Added a Logcat viewing feature below Settings-Models & Services-MCP for better troubleshooting of software issues
2. Continued fixing base64 images in chat from previous version v1.6.19, found that it was caused by inconsistency between chat data model and image data model leading to out-of-bounds issues (possibly) and mysterious index issues (possibly)
| Fix Content | File | Description |
|---------|------|------|
| Added `safeCurrentMessage` property | `Conversation.kt` | Safely get current message, automatically corrects out-of-bounds index |
| Added `safeSelectIndex` property | `Conversation.kt` | Get safe selection index |
| Modified `currentMessages` to use `safeCurrentMessage` | `Conversation.kt` | - |
| Changed all `node.currentMessage` calls to `node.safeCurrentMessage` | `ChatList.kt` | - |
| Changed `node.messages[node.selectIndex]` to `node.safeCurrentMessage` | `ChatMessage.kt` | - |
| Automatically correct invalid `selectIndex` when deserializing from database | `ConversationRepository.kt` | - |
| Fixed index access issue when removing invalid tool calls | `ChatService.kt` | - |
//Seems like the problem wasn't solved, trying to add log data stream that escapes lifecycle in next version to solve the issue


3. As suggested by group member @镜镜月月, added knowledge base feature. Test it yourself for details. You need an embedding-supported model to provide indexing for your knowledge base, such as BAAI/bge-large-zh-v1.5 provided by SiliconFlow

<p align="center">
  <img src="https://github.com/user-attachments/assets/3983be1d-cfe5-4731-af28-a79d0232951f" alt="Knowledge Base Feature Screenshot" width="300" />
</p>

Written in a hurry, didn't have time to add the modification feature for already filled knowledge bases, will add in future versions
Implementation features:

### Layer Description

### 1. User Interface Layer
- **KnowledgePage** - Knowledge base list page
- **KnowledgeDetailPage** - Knowledge base detail page
- **KnowledgeVM** - View model

### 2. Service Layer
- **KnowledgeService** - Core service, responsible for processing, searching, and management

### 3. Data Layer
| Component | Function |
|------|------|
| DocumentLoader | Document parsing |
| TextChunker | Text chunking |
| OpenAI Embedding | Vector generation |

### 4. Storage Layer
- **Room Database (SQLite)** - Vectors stored as JSON
- DAO Layer:
  - `KnowledgeBaseDAO`
  - `KnowledgeItemDAO`
  - `KnowledgeChunkDAO`

✅ Pure local implementation, no external vector database needed
✅ Uses Room/SQLite to store vectors (JSON serialization)
✅ Supports multiple document formats (plain text)
✅ Smart text chunking
✅ Cosine similarity search
⚠️ Brute force search (traverses all vectors), suitable for small to medium-sized knowledge bases



# v1.6.21

### 1. Added an X delete button on each model entry in the model list page

**Specific changes:**
- **Position:** Right side of each model entry, to the left of the settings button (gear icon)
- **Function:** Click the X button to directly delete the model, no need to swipe to delete anymore
- **Icon:** Same as the X icon in the "Available Models" popup at the bottom

**Users can now delete models in two ways:**
1. Click the X button to delete directly (new)
2. Swipe left on the entry and click delete (original feature)

---

### 2. Fixed the issue of multi-key not polling during conversations

**Root cause:** In `KeyRoulette.kt`, the polling strategy used `keyManagement?.roundRobinIndex` to decide which Key to select, but after selecting a Key, `roundRobinIndex` was not updated. This means each request would select the same Key (index 0), and the polling feature wouldn't actually work.

**Fix method:**

**KeyRoulette.kt**
- Use in-memory `roundRobinIndexMap` to maintain the polling index for each Provider
- After each Key selection, automatically increment the index by 1
- Use the first Key's ID as the Provider identifier

**ApiKeyConfig.kt**
- Added `nextRoundRobinIndex` field in `KeySelectionResult` (backup, for future persistence)

**After fix:**
- 1st request → Select Key 0
- 2nd request → Select Key 1
- 3rd request → Select Key 2
- ...and so on, cycling through

---

### 3. Multi-key management defaults to streaming mode enabled

Settings-Provider-Any Provider-Multi-Key Management section now defaults to streaming mode enabled, described as verifying SSE streaming response (enabling this allows faster verification of whether a Key is available)

---

### 4. Logcat data stream save feature

In preparation for completely fixing the index out-of-bounds issue when switching data models between image generation conversations and normal conversations, added data stream save feature in the logcat viewing page. Logcat will save in real-time to the user-selected location. Users can rename it themselves or use the default configuration `/storage/emulated/0/1RikkaHubX/Logcat.txt`
Logcat log output to file is an optional feature. Modified the six function buttons in the upper right corner of the Logcat log page to be smaller






I feel like writing the changelog in release is inconvenient so I wrote it in readme.md

If you like this project, please give it a ⭐Star

## 📄 License

[License](LICENSE)
