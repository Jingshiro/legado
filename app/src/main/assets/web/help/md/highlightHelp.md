# 高亮模式说明

高亮模式是替换规则的扩展功能，可以对正则表达式的捕获组应用 HTML 样式。

## 基本用法

1. 开启 **高亮模式** 开关
2. 在 **替换规则** 中输入正则表达式，使用捕获组 `()` 匹配需要高亮的部分
3. 在 **替换为** 中使用 `$1`, `$2` 等引用捕获组，并用 HTML 标签包裹

## 支持的样式标签

| 标签 | 效果 | 示例 |
|------|------|------|
| `<b>` 或 `<strong>` | 加粗 | `<b>$1</b>` |
| `<i>` 或 `<em>` | 斜体 | `<i>$1</i>` |
| `<u>` | 下划线 | `<u>$1</u>` |
| `<font color="red">` | 颜色 | `<font color="red">$1</font>` |
| `<span style="...">` | 组合样式 | `<span style="font-weight:bold;color:red">$1</span>` |

## 支持的 CSS 样式属性

| 属性 | 值 | 说明 |
|------|------|------|
| `font-weight` | `bold` 或 `100-900` | 加粗（>=700 视为加粗） |
| `font-style` | `italic` | 斜体 |
| `text-decoration` | `underline` | 下划线 |
| `color` | 颜色名或 `#RRGGBB` | 文字颜色 |
| `font-size` | `Npx`（如 `16px`） | 字号 |
| `font-family` | 字体名 | 字体（需配置字体目录） |

## 支持的颜色名

red, blue, green, yellow, white, black, gray, cyan, magenta, orange, purple, pink, brown, gold, silver, navy, teal, maroon

## 使用示例

### 示例1：整行红色加粗
- 正则：`^(.+)$`
- 替换为：`<span style="font-weight:bold;color:red">$1</span>`

### 示例2：多捕获组独立样式
- 正则：`^(.*?)\[(.*?)\](.*)$`
- 替换为：`<span style="font-weight:bold">$1</span><span style="color:red">$2</span><span style="font-style:italic">$3</span>`

### 示例3：只显示部分捕获组
- 正则：`^(.*?)\[(.*?)\](.*)$`
- 替换为：`<b>$1</b><i>$3</i>`（丢弃捕获组2）

### 示例4：使用字体
- 正则：`^(.+)$`
- 替换为：`<span style="font-family:楷体">$1</span>`

## 工具栏使用

开启高亮模式后，"替换为"输入框上方会出现样式工具栏：

- **B** - 插入加粗标签 `<b></b>`
- **I** - 插入斜体标签 `<i></i>`
- **U** - 插入下划线标签 `<u></u>`
- **颜色** - 选择颜色，插入 `<span style="color:xxx"></span>`
- **字号** - 选择字号，插入 `<span style="font-size:Npx"></span>`
- **字体** - 选择字体，插入 `<span style="font-family:xxx"></span>`

使用方法：先将光标定位到"替换为"输入框中需要插入样式的位置，然后点击工具栏按钮。

## 字体配置

使用字体功能前，需要在 **设置 > 界面 > 字体目录** 中配置字体文件所在的目录。

支持的字体格式：`.ttf`, `.otf`

字体名 = 文件名（不含扩展名），例如 `楷体.ttf` 对应的字体名为 `楷体`。

## 注意事项

1. 高亮模式需要开启 **使用正则表达式**
2. 捕获组索引从 `$1` 开始
3. 同一个捕获组只能有一种样式，后面的样式会覆盖前面的
4. 如果替换为中没有引用某个捕获组，该捕获组的内容将不会显示
