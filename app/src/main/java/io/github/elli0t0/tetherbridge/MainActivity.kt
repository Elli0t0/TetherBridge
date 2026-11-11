package io.github.elli0t0.tetherbridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d("MainActivity.onCreate")
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this)[ShizukuViewModel::class.java]
        setContent {
            MaterialTheme {
                Stage1Screen(viewModel)
            }
        }
    }
}

@Composable
private fun Stage1Screen(viewModel: ShizukuViewModel) {
    val state by viewModel.state.collectAsState()
    val logLines by viewModel.logLines.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        Text("TetherBridge — Stage 1", fontSize = 22.sp)
        Text("Shizuku foundation (no tethering yet)", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Shizuku", fontSize = 16.sp)
        StatusRow("Installed", state.installed?.let { if (it) "yes" + (state.installedVersion?.let { v -> " ($v)" } ?: "") else "no — install Shizuku (moe.shizuku.privileged.api)" } ?: "…")
        StatusRow("Running", state.running?.let { if (it) "yes (binder alive)" else "no — start Shizuku first" } ?: "…")
        StatusRow("Permission granted", state.permissionGranted?.let { if (it) "yes" else "no" } ?: "…")
        StatusRow("Server version", state.serverVersion ?: "—")
        StatusRow("Privilege uid", state.privilegeUid ?: "—")
        state.statusError?.let { StatusRow("Status error", it) }

        Spacer(modifier = Modifier.height(12.dp))
        Text("This phone", fontSize = 16.sp)
        StatusRow("Android", "${state.device.androidRelease} (SDK ${state.device.sdkInt})")
        StatusRow("ABI", state.device.abis)
        StatusRow("Manufacturer", state.device.manufacturer)
        StatusRow("Model", state.device.model)

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.refresh() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refresh status")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.requestPermission() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Request permission")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.testService() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test UserService")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.stopService() },
            enabled = !state.busy && state.bound,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Stop UserService")
        }

        state.testResult?.let { result ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(if (state.testOk == true) "Test result: OK" else "Test result", fontSize = 16.sp)
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    result,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Log (tag: App)", fontSize = 16.sp)
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (logLines.isEmpty()) "(no log lines yet)" else logLines.joinToString("\n"),
                modifier = Modifier
                    .padding(12.dp)
                    .heightIn(min = 120.dp, max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val text = logLines.joinToString("\n")
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("TetherBridge log", text))
                Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                AppLog.d("Log copied to clipboard (${logLines.size} lines)")
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Copy log")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.clearLog() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear log")
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Text("$label: $value", fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
}
