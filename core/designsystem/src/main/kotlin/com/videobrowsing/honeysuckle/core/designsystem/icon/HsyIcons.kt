// 24×24 viewport 的路径坐标是绘图数据,不是业务魔数:给每个控制点起名只会让这张图对不上形
@file:Suppress("MagicNumber")

package com.videobrowsing.honeysuckle.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.icon.HsyIcons

/**
 * 产品自带的图标集:全部用 [ImageVector] 的路径指令画出来,没有一张图片资源。
 *
 * 为什么不用 PNG / SVG 文件:
 *  - 图标只需要单色,颜色由 `Icon(tint = …)` 按主题给 —— 位图做不到"一套图随亮暗换色";
 *  - 没有密度桶(它们看到的是一个 24×24 viewport 的矢量,任意尺寸重绘);
 *  - 不缺角、不会因为打包压缩出锯齿,也不多出一条"这张图从哪来的"许可记录。
 *
 * 造型按 24×24 画,全部是**线画空心**(圆头圆角);状态(已点赞 / 已收藏 / 弹幕开关)
 * 只靠 tint 与文字区分,不维护"空心版 + 实心版"两套形状。
 */
object HsyIcons {

    /** 点赞:拇指轮廓。点亮态由调用方换色,不另画一套。 */
    val thumbUp: ImageVector by lazy {
        icon("HsyIcons.ThumbUp") {
            strokePath(lineWidth = 1.6f) {
                moveTo(1f, 21f)
                horizontalLineTo(5f)
                verticalLineTo(9f)
                horizontalLineTo(1f)
                close()
                moveTo(23f, 10f)
                curveTo(23f, 8.9f, 22.1f, 8f, 21f, 8f)
                horizontalLineTo(14.69f)
                lineTo(15.64f, 3.43f)
                lineTo(15.67f, 3.11f)
                curveTo(15.67f, 2.7f, 15.5f, 2.32f, 15.23f, 2.05f)
                lineTo(14.17f, 1f)
                lineTo(7.59f, 7.59f)
                curveTo(7.22f, 7.95f, 7f, 8.45f, 7f, 9f)
                verticalLineTo(19f)
                curveTo(7f, 20.1f, 7.9f, 21f, 9f, 21f)
                horizontalLineTo(18f)
                curveTo(18.83f, 21f, 19.54f, 20.5f, 19.84f, 19.78f)
                lineTo(22.86f, 12.73f)
                curveTo(22.95f, 12.5f, 23f, 12.26f, 23f, 12f)
                verticalLineTo(10f)
                close()
            }
        }
    }

    /** 点踩:拇指朝下,与 [thumbUp] 同一套几何 */
    val thumbDown: ImageVector by lazy {
        icon("HsyIcons.ThumbDown") {
            strokePath(lineWidth = 1.6f) {
                moveTo(15f, 3f)
                horizontalLineTo(6f)
                curveTo(5.17f, 3f, 4.46f, 3.5f, 4.16f, 4.22f)
                lineTo(1.14f, 11.27f)
                curveTo(1.05f, 11.5f, 1f, 11.74f, 1f, 12f)
                verticalLineTo(14f)
                curveTo(1f, 15.1f, 1.9f, 16f, 2.9f, 16f)
                horizontalLineTo(9.31f)
                lineTo(8.36f, 20.57f)
                lineTo(8.33f, 20.89f)
                curveTo(8.33f, 21.3f, 8.5f, 21.68f, 8.77f, 21.95f)
                lineTo(9.83f, 23f)
                lineTo(16.41f, 16.41f)
                curveTo(16.77f, 16.05f, 17f, 15.55f, 17f, 15f)
                verticalLineTo(5f)
                curveTo(17f, 3.9f, 16.1f, 3f, 15f, 3f)
                close()
                moveTo(19f, 3f)
                verticalLineTo(15f)
                horizontalLineTo(23f)
                verticalLineTo(3f)
                close()
            }
        }
    }

