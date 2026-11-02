package com.videobrowsing.honeysuckle.core.common

/** 统一错误分类:文案映射在 UI 层,错误页直说原因,不硬吞 */
sealed interface AppError {
    data class Network(val cause: String) : AppError
    data class Server(val code: Int, val message: String) : AppError
    data class Decode(val cause: String) : AppError
    data class Auth(val reason: String) : AppError
    data object RateLimited : AppError
    data class Unknown(val cause: String) : AppError
}

/** 数据层统一返回包装 */
sealed interface HsyResult<out T> {
    data class Success<T>(val value: T) : HsyResult<T>
    data class Failure(val error: AppError) : HsyResult<Nothing>
}

inline fun <T, R> HsyResult<T>.map(transform: (T) -> R): HsyResult<R> = when (this) {
    is HsyResult.Success -> HsyResult.Success(transform(value))
    is HsyResult.Failure -> this
}

/** 串联多步可失败操作(如"取信封 → 再解业务体"),失败原样透传不吞 */
inline fun <T, R> HsyResult<T>.flatMap(transform: (T) -> HsyResult<R>): HsyResult<R> = when (this) {
    is HsyResult.Success -> transform(value)
    is HsyResult.Failure -> this
}
