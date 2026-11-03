package com.videobrowsing.honeysuckle.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NetworkReader] 的系统实现:问一次当前的活动网络,不注册回调。
 *
 * 不做长驻监听是因为这两个问题都发生在"用户刚按下一个动作"的那一刻(开播、点下载),
 * 那一刻的答案才作数;注册 `NetworkCallback` 等于为了省一次系统调用,
 * 让应用在前台后台都挂着一条连接,还要自己管注册与摘除的时机。
 */
@Singleton
class DefaultNetworkReader @Inject constructor(@ApplicationContext context: Context) : NetworkReader {

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isMetered(): Boolean {
        val capabilities = connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
            ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
