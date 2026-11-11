package io.github.elli0t0.tetherbridge

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

data class DeviceInfo(
    val androidRelease: String,
    val sdkInt: Int,
    val abis: String,
    val manufacturer: String,
    val model: String,
) {
    companion object {
        fun read(): DeviceInfo {
            return DeviceInfo(
                androidRelease = Build.VERSION.RELEASE ?: "unknown",
                sdkInt = Build.VERSION.SDK_INT,
                abis = Build.SUPPORTED_ABIS.joinToString(", "),
                manufacturer = Build.MANUFACTURER ?: "unknown",
                model = Build.MODEL ?: "unknown",
            )
        }
    }
}

data class ShizukuStatus(
    val installed: Boolean,
    val installedVersion: String?,
    val running: Boolean,
    val permissionGranted: Boolean?,
    val serverVersion: String?,
    val privilegeUid: String?,
    val error: String?,
)

/** One bound UserService session, kept so the Stop button can destroy + unbind it. */
private data class TestSession(
    val args: Shizuku.UserServiceArgs,
    val conn: ServiceConnection,
    val service: ITetherService,
)

object ShizukuClient {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val BIND_TIMEOUT_MS = 15_000L
    const val SERVICE_TAG = "tetherbridge"
    const val SERVICE_VERSION = 1

    @Volatile
    private var session: TestSession? = null

    val isBound: Boolean
        get() = session != null

    fun readStatus(context: Context): ShizukuStatus {
        AppLog.d("readStatus: checking Shizuku")
        var installed = false
        var installedVersion: String? = null
        try {
            val info = getPackageInfo(context)
            installed = info != null
            installedVersion = info?.versionName
            AppLog.d("readStatus: installed=$installed version=$installedVersion")
        } catch (t: Throwable) {
            AppLog.e("readStatus: getPackageInfo FAILED ${t.javaClass.simpleName}: ${t.message}", t)
            installed = false
        }

        var running = false
        var statusError: String? = null
        try {
            running = Shizuku.pingBinder()
            AppLog.d("readStatus: pingBinder()=$running")
        } catch (t: Throwable) {
            statusError = "pingBinder() threw ${t.javaClass.simpleName}: ${t.message}"
            AppLog.e("readStatus: $statusError", t)
        }

        var permissionGranted: Boolean? = null
        if (running) {
            try {
                val result = Shizuku.checkSelfPermission()
                permissionGranted = result == PackageManager.PERMISSION_GRANTED
                AppLog.d("readStatus: checkSelfPermission()=$result granted=$permissionGranted")
            } catch (t: Throwable) {
                statusError = "checkSelfPermission() threw ${t.javaClass.simpleName}: ${t.message}"
                AppLog.e("readStatus: $statusError", t)
            }
        } else {
            AppLog.d("readStatus: binder not alive, skipping checkSelfPermission()")
        }

        var serverVersion: String? = null
        var privilegeUid: String? = null
        if (running) {
            try {
                serverVersion = Shizuku.getVersion().toString()
                AppLog.d("readStatus: getVersion()=$serverVersion")
            } catch (t: Throwable) {
                AppLog.e("readStatus: getVersion() FAILED ${t.javaClass.simpleName}: ${t.message}", t)
            }
            if (permissionGranted == true) {
                try {
                    val uid = Shizuku.getUid()
                    privilegeUid = "$uid (${if (uid == 0) "root" else if (uid == 2000) "shell" else "other"})"
                    AppLog.d("readStatus: getUid()=$privilegeUid")
                } catch (t: Throwable) {
                    AppLog.e("readStatus: getUid() FAILED ${t.javaClass.simpleName}: ${t.message}", t)
                }
            }
        }

        return ShizukuStatus(
            installed = installed,
            installedVersion = installedVersion,
            running = running,
            permissionGranted = permissionGranted,
            serverVersion = serverVersion,
            privilegeUid = privilegeUid,
            error = statusError,
        )
    }

    private fun getPackageInfo(context: Context): android.content.pm.PackageInfo? {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(SHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(SHIZUKU_PACKAGE, 0)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            AppLog.d("getPackageInfo: $SHIZUKU_PACKAGE not found (${e.javaClass.simpleName})")
            null
        }
    }

    /**
     * Binds the UserService and calls getInfo(). Every failure returns a
     * Result.failure with the REAL reason — never a generic message.
     * In particular a bind timeout is reported as a timeout, with the most
     * likely causes listed, instead of pretending the service answered.
     */
    suspend fun testService(context: Context): Result<String> {
        AppLog.d("testService: starting")
        try {
            if (Shizuku.isPreV11()) {
                return fail("Shizuku server is older than v11 (isPreV11() = true). UserService needs Shizuku v11 or newer — update the Shizuku app.")
            }
        } catch (t: Throwable) {
            return fail("isPreV11() threw ${t.javaClass.simpleName}: ${t.message}", t)
        }

        val alive = try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            return fail("pingBinder() threw ${t.javaClass.simpleName}: ${t.message}", t)
        }
        AppLog.d("testService: pingBinder()=$alive")
        if (!alive) {
            return fail("Shizuku binder is not alive (pingBinder() = false). Open the Shizuku app and start it (wireless debugging / adb), then tap Refresh and Test again.")
        }

