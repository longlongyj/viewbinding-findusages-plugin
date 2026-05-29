package com.dragold.plugin

import com.intellij.openapi.diagnostic.Logger

/**
 * ViewBinding Find Usages 插件日志工具。
 *
 * ──────────────────────────────────────────────────────────────────────────
 * 如何在 Android Studio / IntelliJ IDEA 中查看插件日志
 * ──────────────────────────────────────────────────────────────────────────
 *
 * 方式一：菜单直接打开（推荐）
 *   Help → Show Log in Explorer（Windows）/ Show Log in Finder（macOS）
 *   打开所在文件夹后，用文本编辑器或 tail 命令查看 idea.log。
 *   Windows PowerShell 实时跟踪：
 *     Get-Content "$env:LOCALAPPDATA\Google\AndroidStudio*\log\idea.log" -Wait -Tail 50
 *
 * 方式二：查看日志文件
 *   Windows： %LOCALAPPDATA%\Google\AndroidStudioX.Y\log\idea.log
 *   macOS：   ~/Library/Logs/Google/AndroidStudioX.Y/idea.log
 *   Linux：   ~/.cache/Google/AndroidStudioX.Y/log/idea.log
 *   （X.Y 为版本号，如 AndroidStudio2024.2，可在 Help → About 查看确切路径）
 *
 * 方式三：内置日志查看器
 *   在 Android Studio / IntelliJ IDEA 中安装插件 "Ideolog"，可高亮过滤 idea.log。
 *
 * 搜索关键字：
 *   grep "[JvmStatic]" idea.log          # Linux / macOS
 *   Select-String "\[JvmStatic\]" idea.log  # Windows PowerShell
 *
 * ──────────────────────────────────────────────────────────────────────────
 * 日志级别说明
 * ──────────────────────────────────────────────────────────────────────────
 *   info  → 受 [ENABLED] 开关控制，ENABLED=false 时不输出（默认关闭）
 *   debug → 受 [ENABLED] 开关控制，ENABLED=false 时不输出（默认关闭）
 *   warn  → 始终输出，不受开关影响（用于排查问题时的诊断日志）
 *   error → 始终输出，不受开关影响
 *
 * 排查问题时：将 [ENABLED] 改为 true 后重新 buildPlugin 并安装，
 * 即可看到所有 info/debug 级别日志。
 */
object LJLogger {

    /**
     * 日志开关。
     * - true  → 输出所有 INFO 日志（调试模式）
     * - false → 静默，不输出任何日志（生产模式）
     */
    const val ENABLED = false

    // ── 各类的 Logger 实例缓存 ──────────────────────────────────────────────

    private val loggers = mutableMapOf<Class<*>, Logger>()

    private fun logger(clazz: Class<*>): Logger =
        loggers.getOrPut(clazz) { Logger.getInstance(clazz) }

    // ── 公共日志方法 ────────────────────────────────────────────────────────

    fun info(clazz: Class<*>, message: String) {
        if (ENABLED) logger(clazz).info(message)
    }

    fun debug(clazz: Class<*>, message: String) {
        if (ENABLED) logger(clazz).debug(message)
    }

    fun warn(clazz: Class<*>, message: String) {
        // 警告和错误始终输出，不受开关影响
        logger(clazz).warn(message)
    }

    fun error(clazz: Class<*>, message: String, t: Throwable? = null) {
        if (t != null) logger(clazz).error(message, t)
        else logger(clazz).error(message)
    }
}

