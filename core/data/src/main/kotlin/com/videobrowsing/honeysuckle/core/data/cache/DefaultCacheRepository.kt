package com.videobrowsing.honeysuckle.core.data.cache

import android.content.Context
import com.videobrowsing.honeysuckle.core.data.di.IoDispatcher
import com.videobrowsing.honeysuckle.core.model.CacheUsage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Coil 3 的磁盘缓存默认目录名;清理时它算作"图片"那一笔 */
internal const val IMAGE_CACHE_DIR = "image_cache"

/**
 * [CacheRepository] 的文件系统实现。
 *
 * 用遍历而不是自建索引表:缓存本来就会被系统/库随时增删,索引与真实文件迟早对不上,
 * 而这里每一次读数都直接问文件系统 —— 慢一点,但屏上的数与盘上的数必然一致。
 * 清理时 `image_cache` 也一起删:Coil 会自己重建 journal,而用户按下"清理"期待的是归零。
 */
@Singleton
class DefaultCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CacheRepository {

    override suspend fun usage(): CacheUsage = withContext(ioDispatcher) {
        measureCache(context.cacheDir)
    }

    override suspend fun clear(): CacheUsage = withContext(ioDispatcher) {
        val root = context.cacheDir
        root.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
        measureCache(root)
    }
}

/**
 * 缓存目录 → 两笔账。抽成纯函数是为了能在 JVM 里用临时目录钉住"图片那笔怎么算"。
 */
internal fun measureCache(root: File): CacheUsage {
    if (!root.exists()) return CacheUsage()
    val image = File(root, IMAGE_CACHE_DIR).takeIf { it.exists() }?.let(::sizeOf) ?: 0L
    return CacheUsage(totalBytes = sizeOf(root), imageBytes = image)
}

internal fun sizeOf(file: File): Long = when {
    file.isFile -> file.length()
    else -> file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
