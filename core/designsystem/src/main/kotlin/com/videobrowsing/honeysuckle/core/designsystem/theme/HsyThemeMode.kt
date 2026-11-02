package com.videobrowsing.honeysuckle.core.designsystem.theme

/**
 * 外观三档("深色模式")。
 *
 * 盘上落的是 [storageId] 那个英文名,不是序号也不是枚举名:用户认得 `dark`,
 * 而"3"这种工程值一旦重排,老设置就会被读成另一档。`id → 档位`的翻译只有 [fromStorageId] 一处,
 * 分工与"密度/限帧档归 `core:player`"完全同构 —— 本模块是全仓唯一认识"主题有哪几档"的地方。
 */
enum class HsyThemeMode(val storageId: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    ;

    /**
     * 落成"这一屏到底走亮还是暗"。
     *
     * [systemDark] 由调用方给(`isSystemInDarkTheme()`):本模块不装读系统状态的探针,
     * "跟随系统"这一档的两条分支都在调用点一处决定。
     */
    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** 没设过与认不出都退这一档:跟随系统是三方客户端里最不容易出错的选择 */
        val DEFAULT = SYSTEM

        fun fromStorageId(id: String): HsyThemeMode = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}
