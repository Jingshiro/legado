**2026/05/18**

- 修复AI助手界面改为MD风格后启动崩溃的问题
- 修复MaterialCardView的strokeColor使用了drawable属性(?attr/dividerHorizontal)而非颜色导致在Android 16上崩溃
- 修复BaseActivity运行时覆盖Material主题导致MaterialComponents组件样式无法解析
- 修复引用MaterialComponents内部私有样式ShapeAppearanceOverlay导致的兼容性问题

**2022/10/02**

- 更新cronet: 106.0.5249.79
- 正文选择菜单朗读按钮长按可切换朗读选择内容和从选择开始处一直朗读
- 源编辑输入框设置最大行数12,在行数特别多的时候更容易滚动到其它输入
- 修复某些情况下无法搜索到标题的bug，净化规则较多的可能会降低搜索速度 by Xwite
- 修复文件类书源换源后阅读bug by Xwite
- Cronet 支持DnsHttpsSvcb by g2s20150909
- 修复web进度同步问题 by 821938089
- 启用混淆以减小app大小 有bug请带日志反馈
- 其它一些优化