    /** 投币:一枚有厚度的硬币轮廓(上沿一个椭圆 + 侧壁与下沿) */
    val coin: ImageVector by lazy {
        icon("HsyIcons.Coin") {
            strokePath(lineWidth = 1.6f) {
                ellipse(12f, 7.4f, 8f, 3.2f)
                moveTo(4f, 7.4f)
                verticalLineTo(16.4f)
                quadTo(4f, 19.6f, 12f, 19.6f)
                quadTo(20f, 19.6f, 20f, 16.4f)
                verticalLineTo(7.4f)
            }
        }
    }

    /** 收藏:星形轮廓;收藏与否只靠 tint 与文字区分,不换形状 */
    val star: ImageVector by lazy {
        icon("HsyIcons.Star") {
            strokePath(lineWidth = 1.6f) {
                moveTo(12f, 17.27f)
                lineTo(18.18f, 21f)
                lineTo(16.54f, 13.97f)
                lineTo(22f, 9.24f)
                lineTo(14.81f, 8.63f)
                lineTo(12f, 2f)
                lineTo(9.19f, 8.63f)
                lineTo(2f, 9.24f)
                lineTo(7.46f, 13.97f)
                lineTo(5.82f, 21f)
                close()
            }
        }
    }

    /** 分享:三个节点 + 两条连线,与系统分享面板是同一件事 */
    val share: ImageVector by lazy {
        icon("HsyIcons.Share") {
            strokePath(lineWidth = 1.6f) {
                ellipse(18f, 5f, 3f, 3f)
                ellipse(6f, 12f, 3f, 3f)
                ellipse(18f, 19f, 3f, 3f)
                moveTo(8.59f, 10.49f)
                lineTo(15.41f, 6.51f)
                moveTo(8.59f, 13.51f)
                lineTo(15.41f, 17.49f)
            }
        }
    }

    /** 弹幕开:气泡轮廓 + 两行文字 */
    val danmakuOn: ImageVector by lazy {
        icon("HsyIcons.DanmakuOn") {
            strokePath(lineWidth = 1.6f) {
                bubble()
            }
            strokePath(lineWidth = 1.4f) {
                moveTo(6.8f, 9.2f)
                horizontalLineTo(17.2f)
                moveTo(6.8f, 12.8f)
                horizontalLineTo(13.6f)
            }
        }
    }

    /** 弹幕关:同一个气泡,压一道斜杠 */
    val danmakuOff: ImageVector by lazy {
        icon("HsyIcons.DanmakuOff") {
            strokePath(lineWidth = 1.6f) {
                bubble()
            }
            strokePath(lineWidth = 1.8f) {
                moveTo(3.5f, 20.5f)
                lineTo(20.5f, 3.5f)
            }
        }
    }

    /** 评论:气泡轮廓 + 一排省略号(设置页的"评论"一组用它) */
    val comment: ImageVector by lazy {
        icon("HsyIcons.Comment") {
            strokePath(lineWidth = 1.6f) {
                bubble()
            }
            strokePath(lineWidth = 2.2f) {
                moveTo(8f, 11f)
                lineTo(8.1f, 11f)
                moveTo(12f, 11f)
                lineTo(12.1f, 11f)
                moveTo(16f, 11f)
                lineTo(16.1f, 11f)
            }
        }
    }

    /** 返回:左向折线 */
    val back: ImageVector by lazy {
        icon("HsyIcons.Back") {
            strokePath(lineWidth = 1.8f) {
                moveTo(15f, 5f)
                lineTo(8f, 12f)
                lineTo(15f, 19f)
            }
        }
    }

    /** 进入:右向折线 */
    val chevronRight: ImageVector by lazy {
        icon("HsyIcons.ChevronRight") {
            strokePath(lineWidth = 1.8f) {
                moveTo(9f, 5f)
                lineTo(16f, 12f)
                lineTo(9f, 19f)
            }
        }
    }

    /** 展开:下向折线,排序菜单这类"点开看选项"的入口缀在文案右边 */
    val chevronDown: ImageVector by lazy {
        icon("HsyIcons.ChevronDown") {
            strokePath(lineWidth = 1.8f) {
                moveTo(5f, 9f)
                lineTo(12f, 16f)
                lineTo(19f, 9f)
            }
        }
    }

