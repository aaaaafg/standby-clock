package com.deskclock.standby

import android.os.Build

/**
 * ROM 识别：判断小米 / 澎湃OS / MIUI，供权限面板给出对应设置路径。
 */
object RomInfo {
    /** 是否小米系设备（Xiaomi / Redmi / POCO） */
    val isXiaomi: Boolean by lazy {
        val m = Build.MANUFACTURER ?: ""
        m.contains("xiaomi", true) || m.contains("redmi", true) || m.contains("poco", true)
    }

    /** 澎湃OS 版本名（ro.mi.os.version.name），MIUI 老版本可能取 incremental。取不到返回 null。 */
    val miVersion: String? by lazy {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            val name = get.invoke(null, "ro.mi.os.version.name") as? String
            if (!name.isNullOrBlank()) {
                name
            } else {
                val inc = get.invoke(null, "ro.mi.os.version.incremental") as? String
                if (!inc.isNullOrBlank()) inc else null
            }
        } catch (e: Throwable) {
            null
        }
    }

    /** 是否为澎湃OS（HyperOS） */
    val isHyperOs: Boolean by lazy { !miVersion.isNullOrBlank() }

    /** 澎湃OS 大版本，如 "OS4"；无法识别返回 null。 */
    val hyperOsMajor: String? by lazy {
        val v = miVersion ?: return@lazy null
        val m = Regex("OS(\\d+)", RegexOption.IGNORE_CASE).find(v)
        m?.groupValues?.get(1)?.let { "OS$it" }
    }

    /** 是否为澎湃OS 4 及以上 */
    val isHyperOs4: Boolean by lazy {
        val major = hyperOsMajor ?: return@lazy false
        val n = major.removePrefix("OS").toIntOrNull() ?: return@lazy false
        n >= 4
    }

    /** 面板展示用的 ROM 名称 */
    val displayName: String by lazy {
        when {
            isHyperOs -> {
                val ver = hyperOsMajor?.removePrefix("OS")?.let { " $it" } ?: ""
                "小米澎湃OS$ver（${miVersion}）"
            }
            isXiaomi -> "MIUI"
            else -> "通用安卓 ${Build.VERSION.RELEASE}"
        }
    }
}
