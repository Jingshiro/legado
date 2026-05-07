# Obsidian Local REST API 插件使用教程

为了在阅读 App 中使用 Obsidian 导出功能，您需要配置 Obsidian 的 Local REST API 插件。以下是具体步骤：

## 1. 安装插件

1. 打开 Obsidian，进入 **设置 (Settings)**。
2. 选择 **第三方插件 (Community plugins)**。
3. 点击 **浏览 (Browse)**，搜索 `Local REST API` 并安装。
4. 安装完成后，点击 **启用 (Enable)**。

## 2. 核心配置 (关键)

为了让手机能够访问到电脑上的 Obsidian，请进行以下设置：

1. **Enable Non-Localhost Access**: **必须开启**。否则插件仅允许电脑自身访问。
2. **Insecure Bind Address**: 建议设置为 `0.0.0.0`。这表示插件将监听电脑所有网络接口的请求。
3. **API Key**: 点击生成或复制现有的 API Key。导出时需要用到。
4. **Port (端口)**: 默认为 `27124`，通常不需要修改。

## 3. 获取电脑 IP 地址

手机需要知道电脑在网络中的地址才能进行连接。

### 情况 A：同局域网/同 Wi-Fi

1. 在电脑上按下 `Win + R`，输入 `cmd` 并回车。
2. 输入 `ipconfig` 并回车。
3. 找到 **IPv4 地址**（通常是 `192.168.x.x`）。

### 情况 B：使用 Tailscale 等内网穿透

1. 打开 Tailscale 客户端。
2. 找到本机的 IP 地址（通常是 `100.x.x.x`）。

## 4. 在阅读 App 中配置

1. 打开阅读 App 的 Obsidian 导出界面。
2. **API 地址**:
   - 格式为：`https://<你的电脑IP>:27124`（注意，是https！）
   - 例如：`https://192.168.1.5:27124`
3. **API Key**: 粘贴刚才复制的 Key。
4. 点击 **测试连接**。

## 5. 常见问题

- **连接失败**:
  - 请确保手机和电脑在同一 Wi-Fi 下，或 VPN (如 Tailscale) 已连接。
  - 检查电脑防火墙是否放行了 `27124` 端口。
  - 确认插件设置中的 **Enable Non-Localhost Access** 已开启。

---

## 我的实践

> [!note] 提示
> 这部分只是我自用的工作流，**仅供参考**，你完全可以按你自己更喜欢的方式进行整理。

### 1. 准备工作

- **新建文件夹**: 在obsidian的笔记库中新建一个 `Z待清洗书摘` 文件夹（其实你也可以取别的名字，只要你知道就行），并将其作为阅读 App 的 **导出路径**。
- **创建清洗间**: 在该文件夹外设置一个空白文档，命名为 `清洗间.md`。
- **安装插件**: 确保 Obsidian 已安装 **Templater** 插件。

### 2. 自动化清洗脚本

下载下面这个清洗脚本，它能批量处理导入的原始书摘，实现去重、封面下载、排版美化等功能。

- **脚本下载**: [tpl\_书摘清理脚本.md](https://pub-0e6ce5d0161d4148a621c594405613f1.r2.dev/tpl_书摘清理脚本.md)

**使用方法**:

1. 下载脚本后放入 Obsidian 模板文件夹。
2. 打开 `清洗间.md`。
3. 按下 Templater 快捷键（通常是 `Alt + E`），选择该脚本运行。
4. 脚本将自动扫描导出目录，并完成整理。

---

感谢[Adam Coddington](https://coddingtonbear.net/)开发的Local REST API 和[SilentVoid13](https://github.com/SilentVoid13/)开发的Templater 插件~
灵感/部分交互思路参考自 [纸间书摘](https://www.xmnote.com/)，非常非常好用的阅读书摘记录软件，强力推荐大家使用~
