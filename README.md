# ViewBinding Find Usages

> 一款 Android Studio 插件，让 **Find Usages** 不再遗漏 ViewBinding 字段的使用位置。

---

## 目录

1. [背景与痛点](#背景与痛点)
2. [插件功能](#插件功能)
3. [安装教程](#安装教程)
4. [使用教程](#使用教程)
5. [支持的使用场景](#支持的使用场景)
6. [兼容性](#兼容性)
7. [常见问题](#常见问题)

---

## 背景与痛点

### Android Studio 原生 Find Usages 的缺陷

在使用 ViewBinding 的 Android 项目中，一个 XML 布局 id（如 `@+id/tv_title`）在代码中会通过
`binding.tvTitle` 的形式访问。然而 Android Studio **原生 Find Usages** 存在以下问题：

| 场景 | 原生行为 | 期望行为 |
|------|----------|----------|
| 在 XML 中对 `@+id/tv_title` 执行 Find Usages | 只找到 `R.id.tv_title` 的 Java/Kotlin 引用，**遗漏** `binding.tvTitle` | 同时列出所有 `binding.tvTitle` 使用位置 |
| 想知道某个 View 在代码中被用了几次 | 需要分别搜索 `R.id.xxx` 和 `binding.xxx`，手动合并 | 一次搜索，结果完整 |
| 重命名或删除某个 View 前做影响分析 | 容易遗漏 ViewBinding 访问，导致遗漏改动点 | 所有引用一目了然 |

### 根本原因

Android Studio 的 Find Usages 引擎将 `@+id/tv_title`（XML 属性）与 `binding.tvTitle`（ViewBinding
生成字段）视为**两个完全独立的符号**，不会自动关联。

---

## 插件功能

✅ **在 XML 中对 `@+id/xxx` 执行 Find Usages** → 结果中同时包含 `binding.xxx` 的所有使用位置

✅ **在 Kotlin/Java 中对 `binding.xxx` 执行 Find Usages** → 结果同样完整，且只显示对应 Binding 类的引用（不混入其他 XML 的同名字段）

✅ **按 Binding 类名精确过滤** → 同名 id 在不同 XML 中出现时，结果不会互相污染
（如 `fragment_home.xml` 和 `fragment_detail.xml` 都有 `@+id/tv_title`，搜索时各自独立）

✅ **支持多种访问写法**，全部能被识别：

```kotlin
// 直接访问
binding.tvTitle.text = "Hello"

// 安全调用
binding?.tvTitle?.text = "Hello"

// apply 块（隐式 this）
binding.apply {
    tvTitle.text = "Hello"
}

// let 块（通过 it 访问）
binding.let {
    it.tvTitle.text = "Hello"
}

// 安全 let 块
binding?.let {
    it.tvTitle.text = "Hello"
}

// run / also / with 块
with(binding) {
    tvTitle.text = "Hello"
}
```

---

## 安装教程

### 方法一：从本地文件安装（推荐，离线可用）

1. 前往 [releases 目录](releases) 下载最新版本的 `.zip` 文件
   （文件名格式：`viewbinding-findusages-plugin-x.x.x.zip`）

2. 打开 Android Studio，进入菜单：
   ```
   File → Settings → Plugins
   ```
   > macOS 用户：`Android Studio → Settings → Plugins`

3. 点击插件列表右上角的齿轮图标 ⚙️，选择 **Install Plugin from Disk...**

4. 在文件选择对话框中选中下载的 `.zip` 文件，点击 **OK**

5. 点击 **Restart IDE**，重启后插件生效

### 方法二：从 JetBrains Marketplace 安装（即将上线）

> ⏳ 还未上线，敬请期待，上线后即可通过 Marketplace 直接搜索安装。

<!--
1. 打开 Android Studio，进入菜单：
   ```
   File → Settings → Plugins → Marketplace
   ```

2. 搜索 `ViewBinding Find Usages`

3. 点击 **Install**，重启 IDE
-->

### 验证安装

重启后，在任意 XML 布局文件中找到一个 `@+id/xxx` 属性，右键点击 id 值，
菜单中出现 **Find Usages** 选项后执行，如果结果窗口中同时显示了 `binding.xxx`
的使用位置，说明插件安装成功。

---

## 使用教程

### 入口一：从 XML 布局文件发起（最常用）

1. 打开任意布局 XML 文件（如 `fragment_home.xml`）

2. 将光标定位到某个 View 的 `android:id` 属性值上，将光标放在 id 值处，例如 `"@+id/tv_title"` 的引号内：

   ```
   android:id="@+id/tv_title"
   ```

3. 按快捷键 **Alt+F7**（macOS：**⌥F7**），或右键选择 **Find Usages**

4. Find Usages 结果窗口将同时展示：
   - XML 中其他引用该 id 的位置（如 `include`、`ConstraintLayout` 约束等）
   - Kotlin/Java 代码中所有 `binding.tvTitle` 的使用位置

   > 结果只包含 `FragmentHomeBinding.tvTitle` 的引用，不会混入其他布局中同名字段的引用。

---

### 入口二：从 Kotlin/Java 代码发起

1. 打开 Kotlin 或 Java 文件，将光标定位到 binding 字段访问处：
   ```kotlin
   binding.tvTitle.text = "Hello"
   //      ↑ 将光标放在 tvTitle 上
   ```

2. 按 **Alt+F7** 执行 Find Usages

3. 结果窗口展示该项目中所有 `binding.tvTitle`（属于同一 Binding 类）的使用位置

---

### 结果窗口说明

Find Usages 结果会按文件分组展示，ViewBinding 字段的使用将与其他引用（如 XML 定义、
`R.id` 引用）一起显示：

```
Find Usages: tv_title
├── XML Usages (1)
│   └── fragment_home.xml:12  @+id/tv_title
├── Kotlin Usages (3)
│   ├── HomeFragment.kt:45   binding.tvTitle.text = title
│   ├── HomeFragment.kt:67   binding.apply { tvTitle.visibility = View.GONE }
│   └── HomeFragment.kt:89   binding?.let { it.tvTitle.text = subtitle }
```

---

## 支持的使用场景

| 触发方式 | 元素类型 | Binding 类过滤 |
|----------|----------|---------------|
| XML `@+id/xxx` 属性 | `XmlAttributeValue` | ✅ 按 XML 文件名精确过滤 |
| Android Studio Find 窗口（右键 XML） | `ResourceReferencePsiElement` | ✅ 按 XML 文件名精确过滤 |
| Kotlin/Java `binding.field` | `LightDataBindingField` | ✅ 按 Binding 类名精确过滤 |

---

## 兼容性

| 项目 | 说明 |
|------|------|
| **Android Studio 版本** | Iguana (2023.2) 及以上所有版本 |
| **操作系统** | Windows / macOS / Linux，完全跨平台 |
| **Kotlin / Java** | 两者均支持 |
| **ViewBinding** | 支持（插件专为 ViewBinding 设计） |
| **DataBinding** | 不支持（DataBinding 有独立的引用机制） |
| **未来版本 AS** | 无版本上限限制，AS 升级后无需重新安装 |

---

## 常见问题

**Q：安装后 Find Usages 结果没有 binding.xxx，怎么办？**

A：请确认：
1. 项目已开启 ViewBinding（`buildFeatures { viewBinding = true }`）
2. 在 XML 文件中操作，而不是在 `R.java` 中
3. 代码中访问 binding 的变量名包含 `binding` 或 `Binding` 字样（如 `mBinding`、`viewBinding`）

---

**Q：为什么只有部分 binding.tvTitle 出现在结果里？**

A：插件会按 Binding 类名过滤，只收录来自对应 XML 的 Binding 类引用。
例如搜索 `fragment_home.xml` 中的 `@+id/tv_title`，只有代码中出现 `FragmentHomeBinding`
的文件中的 `tvTitle` 引用才会被收录，防止不同布局的同名字段互相污染。

---

**Q：插件会影响 Android Studio 性能吗？**

A：影响极小。插件仅在执行 Find Usages 时介入，日常编辑、索引等操作均不参与。
搜索本身基于 IntelliJ 平台的词索引（`PsiSearchHelper`），性能与原生搜索相当。

---

**Q：支持 include 布局或 merge 布局吗？**

A：支持，ViewBinding 对 `<include>` 生成嵌套字段，只要代码中通过 binding 对象访问，
均可被插件识别。

---

## 开源协议

本项目基于 [MIT License](LICENSE) 开源。

你可以自由地：
- 免费使用、复制、修改、合并、发布本插件
- 将其集成到个人或商业项目中

唯一要求：在软件副本中保留原始版权声明。

