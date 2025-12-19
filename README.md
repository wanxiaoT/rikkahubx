<div align="center">
  <img src="docs/icon.png" alt="App 图标" width="100" />
  <h1>RikkaHubX</h1>

> [!WARNING] 这不是受到 RikkaHub官方 支持的版本！

与原项目有关的内容请前往[RikkaHub-Github](https://github.com/rikkahub/rikkahub)

一个原生Android LLM 聊天客户端，支持切换不同的供应商进行聊天 🤖💬




本项目Fork自RikkaHub 1.16.5，本项目支持对于本项目的PR（说明清楚，没病毒就行，因为我懒 ）

RikkaHubX的作者是大陆人，交流最好用简体中文



RikkaHubX是RikkaHub的独立分支，在RikkaHub的基础上添加了部分修改功能，不会提交PR给原项目

[RikkaHubX](https://github.com/wanxiaoT/rikkahubx) By [wanxiaoT](https://github.com/wanxiaoT)

# v1.6.18

感谢 [Kelivo](https://github.com/Chevey339/kelivo/) 提供的多 Key 管理功能示例代码

文件路径             | 说明
-------|--------------------
kelivo-master\lib\core\models\api_keys.dart | 数据模型 - 定义核心数据结构
kelivo-master\lib\core\services\api_key_manager.dart | 核心服务 - Key 选择逻辑和状态更新
kelivo-master\lib\features\provider\pages\multi_key_manager_page.dart | UI 页面 - 多 Key 管理界面

多Key轮询在这个版本里面不能正确使用到聊天中，这个版本的密钥健康度检测也是单线程的，请查看1.6.22版本


# v1.6.19

| 序号 | 更新内容 |
|:---:|----------|
| 1 | 从 rikkahub 官方 v1.6.16 提取 token/tps 记录功能插入新版源码 |
| 2 | 修复存在问题的 base64 图片传输，转为使用文件存储。原有版本的图片在本地是用 SQL 数据库存的，我也不知道是不是真的修复了，还得用户自己测试，因为我没有画图的 api |



# v1.6.20
感谢 [CherryStudio v1.7.5](https://github.com/Chevey339/kelivo/) 提供的多 Key 管理功能示例代码

1.在设置-模型与服务-MCP下方添加一个Logcat查看功能，更好排查软件出现的问题
2.继续上个版本v1.6.19的对于聊天中base64图片的修复，发现是聊天数据模型和图片数据模型不一致导致的越界问题（可能）和神秘索引问题（可能）
| 修复内容 | 文件 | 说明 |
|---------|------|------|
| 新增 `safeCurrentMessage` 属性 | `Conversation.kt` | 安全获取当前消息，自动修正越界的索引 |
| 新增 `safeSelectIndex` 属性 | `Conversation.kt` | 获取安全的选择索引 |
| 修改 `currentMessages` 使用 `safeCurrentMessage` | `Conversation.kt` | - |
| 将所有 `node.currentMessage` 调用改为 `node.safeCurrentMessage` | `ChatList.kt` | - |
| 将 `node.messages[node.selectIndex]` 改为 `node.safeCurrentMessage` | `ChatMessage.kt` | - |
| 在从数据库反序列化时自动修正无效的 `selectIndex` | `ConversationRepository.kt` | - |
| 修复移除无效 tool call 时的索引访问问题 | `ChatService.kt` | - |
//貌似并没有解决问题，尝试在下个版本中添加跳出生命周期的日志数据流以解决问题


3.应群友 @镜镜月月 提议，添加知识库功能，具体的自己测试即可，要有一个支持嵌入的模型提供你知识库的索引，例如硅基流动提供的BAAI/bge-large-zh-v1.5

<p align="center">
  <img src="https://github.com/user-attachments/assets/3983be1d-cfe5-4731-af28-a79d0232951f" alt="知识库功能截图" width="300" />
</p>

写的仓促没来得及加上已经填写的知识库修改功能，以后版本会加
实现特点：

### 层级说明

### 1. 用户界面层
- **KnowledgePage** - 知识库列表页面
- **KnowledgeDetailPage** - 知识库详情页面
- **KnowledgeVM** - 视图模型

### 2. 服务层
- **KnowledgeService** - 核心服务，负责处理、搜索、管理

### 3. 数据层
| 组件 | 功能 |
|------|------|
| DocumentLoader | 文档解析 |
| TextChunker | 文本分块 |
| OpenAI Embedding | 向量生成 |

### 4. 存储层
- **Room Database (SQLite)** - 向量以JSON存储
- DAO层：
  - `KnowledgeBaseDAO`
  - `KnowledgeItemDAO`
  - `KnowledgeChunkDAO`

✅ 纯本地实现，无需外部向量数据库
✅ 使用Room/SQLite存储向量（JSON序列化）
✅ 支持多种文档格式（纯文本）
✅ 智能文本分块
✅ 余弦相似度搜索
⚠️ 暴力搜索（遍历所有向量），适合中小规模知识库



# v1.6.21

### 1. 在模型列表页面的每个模型条目上添加了一个X删除按钮

**具体改动：**
- **位置：** 每个模型条目右边，在调控按钮（齿轮图标）的左边
- **功能：** 点击X按钮可以直接删除该模型，不需要再滑动删除了
- **图标：** 和底部"可用模型"弹窗里的X图标一样

**现在用户可以通过两种方式删除模型：**
1. 点击X按钮直接删除（新增）
2. 向左滑动条目后点击删除（原有功能）

---

### 2. 修复多Key对话时不轮询的问题

**问题缘由：** 在 `KeyRoulette.kt` 中，轮询策略使用了 `keyManagement?.roundRobinIndex` 来决定选择哪个Key，但是选择完Key后，没有更新 `roundRobinIndex`这意味着每次请求都会选择同一个Key（索引0），轮询功能实际上不会工作。

**修复方式：**

**KeyRoulette.kt**
- 使用内存中的 `roundRobinIndexMap` 来维护每个Provider的轮询索引
- 每次选择Key后，自动将索引+1
- 使用第一个Key的ID作为Provider标识

**ApiKeyConfig.kt**
- 在 `KeySelectionResult` 中添加了 `nextRoundRobinIndex` 字段（备用，方便未来持久化）

**修复后的效果：**
- 第1次请求 → 选择Key 0
- 第2次请求 → 选择Key 1
- 第3次请求 → 选择Key 2
- ...依此类推，循环轮询

---

### 3. 多Key管理默认开启流式模式

设置-提供商-任意提供商-多Key管理 部分默认开启使用流式模式，描述为验证SSE流式响应（开启后可以更快地验证Key是否可用）

---

### 4. Logcat数据流保存功能

为完全修复生图对话和普通对话切换数据模型时的索引越界问题做准备，在logcat查看页面中添加了数据流保存功能，logcat会实时保存到用户选择的位置，用户可以自行重命名，也可以使用默认配置 `/storage/emulated/0/1RikkaHubX/Logcat.txt`
Logcat日志输出为文件为可选选项，修改Logcat日志页面右上角的六个功能按钮，修改的更小一点






我感觉把更新日志写release里面不方便我就写readme.md了

觉得本项目好请点一个⭐Star

## 📄 许可证

[License](LICENSE)