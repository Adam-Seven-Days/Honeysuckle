package com.videobrowsing.honeysuckle.core.data.crash

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** 崩溃记录的最长保留长度:一份栈够定位,再长只是把关于页撑爆 */
private const val MAX_CHARS = 64 * 1024

/** 异常链最多展开这么多层;再深的 cause 对定位没有增量 */
private const val MAX_CAUSE_DEPTH = 8

/**
 * 崩溃的本地落盘(Release 不打日志,Crash 仅本地落盘,在"关于"页可手动导出)。
 *
 * 只写一个"最近一次"文件,不累积:崩溃日志的用途是"刚挂的那次为什么挂",
 * 而保留十份旧栈只会让用户导出时挑不出来。写盘本身包在 runCatching 里 ——
 * 记录崩溃的代码再抛一次,会把原来的栈冲掉,变成更难查的二次崩溃。
 *
 * **落盘内容只有异常类名 + 脱敏后的 message**,不写栈帧:栈帧里会带方法参数、
 * 请求 URL 与偶尔被内联进来的 Cookie,而定位"哪一类异常、说了什么"这两项已经够用。
 */
@Singleton
class CrashStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val crashFile: File get() = File(context.filesDir, CRASH_DIR).resolve(FILE_NAME)

    /** 装在进程最前面;先落盘,再把控制权交回系统默认处理器(它负责弹"应用已停止") */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 读出来的文本再脱敏一次:旧版本落盘的完整栈同样可能在展示/复制时带出凭证 */
    fun latest(): String? = runCatching {
        crashFile.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }?.let(CrashRedactor::redact)
    }.getOrNull()

    fun clear() {
        runCatching { crashFile.delete() }
    }

    private fun write(thread: Thread, throwable: Throwable) {
        val text = buildString {
            appendLine("时间:${Instant.now()}")
            appendLine("线程:${thread.name}")
            append(describe(throwable))
        }.take(MAX_CHARS)
        crashFile.parentFile?.mkdirs()
        crashFile.writeText(text)
    }

    /** 异常链只留类名与 message;message 进文本前统一过 [CrashRedactor] */
    private fun describe(throwable: Throwable): String = buildString {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (depth > 0) append("Caused by: ")
            append(current.javaClass.name)
            current.message?.takeIf { it.isNotBlank() }?.let { message ->
                append(": ").append(CrashRedactor.redact(message))
            }
            append('\n')
            current = current.cause?.takeIf { it !== current }
            depth++
        }
    }

    private companion object {
        const val CRASH_DIR = "crash"
        const val FILE_NAME = "last-crash.txt"
    }
}
