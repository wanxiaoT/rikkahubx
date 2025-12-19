[简体中文](https://github.com/wanxiaoT/rikkahubx/blob/main/README_EN.md) | 繁體中文 | [English](https://github.com/wanxiaoT/rikkahubx/blob/main/README_EN.md)

<div align="center">
  <img src="docs/icon.png" alt="App 圖示" width="100" />
  <h1>RikkaHubX</h1>

> [!WARNING] 這不是受到 RikkaHub官方 支援的版本！

與原專案有關的內容請前往[RikkaHub-Github](https://github.com/rikkahub/rikkahub)

一個原生Android LLM 聊天客戶端，支援切換不同的供應商進行聊天 🤖💬




本專案Fork自RikkaHub 1.16.5，本專案支援對於本專案的PR（說明清楚，沒病毒就行，因為我懶）

RikkaHubX的作者是大陸人，交流最好用簡體中文



RikkaHubX是RikkaHub的獨立分支，在RikkaHub的基礎上添加了部分修改功能，不會提交PR給原專案

[RikkaHubX](https://github.com/wanxiaoT/rikkahubx) By [wanxiaoT](https://github.com/wanxiaoT)

# v1.6.18

感謝 [Kelivo](https://github.com/Chevey339/kelivo/) 提供的多 Key 管理功能示例程式碼

檔案路徑             | 說明
-------|--------------------
kelivo-master\lib\core\models\api_keys.dart | 資料模型 - 定義核心資料結構
kelivo-master\lib\core\services\api_key_manager.dart | 核心服務 - Key 選擇邏輯和狀態更新
kelivo-master\lib\features\provider\pages\multi_key_manager_page.dart | UI 頁面 - 多 Key 管理介面

多Key輪詢在這個版本裡面不能正確使用到聊天中，這個版本的密鑰健康度檢測也是單執行緒的，請查看1.6.22版本


# v1.6.19

| 序號 | 更新內容 |
|:---:|----------|
| 1 | 從 rikkahub 官方 v1.6.16 提取 token/tps 記錄功能插入新版原始碼 |
| 2 | 修復存在問題的 base64 圖片傳輸，轉為使用檔案儲存。原有版本的圖片在本地是用 SQL 資料庫存的，我也不知道是不是真的修復了，還得使用者自己測試，因為我沒有畫圖的 api |



# v1.6.20
感謝 [CherryStudio v1.7.5](https://github.com/Chevey339/kelivo/) 提供的多 Key 管理功能示例程式碼

1.在設定-模型與服務-MCP下方添加一個Logcat查看功能，更好排查軟體出現的問題
2.繼續上個版本v1.6.19的對於聊天中base64圖片的修復，發現是聊天資料模型和圖片資料模型不一致導致的越界問題（可能）和神秘索引問題（可能）
| 修復內容 | 檔案 | 說明 |
|---------|------|------|
| 新增 `safeCurrentMessage` 屬性 | `Conversation.kt` | 安全獲取當前訊息，自動修正越界的索引 |
| 新增 `safeSelectIndex` 屬性 | `Conversation.kt` | 獲取安全的選擇索引 |
| 修改 `currentMessages` 使用 `safeCurrentMessage` | `Conversation.kt` | - |
| 將所有 `node.currentMessage` 呼叫改為 `node.safeCurrentMessage` | `ChatList.kt` | - |
| 將 `node.messages[node.selectIndex]` 改為 `node.safeCurrentMessage` | `ChatMessage.kt` | - |
| 在從資料庫反序列化時自動修正無效的 `selectIndex` | `ConversationRepository.kt` | - |
| 修復移除無效 tool call 時的索引存取問題 | `ChatService.kt` | - |
//貌似並沒有解決問題，嘗試在下個版本中添加跳出生命週期的日誌資料流以解決問題


3.應群友 @鏡鏡月月 提議，添加知識庫功能，具體的自己測試即可，要有一個支援嵌入的模型提供你知識庫的索引，例如矽基流動提供的BAAI/bge-large-zh-v1.5

<p align="center">
  <img src="https://github.com/user-attachments/assets/3983be1d-cfe5-4731-af28-a79d0232951f" alt="知識庫功能截圖" width="300" />
</p>

寫的倉促沒來得及加上已經填寫的知識庫修改功能，以後版本會加
實現特點：

### 層級說明

### 1. 使用者介面層
- **KnowledgePage** - 知識庫列表頁面
- **KnowledgeDetailPage** - 知識庫詳情頁面
- **KnowledgeVM** - 視圖模型

### 2. 服務層
- **KnowledgeService** - 核心服務，負責處理、搜尋、管理

### 3. 資料層
| 元件 | 功能 |
|------|------|
| DocumentLoader | 文件解析 |
| TextChunker | 文字分塊 |
| OpenAI Embedding | 向量生成 |

### 4. 儲存層
- **Room Database (SQLite)** - 向量以JSON儲存
- DAO層：
  - `KnowledgeBaseDAO`
  - `KnowledgeItemDAO`
  - `KnowledgeChunkDAO`

✅ 純本地實現，無需外部向量資料庫
✅ 使用Room/SQLite儲存向量（JSON序列化）
✅ 支援多種文件格式（純文字）
✅ 智慧文字分塊
✅ 餘弦相似度搜尋
⚠️ 暴力搜尋（遍歷所有向量），適合中小規模知識庫



# v1.6.21

### 1. 在模型列表頁面的每個模型條目上添加了一個X刪除按鈕

**具體改動：**
- **位置：** 每個模型條目右邊，在調控按鈕（齒輪圖示）的左邊
- **功能：** 點擊X按鈕可以直接刪除該模型，不需要再滑動刪除了
- **圖示：** 和底部「可用模型」彈窗裡的X圖示一樣

**現在使用者可以透過兩種方式刪除模型：**
1. 點擊X按鈕直接刪除（新增）
2. 向左滑動條目後點擊刪除（原有功能）

---

### 2. 修復多Key對話時不輪詢的問題

**問題緣由：** 在 `KeyRoulette.kt` 中，輪詢策略使用了 `keyManagement?.roundRobinIndex` 來決定選擇哪個Key，但是選擇完Key後，沒有更新 `roundRobinIndex`這意味著每次請求都會選擇同一個Key（索引0），輪詢功能實際上不會工作。

**修復方式：**

**KeyRoulette.kt**
- 使用記憶體中的 `roundRobinIndexMap` 來維護每個Provider的輪詢索引
- 每次選擇Key後，自動將索引+1
- 使用第一個Key的ID作為Provider標識

**ApiKeyConfig.kt**
- 在 `KeySelectionResult` 中添加了 `nextRoundRobinIndex` 欄位（備用，方便未來持久化）

**修復後的效果：**
- 第1次請求 → 選擇Key 0
- 第2次請求 → 選擇Key 1
- 第3次請求 → 選擇Key 2
- ...依此類推，循環輪詢

---

### 3. 多Key管理預設開啟串流模式

設定-供應商-任意供應商-多Key管理 部分預設開啟使用串流模式，描述為驗證SSE串流回應（開啟後可以更快地驗證Key是否可用）

---

### 4. Logcat資料流儲存功能

為完全修復生圖對話和普通對話切換資料模型時的索引越界問題做準備，在logcat查看頁面中添加了資料流儲存功能，logcat會即時儲存到使用者選擇的位置，使用者可以自行重新命名，也可以使用預設配置 `/storage/emulated/0/1RikkaHubX/Logcat.txt`
Logcat日誌輸出為檔案為可選選項，修改Logcat日誌頁面右上角的六個功能按鈕，修改的更小一點






我感覺把更新日誌寫release裡面不方便我就寫readme.md了

覺得本專案好請點一個⭐Star

## 📄 授權條款

[License](LICENSE)