        val permission = try {
            Shizuku.checkSelfPermission()
        } catch (t: Throwable) {
            return fail("checkSelfPermission() threw ${t.javaClass.simpleName}: ${t.message}", t)
        }
        AppLog.d("testService: checkSelfPermission()=$permission")
        if (permission != PackageManager.PERMISSION_GRANTED) {
            val rationale = try {
                Shizuku.shouldShowRequestPermissionRationale()
            } catch (t: Throwable) {
                AppLog.e("testService: shouldShowRequestPermissionRationale() FAILED ${t.javaClass.simpleName}: ${t.message}", t)
                null
            }
            return fail("Permission not granted (checkSelfPermission()=$permission, expected ${PackageManager.PERMISSION_GRANTED}). Tap 'Request permission' first. shouldShowRequestPermissionRationale()=$rationale (true means Shizuku was told 'Deny and don't ask again' — re-allow it inside the Shizuku app).")
        }

        val args = try {
            Shizuku.UserServiceArgs(
                ComponentName(context.packageName, TetherUserService::class.java.name),
            ).daemon(false).processNameSuffix("tether").version(SERVICE_VERSION).tag(SERVICE_TAG)
        } catch (t: Throwable) {
            return fail("Building UserServiceArgs threw ${t.javaClass.simpleName}: ${t.message}", t)
        }
        AppLog.d("testService: UserServiceArgs built (tag=$SERVICE_TAG version=$SERVICE_VERSION daemon=false suffix=tether)")

        val deferred = CompletableDeferred<IBinder>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                AppLog.d("testService: onServiceConnected name=$name binder=$service")
                if (service != null && !deferred.isCompleted) {
                    deferred.complete(service)
                } else if (service == null) {
                    AppLog.e("testService: onServiceConnected called with NULL binder")
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(IllegalStateException("onServiceConnected delivered a null binder"))
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                AppLog.e("testService: onServiceDisconnected name=$name (service process died or was stopped)")
            }
        }

        try {
            Shizuku.bindUserService(args, conn)
            AppLog.d("testService: bindUserService() returned, waiting up to ${BIND_TIMEOUT_MS}ms for onServiceConnected")
        } catch (t: Throwable) {
            return fail("bindUserService() threw ${t.javaClass.simpleName}: ${t.message}", t)
        }

        val binder = withTimeoutOrNull(BIND_TIMEOUT_MS) {
            try {
                deferred.await()
            } catch (t: Throwable) {
                AppLog.e("testService: deferred.await() FAILED ${t.javaClass.simpleName}: ${t.message}", t)
                null
            }
        }
        if (binder == null) {
            try {
                Shizuku.unbindUserService(args, conn, true)
                AppLog.d("testService: unbound after timeout")
            } catch (t: Throwable) {
                AppLog.e("testService: unbindUserService after timeout FAILED ${t.javaClass.simpleName}: ${t.message}", t)
            }
            return fail("TIMEOUT: bindUserService() did not call onServiceConnected within ${BIND_TIMEOUT_MS}ms. No binder was received, so getInfo() was never called. Likely causes: (1) the Shizuku server is busy or its service process failed to start — check logcat for the Shizuku server and for this app; (2) the UserService class could not be instantiated (constructor crash — the log above shows which constructor ran); (3) Shizuku was killed in the background. Start Shizuku again and retry.")
        }

        val service = try {
            ITetherService.Stub.asInterface(binder)
        } catch (t: Throwable) {
            return fail("ITetherService.Stub.asInterface() threw ${t.javaClass.simpleName}: ${t.message}", t)
        }

        val info = try {
            service.getInfo()
        } catch (t: Throwable) {
            return fail("AIDL getInfo() threw ${t.javaClass.simpleName}: ${t.message}", t)
        }
        AppLog.d("testService: getInfo() returned [$info]")
        session = TestSession(args, conn, service)
        return Result.success(info)
    }

    /** Sends destroy() to the service (it exits its own process) then unbinds. */
    fun stopService(): String {
        val current = session ?: return "No UserService session is bound — nothing to stop."
        session = null
        val destroyResult = try {
            current.service.destroy()
            "destroy() sent (service exits its process via System.exit(0))"
        } catch (t: Throwable) {
            AppLog.e("stopService: destroy() FAILED ${t.javaClass.simpleName}: ${t.message}", t)
            "destroy() FAILED: ${t.javaClass.simpleName}: ${t.message}"
        }
        val unbindResult = try {
            Shizuku.unbindUserService(current.args, current.conn, true)
            "unbindUserService() done"
        } catch (t: Throwable) {
            AppLog.e("stopService: unbindUserService() FAILED ${t.javaClass.simpleName}: ${t.message}", t)
            "unbindUserService() FAILED: ${t.javaClass.simpleName}: ${t.message}"
        }
        val msg = "Stop: $destroyResult; $unbindResult. Note: unbinding alone does NOT kill the service process — destroy() is what exits it."
        AppLog.d(msg)
        return msg
    }

    private fun fail(message: String, t: Throwable? = null): Result<String> {
        AppLog.e("testService FAILED: $message", t)
        return Result.failure(IllegalStateException(message))
    }
}
