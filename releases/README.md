# Releases

此目录存放每次正式发布的插件安装包。

## 使用方式

在 Android Studio 中安装：
1. 下载本目录中对应版本的 `.zip` 文件
2. 打开 `File → Settings → Plugins`
3. 点击齿轮图标 ⚙️ → `Install Plugin from Disk...`
4. 选中 `.zip` 文件 → `OK` → 重启 IDE

## 版本历史

| 版本 | 主要更新 |
|------|---------|
| 1.2.3 | 按 Binding 类名精确过滤，避免同名 id 跨 XML 污染结果；修复 apply/let 块内字段被漏检问题 |
| 1.2.0 | 支持 LightDataBindingField；消除 C++ 日志噪音；添加日志开关工具类 |
| 1.1.0 | 支持 ResourceReferencePsiElement，Find 窗口正式可用 |
| 1.0.0 | 初始版本，支持 XML @+id/xxx 触发 Find Usages |

