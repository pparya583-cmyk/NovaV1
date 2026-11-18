package id.novamusic.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.*
import id.novamusic.app.ui.NovaRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: NovaViewModel = viewModel(factory = NovaViewModel.Factory(application))
            val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
            // Android 13+ hides the media-playback notification unless POST_NOTIFICATIONS is granted.
            val wanted = buildList {
                add(audioPermission)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                // The result only lists permissions that were requested, so check the real state.
                val audioGranted = ContextCompat.checkSelfPermission(this@MainActivity, audioPermission) == PackageManager.PERMISSION_GRANTED
                if (audioGranted) vm.refreshLibrary() else vm.message("Izin audio diperlukan untuk membaca musik lokal")
            }
            LaunchedEffect(Unit) {
                val missing = wanted.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
            }
            NovaRoot(vm)
        }
    }
}