    /** 首页:屋顶 + 屋身 + 门 */
    val home: ImageVector by lazy {
        icon("HsyIcons.Home") {
            strokePath(lineWidth = 1.7f) {
                moveTo(3f, 11f)
                lineTo(12f, 3f)
                lineTo(21f, 11f)
                moveTo(5.5f, 9.5f)
                verticalLineTo(20f)
                horizontalLineTo(18.5f)
                verticalLineTo(9.5f)
                moveTo(10f, 20f)
                verticalLineTo(14f)
                horizontalLineTo(14f)
                verticalLineTo(20f)
            }
        }
    }

    /** 动态:三条列表线 + 三个点 */
    val list: ImageVector by lazy {
        icon("HsyIcons.List") {
            strokePath(lineWidth = 1.7f) {
                moveTo(9f, 6f)
                horizontalLineTo(20f)
                moveTo(9f, 12f)
                horizontalLineTo(20f)
                moveTo(9f, 18f)
                horizontalLineTo(20f)
            }
            strokePath(lineWidth = 2.2f) {
                moveTo(4.5f, 6f)
                lineTo(4.6f, 6f)
                moveTo(4.5f, 12f)
                lineTo(4.6f, 12f)
                moveTo(4.5f, 18f)
                lineTo(4.6f, 18f)
            }
        }
    }

    /** 直播:播放三角 */
    val play: ImageVector by lazy {
        icon("HsyIcons.Play") {
            strokePath(lineWidth = 1.7f) {
                moveTo(8f, 5f)
                lineTo(19f, 12f)
                lineTo(8f, 19f)
                close()
            }
        }
    }

    /** 番剧:日历 */
    val calendar: ImageVector by lazy {
        icon("HsyIcons.Calendar") {
            strokePath(lineWidth = 1.6f) {
                moveTo(5f, 6f)
                horizontalLineTo(19f)
                quadTo(20f, 6f, 20f, 7f)
                verticalLineTo(20f)
                quadTo(20f, 21f, 19f, 21f)
                horizontalLineTo(5f)
                quadTo(4f, 21f, 4f, 20f)
                verticalLineTo(7f)
                quadTo(4f, 6f, 5f, 6f)
                close()
                moveTo(4f, 10f)
                horizontalLineTo(20f)
                moveTo(8f, 3f)
                verticalLineTo(7f)
                moveTo(16f, 3f)
                verticalLineTo(7f)
            }
        }
    }

    /** 我的:头像轮廓 */
    val person: ImageVector by lazy {
        icon("HsyIcons.Person") {
            strokePath(lineWidth = 1.7f) {
                circleAt(12f, 8f, 4f)
                moveTo(4.5f, 20f)
                quadTo(4.5f, 14.5f, 12f, 14.5f)
                quadTo(19.5f, 14.5f, 19.5f, 20f)
            }
        }
    }

    /** 稍后再看:一只表盘 + 两根针;已加入与否同样只靠 tint 与文字,不另画"已选中的表盘" */
    val clock: ImageVector by lazy {
        icon("HsyIcons.Clock") {
            strokePath(lineWidth = 1.6f) {
                circleAt(12f, 12f, 8.5f)
                moveTo(12f, 7.2f)
                verticalLineTo(12f)
                lineTo(15.8f, 14.2f)
            }
        }
    }

    /** 搜索:放大镜 */
    val search: ImageVector by lazy {
        icon("HsyIcons.Search") {
            strokePath(lineWidth = 1.7f) {
                circleAt(10.5f, 10.5f, 6.5f)
                moveTo(15.2f, 15.2f)
                lineTo(20.5f, 20.5f)
            }
        }
    }

    /** 刷新:顺时针缺口圆 + 箭头 */
    val refresh: ImageVector by lazy {
        icon("HsyIcons.Refresh") {
            strokePath(lineWidth = 1.7f) {
                moveTo(19.5f, 12.5f)
                arcTo(7.5f, 7.5f, 0f, true, true, 14f, 4.8f)
                moveTo(19.8f, 4.5f)
                lineTo(19.8f, 9.5f)
                lineTo(14.8f, 9.5f)
            }
        }
    }

    /** 新增:加号 */
    val add: ImageVector by lazy {
        icon("HsyIcons.Add") {
            strokePath(lineWidth = 1.9f) {
                moveTo(12f, 5f)
                verticalLineTo(19f)
                moveTo(5f, 12f)
                horizontalLineTo(19f)
            }
        }
    }

