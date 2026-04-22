package com.dragold.plugin

import com.intellij.openapi.diagnostic.Logger

/**
 * ViewBinding Find Usages 插件日志工具。
 *
 * 将 [ENABLED] 改为 false 即可关闭所有插件日志，避免日常使用中产生大量日志输出。
 * 需要排查问题时再改回 true 并重新构建插件。
 */
object LJLogger {

    /**
     * 日志开关。
     * - true  → 输出所有 INFO 日志（调试模式）
     * - false → 静默，不输出任何日志（生产模式）
     */
    const val ENABLED = true

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

