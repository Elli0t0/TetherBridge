package io.github.elli0t0.tetherbridge

import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Runs in a separate process as shell (uid 2000) or root (uid 0), started by
 * the Shizuku server — NOT as a normal app process. That is why this class
 * does not touch ContentResolver, registerReceiver, or app files: the
 * Shizuku README says those do not work here.
 *
 * Shizuku v13 tries the Context constructor first, older servers use the
 * no-arg one. Both are kept on purpose.
 */
class TetherUserService : ITetherService.Stub {

    constructor() : super() {
        AppLog.d("TetherUserService: no-arg constructor (uid=${safeUid()})")
    }

    constructor(context: Context) : super() {
        val pkg = try {
            context.packageName
        } catch (t: Throwable) {
            "unavailable: ${t.javaClass.simpleName}: ${t.message}"
        }
        AppLog.d("TetherUserService: Context constructor, package=$pkg (uid=${safeUid()})")
    }

    override fun getInfo(): String {
        AppLog.d("TetherUserService.getInfo: called")
        return try {
            val uid = Process.myUid()
            val who = when (uid) {
                0 -> "root"
                2000 -> "shell"
                else -> "other"
            }
            val info = "uid=$uid ($who) | sdk=${Build.VERSION.SDK_INT} " +
                "| android=${Build.VERSION.RELEASE} " +
                "| manufacturer=${Build.MANUFACTURER} | model=${Build.MODEL} " +
                "| abis=${Build.SUPPORTED_ABIS.joinToString(",")} " +
                "| arch=${System.getProperty("os.arch")}"
            AppLog.d("TetherUserService.getInfo: returning [$info]")
            info
        } catch (t: Throwable) {
            AppLog.e("TetherUserService.getInfo: FAILED ${t.javaClass.simpleName}: ${t.message}", t)
            "ERROR in UserService.getInfo: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    override fun destroy() {
        AppLog.d("TetherUserService.destroy: called, exiting process")
        try {
            System.exit(0)
        } catch (t: Throwable) {
            AppLog.e("TetherUserService.destroy: System.exit FAILED ${t.javaClass.simpleName}: ${t.message}", t)
        }
    }

    private fun safeUid(): String {
        return try {
            Process.myUid().toString()
        } catch (t: Throwable) {
            "unknown (${t.javaClass.simpleName})"
        }
    }
}
