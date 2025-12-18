<div align="center">
  <img src="docs/icon.png" alt="App 图标" width="100" />
  <h1>RikkaHubX</h1>

> [!WARNING] 这不是受到 RikkaHub官方 支持的版本！

与原项目有关的内容请前往[RikkaHub-Github](https://github.com/rikkahub/rikkahub)

一个原生Android LLM 聊天客户端，支持切换不同的供应商进行聊天 🤖💬




本项目Fork自RikkaHub 1.16.5（maybe）

RikkaHubX的作者是大陆人，交流最好用简体中文



RikkaHubX是RikkaHub的独立分支，在RikkaHub的基础上添加了部分修改功能，不会提交PR给原项目

[RikkaHubX](https://github.com/wanxiaoT/rikkahubx) By [wanxiaoT](https://github.com/wanxiaoT)

## v1.6.18

感谢 [Kelivo](https://github.com/Chevey339/kelivo/) 提供的多 Key 管理功能示例代码

文件路径             | 说明
-------|--------------------
kelivo-master\lib\core\models\api_keys.dart | 数据模型 - 定义核心数据结构
kelivo-master\lib\core\services\api_key_manager.dart | 核心服务 - Key 选择逻辑和状态更新
kelivo-master\lib\features\provider\pages\multi_key_manager_page.dart | UI 页面 - 多 Key 管理界面



## v1.6.19

| 序号 | 更新内容 |
|:---:|----------|
| 1 | 从 rikkahub 官方 v1.6.16 提取 token/tps 记录功能插入新版源码 |
| 2 | 修复存在问题的 base64 图片传输，转为使用文件存储。原有版本的图片在本地是用 SQL 数据库存的，我也不知道是不是真的修复了，还得用户自己测试，因为我没有画图的 api |




觉得本项目好请点一个⭐Star

## 📄 许可证

[License](LICENSE)


