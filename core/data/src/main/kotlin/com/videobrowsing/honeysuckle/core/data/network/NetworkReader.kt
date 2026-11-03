package com.videobrowsing.honeysuckle.core.data.network

/**
 * 此刻这条网按不按量计费 —— "仅 Wi-Fi 自动播"与"仅 Wi-Fi 下载"这两档要问的就是这一句。
 *
 * 只出一个布尔而不是出网络类型:调用方判的是"要不要在用户不知情时替他花流量",
 * 把 `TRANSPORT_WIFI` / `TRANSPORT_CELLULAR` 那一串交出去,每个调用点都会各自猜一遍哪个算免费。
 */
interface NetworkReader {

    /**
     * 现在走的是计费网络(移动数据)吗。
     *
     * **读不到就当计费**:没有活动网络、系统不给能力描述、权限之外的任何情况都回 `true`。
     * 这一侧判错的代价是多按一次播放或多点一次下载,反过来判错是替用户烧掉一笔他不知道的钱。
     */
    fun isMetered(): Boolean
}
