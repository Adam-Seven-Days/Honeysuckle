package com.videobrowsing.honeysuckle.core.data.offline

import com.videobrowsing.honeysuckle.core.model.DownloadState
import com.videobrowsing.honeysuckle.core.model.DownloadTask
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 任务表的落盘。
 *
 * 只存"任务是什么、文件在哪、停在哪",不存字节进度:文件本身的长度就是进度 ——
 * 进程被杀时最后一次内存刷新必然丢失,拿盘上的真实长度校准才是对的。
 *
 * 写临时文件再改名:一次写一半被系统杀掉,下次启动不该从半截 JSON 读出半个任务表。
 */
internal class OfflineIndex(private val file: File) {

    fun read(): List<DownloadTask> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val root = Json.parseToJsonElement(file.readText()) as? JsonArray ?: return emptyList()
            root.mapNotNull { element -> (element as? JsonObject)?.toTask() }
        }.getOrDefault(emptyList())
    }

    fun write(tasks: List<DownloadTask>) {
        val parent = file.parentFile ?: return
        parent.mkdirs()
        val payload = buildJsonArray { tasks.forEach { add(it.toJson()) } }.toString()
        val temp = File(parent, "${file.name}.tmp")
        temp.writeText(payload)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private fun DownloadTask.toJson(): JsonObject = buildJsonObject {
        put("bvid", bvid)
        put("cid", cid)
        put("title", title)
        put("upName", upName)
        put("coverUrl", coverUrl)
        put("qualityId", qualityId)
        put("qualityLabel", qualityLabel)
        put("durationMs", durationMs)
        put("state", state.name)
        put("videoPath", videoPath)
        put("audioPath", audioPath)
        put("totalBytes", totalBytes)
        put("error", error.orEmpty())
        put("updatedEpochMs", updatedEpochMs)
    }

    private fun JsonObject.toTask(): DownloadTask? {
        val bvid = text("bvid")
        val cid = number("cid")
        if (bvid.isBlank() || cid <= 0L) return null
        return DownloadTask(
            bvid = bvid,
            cid = cid,
            title = text("title"),
            upName = text("upName"),
            coverUrl = text("coverUrl"),
            qualityId = number("qualityId").toInt(),
            qualityLabel = text("qualityLabel"),
            durationMs = number("durationMs"),
            state = runCatching { DownloadState.valueOf(text("state")) }.getOrDefault(DownloadState.PAUSED),
            videoPath = text("videoPath"),
            audioPath = text("audioPath"),
            totalBytes = number("totalBytes"),
            error = text("error").ifBlank { null },
            updatedEpochMs = number("updatedEpochMs"),
        )
    }

    private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.number(key: String): Long =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
}
