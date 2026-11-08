package com.videobrowsing.honeysuckle.core.designsystem.component

/**
 * 勾选模式的开关束:顶栏据此在"操作菜单"与"退出管理的叉"之间切换。
 *
 * 收成一束是因为"在不在勾选"([selecting])与"进/出勾选"([onChange])永远成对出现,
 * 散成两个参数会把三页顶栏的签名都撑过 detekt 的必填参数阈值。菜单里的"管理"调 onChange(true),
 * 那颗叉调 onChange(false);退出时要不要一并清空已选,由调用方在 onChange 里决定。
 */
class SelectionControls(val selecting: Boolean, val onChange: (Boolean) -> Unit)