    /** 关闭:叉 */
    val close: ImageVector by lazy {
        icon("HsyIcons.Close") {
            strokePath(lineWidth = 1.8f) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    /** 勾选:对勾,批量管理里选中态的那一枚 */
    val check: ImageVector by lazy {
        icon("HsyIcons.Check") {
            strokePath(lineWidth = 2f) {
                moveTo(5f, 12.5f)
                lineTo(10f, 17.5f)
                lineTo(19f, 6.5f)
            }
        }
    }

    /** 删除:垃圾桶 */
    val delete: ImageVector by lazy {
        icon("HsyIcons.Delete") {
            strokePath(lineWidth = 1.6f) {
                moveTo(4f, 7f)
                horizontalLineTo(20f)
                moveTo(9.5f, 7f)
                verticalLineTo(4.5f)
                horizontalLineTo(14.5f)
                verticalLineTo(7f)
                moveTo(6.5f, 7f)
                lineTo(7.5f, 20.5f)
                horizontalLineTo(16.5f)
                lineTo(17.5f, 7f)
                moveTo(10.5f, 10.5f)
                verticalLineTo(17f)
                moveTo(13.5f, 10.5f)
                verticalLineTo(17f)
            }
        }
    }

    /** 编辑:铅笔 */
    val edit: ImageVector by lazy {
        icon("HsyIcons.Edit") {
            strokePath(lineWidth = 1.6f) {
                moveTo(4f, 20f)
                lineTo(5.5f, 15.5f)
                lineTo(16.5f, 4.5f)
                lineTo(19.5f, 7.5f)
                lineTo(8.5f, 18.5f)
                close()
                moveTo(14f, 7f)
                lineTo(17f, 10f)
            }
        }
    }

    /** 更多:竖排三点 */
    val more: ImageVector by lazy {
        icon("HsyIcons.More") {
            strokePath(lineWidth = 2.4f) {
                moveTo(12f, 5f)
                lineTo(12.1f, 5f)
                moveTo(12f, 12f)
                lineTo(12.1f, 12f)
                moveTo(12f, 19f)
                lineTo(12.1f, 19f)
            }
        }
    }

    /** 信息:圆 + i */
    val info: ImageVector by lazy {
        icon("HsyIcons.Info") {
            strokePath(lineWidth = 1.6f) {
                circleAt(12f, 12f, 8.5f)
                moveTo(12f, 11f)
                verticalLineTo(16.5f)
            }
            strokePath(lineWidth = 2.2f) {
                moveTo(12f, 7.8f)
                lineTo(12.1f, 7.8f)
            }
        }
    }

    /** 设置:三根推子,比齿轮更符合"扁平" */
    val settings: ImageVector by lazy {
        icon("HsyIcons.Settings") {
            strokePath(lineWidth = 1.6f) {
                moveTo(3.5f, 8f)
                horizontalLineTo(20.5f)
                moveTo(3.5f, 16f)
                horizontalLineTo(20.5f)
                circleAt(9f, 8f, 2.4f)
                circleAt(15.5f, 16f, 2.4f)
            }
        }
    }

    /** 太阳:外观从深色翻回浅色时的那一颗按钮 */
    val sun: ImageVector by lazy {
        icon("HsyIcons.Sun") {
            strokePath(lineWidth = 1.6f) {
                circleAt(12f, 12f, 4.4f)
                moveTo(12f, 2.5f)
                verticalLineTo(4.8f)
                moveTo(12f, 19.2f)
                verticalLineTo(21.5f)
                moveTo(2.5f, 12f)
                horizontalLineTo(4.8f)
                moveTo(19.2f, 12f)
                horizontalLineTo(21.5f)
                moveTo(5.3f, 5.3f)
                lineTo(6.9f, 6.9f)
                moveTo(17.1f, 17.1f)
                lineTo(18.7f, 18.7f)
                moveTo(5.3f, 18.7f)
                lineTo(6.9f, 17.1f)
                moveTo(17.1f, 6.9f)
                lineTo(18.7f, 5.3f)
            }
        }
    }

    /** 月亮:外观从浅色翻到深色时的那一颗按钮;一弯月牙,不画星星 */
    val moon: ImageVector by lazy {
        icon("HsyIcons.Moon") {
            strokePath(lineWidth = 1.6f) {
                moveTo(20.5f, 13.3f)
                arcTo(8.5f, 8.5f, 0f, true, true, 10.7f, 3.5f)
                arcTo(6.8f, 6.8f, 0f, false, false, 20.5f, 13.3f)
                close()
            }
        }
    }

    /** 消息:铃铛轮廓 + 底下一颗小锤 */
    val bell: ImageVector by lazy {
        icon("HsyIcons.Bell") {
            strokePath(lineWidth = 1.6f) {
                moveTo(6f, 16.5f)
                verticalLineTo(11f)
                curveTo(6f, 7.6f, 8.7f, 5f, 12f, 5f)
                curveTo(15.3f, 5f, 18f, 7.6f, 18f, 11f)
                verticalLineTo(16.5f)
                moveTo(4.5f, 16.5f)
                horizontalLineTo(19.5f)
                moveTo(10f, 19f)
                quadTo(10f, 21f, 12f, 21f)
                quadTo(14f, 21f, 14f, 19f)
                moveTo(12f, 2.5f)
                verticalLineTo(4.5f)
            }
        }
    }

    /** 离线下载:向下箭头落进一个托盘 */
    val download: ImageVector by lazy {
        icon("HsyIcons.Download") {
            strokePath(lineWidth = 1.7f) {
                moveTo(12f, 3.5f)
                verticalLineTo(15f)
                moveTo(7.5f, 10.5f)
                lineTo(12f, 15f)
                lineTo(16.5f, 10.5f)
                moveTo(4.5f, 17.5f)
                verticalLineTo(20.5f)
                horizontalLineTo(19.5f)
                verticalLineTo(17.5f)
            }
        }
    }

    /** 暂停:两条竖杠 */
    val pause: ImageVector by lazy {
        icon("HsyIcons.Pause") {
            strokePath(lineWidth = 1.8f) {
                moveTo(9f, 5f)
                verticalLineTo(19f)
                moveTo(15f, 5f)
                verticalLineTo(19f)
            }
        }
    }

    /** 警告:三角 + 感叹号 */
    val warning: ImageVector by lazy {
        icon("HsyIcons.Warning") {
            strokePath(lineWidth = 1.6f) {
                moveTo(12f, 3.5f)
                lineTo(21f, 19.5f)
                horizontalLineTo(3f)
                close()
                moveTo(12f, 9f)
                verticalLineTo(14f)
            }
            strokePath(lineWidth = 2.2f) {
                moveTo(12f, 16.8f)
                lineTo(12.1f, 16.8f)
            }
        }
    }
}

/** 弹幕/评论共用的气泡轮廓(闭合) */
private fun PathBuilder.bubble() {
    moveTo(5.5f, 5f)
    horizontalLineTo(18.5f)
    quadTo(21f, 5f, 21f, 7.5f)
    verticalLineTo(15.5f)
    quadTo(21f, 18f, 18.5f, 18f)
    horizontalLineTo(11.5f)
    lineTo(7.5f, 21f)
    verticalLineTo(18f)
    horizontalLineTo(5.5f)
    quadTo(3f, 18f, 3f, 15.5f)
    verticalLineTo(7.5f)
    quadTo(3f, 5f, 5.5f, 5f)
    close()
}

/** 空心圆:统一的圆心 + 半径画法 */
private fun PathBuilder.circleAt(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    close()
}

/** 空心圆/椭圆的统一画法:两段半弧拼一个闭合形 */
private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
    moveTo(cx - rx, cy)
    arcTo(rx, ry, 0f, false, true, cx + rx, cy)
    arcTo(rx, ry, 0f, false, true, cx - rx, cy)
    close()
}

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(block).build()

/** 线画用的统一口径:圆头圆角,颜色留黑,由 `Icon` 的 tint 覆盖 */
private fun ImageVector.Builder.strokePath(
    lineWidth: Float = 1.7f,
    block: PathBuilder.() -> Unit,
): ImageVector.Builder = path(
    stroke = SolidColor(Color.Black),
    strokeLineWidth = lineWidth,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = block,
)
