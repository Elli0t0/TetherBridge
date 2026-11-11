package io.github.elli0t0.tetherbridge

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

data class UiState(
    val device: DeviceInfo,
    val installed: Boolean? = null,
    val installedVersion: String? = null,
    val running: Boolean? = null,
    val permissionGranted: Boolean? = null,
    val serverVersion: String? = null,
    val privilegeUid: String? = null,
    val statusError: String? = null,
    val testResult: String? = null,
    val testOk: Boolean? = null,
    val bound: Boolean = false,
    val busy: Boolean = false,
)

class ShizukuViewModel(application: Application) : AndroidViewModel(application) {

    val logLines: StateFlow<List<String>> = AppLog.lines

    private val _state = MutableStateFlow(UiState(device = DeviceInfo.read()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        AppLog.d("onRequestPermissionResult: requestCode=$requestCode grantResult=$grantResult granted=$granted")
        if (!granted) {
            try {
                val rationale = Shizuku.shouldShowRequestPermissionRationale()
                AppLog.d("onRequestPermissionResult: shouldShowRequestPermissionRationale()=$rationale")
            } catch (t: Throwable) {
                AppLog.e("onRequestPermissionResult: shouldShowRequestPermissionRationale() FAILED ${t.javaClass.simpleName}: ${t.message}", t)
            }
        }
        refresh()
    }

    init {
        AppLog.d("ShizukuViewModel: init, registering permission listener")
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (t: Throwable) {
            AppLog.e("ShizukuViewModel: addRequestPermissionResultListener FAILED ${t.javaClass.simpleName}: ${t.message}", t)
        }
        refresh()
    }

    override fun onCleared() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (t: Throwable) {
            AppLog.e("ShizukuViewModel: removeRequestPermissionResultListener FAILED ${t.javaClass.simpleName}: ${t.message}", t)
        } finally {
            super.onCleared()
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(busy = true)
            val status = try {
                ShizukuClient.readStatus(getApplication())
            } catch (t: Throwable) {
                AppLog.e("refresh: readStatus FAILED ${t.javaClass.simpleName}: ${t.message}", t)
                _state.value = _state.value.copy(busy = false, statusError = "readStatus threw ${t.javaClass.simpleName}: ${t.message}")
                return@launch
            }
            _state.value = _state.value.copy(
                installed = status.installed,
                installedVersion = status.installedVersion,
                running = status.running,
                permissionGranted = status.permissionGranted,
                serverVersion = status.serverVersion,
                privilegeUid = status.privilegeUid,
                statusError = status.error,
                bound = ShizukuClient.isBound,
                busy = false,
            )
        }
    }

    fun requestPermission() {
        AppLog.d("requestPermission: tapped")
        viewModelScope.launch {
            try {
                if (Shizuku.isPreV11()) {
                    AppLog.e("requestPermission: Shizuku server is pre-v11, cannot request permission")
                    return@launch
                }
            } catch (t: Throwable) {
                AppLog.e("requestPermission: isPreV11() FAILED ${t.javaClass.simpleName}: ${t.message}", t)
                return@launch
            }
            try {
                if (!Shizuku.pingBinder()) {
                    AppLog.e("requestPermission: binder not alive (pingBinder()=false) — start Shizuku first")
                    return@launch
                }
            } catch (t: Throwable) {
                AppLog.e("requestPermission: pingBinder() FAILED ${t.javaClass.simpleName}: ${t.message}", t)
                return@launch
            }
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    AppLog.d("requestPermission: already granted")
                    refresh()
                    return@launch
                }
            } catch (t: Throwable) {
                AppLog.e("requestPermission: checkSelfPermission() FAILED ${t.javaClass.simpleName}: ${t.message}", t)
                return@launch
            }
            try {
                Shizuku.requestPermission(REQUEST_PERMISSION_CODE)
                AppLog.d("requestPermission: requestPermission($REQUEST_PERMISSION_CODE) sent, waiting for dialog result")
            } catch (t: Throwable) {
                AppLog.e("requestPermission: requestPermission() FAILED ${t.javaClass.simpleName}: ${t.message}", t)
            }
        }
    }

    fun testService() {
        AppLog.d("testService: tapped")
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, testResult = null, testOk = null)
            val result = try {
                ShizukuClient.testService(getApplication())
            } catch (t: Throwable) {
                AppLog.e("testService: ShizukuClient.testService FAILED ${t.javaClass.simpleName}: ${t.message}", t)
                Result.failure<String>(IllegalStateException("testService threw ${t.javaClass.simpleName}: ${t.message}"))
            }
            val text = result.getOrNull()
                ?: result.exceptionOrNull()?.message
                ?: "Unknown failure (no result and no exception)"
            _state.value = _state.value.copy(
                testResult = text,
                testOk = result.isSuccess,
                bound = ShizukuClient.isBound,
                busy = false,
            )
        }
    }

    fun stopService() {
        AppLog.d("stopService: tapped")
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                ShizukuClient.stopService()
            } catch (t: Throwable) {
                AppLog.e("stopService: FAILED ${t.javaClass.simpleName}: ${t.message}", t)
                "Stop FAILED: ${t.javaClass.simpleName}: ${t.message}"
            }
            _state.value = _state.value.copy(
                testResult = message,
                testOk = null,
                bound = ShizukuClient.isBound,
            )
        }
    }

    fun clearLog() {
        AppLog.clear()
    }

    companion object {
        const val REQUEST_PERMISSION_CODE = 1001
    }
}
