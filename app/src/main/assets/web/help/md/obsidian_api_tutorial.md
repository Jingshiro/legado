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
   - 格式为：`http://<您的电脑IP>:27124`
   - 例如：`http://192.168.1.5:27124`
3. **API Key**: 粘贴刚才复制的 Key。
4. 点击 **测试连接**。

## 5. 常见问题

- **连接失败**:
  - 请确保手机和电脑在同一 Wi-Fi 下，或 VPN (如 Tailscale) 已连接。
  - 检查电脑防火墙是否放行了 `27124` 端口。
  - 确认插件设置中的 **Enable Non-Localhost Access** 已开启。
