package com.videobrowsing.honeysuckle.core.data.crash

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** 崩溃记录的最长保留长度:一份栈够定位,再长只是把关于页撑爆 */
private const val MAX_CHARS = 64 * 1024

/**
 * 崩溃的本地落盘(Release 不打日志,Crash 仅本地落盘,在"关于"页可手动导出)。
 *
 * 只写一个"最近一次"文件,不累积:崩溃日志的用途是"刚挂的那次为什么挂",
 * 而保留十份旧栈只会让用户导出时挑不出来。写盘本身包在 runCatching 里 ——
 * 记录崩溃的代码再抛一次,会把原来的栈冲掉,变成更难查的二次崩溃。
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

    fun latest(): String? = runCatching {
        crashFile.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear() {
        runCatching { crashFile.delete() }
    }

    private fun write(thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { printer ->
                printer.println("时间:${Instant.now()}")
                printer.println("线程:${thread.name}")
                throwable.printStackTrace(printer)
            }
        }.toString().take(MAX_CHARS)
        crashFile.parentFile?.mkdirs()
        crashFile.writeText(stack)
    }

    private companion object {
        const val CRASH_DIR = "crash"
        const val FILE_NAME = "last-crash.txt"
    }
}
