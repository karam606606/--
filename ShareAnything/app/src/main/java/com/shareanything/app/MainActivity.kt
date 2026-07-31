package com.shareanything.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : ComponentActivity() {

    private var activeServer: LocalShareServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShareAnythingScreen(
                        onServerCreated = { activeServer = it }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeServer?.stop()
    }
}

@Composable
fun ShareAnythingScreen(onServerCreated: (LocalShareServer) -> Unit) {
    val context = LocalContext.current

    var picked by remember { mutableStateOf<PickedFile?>(null) }
    var localFile by remember { mutableStateOf<File?>(null) }
    var serverPort by remember { mutableStateOf<Int?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var shareUrl by remember { mutableStateOf<String?>(null) }
    var server by remember { mutableStateOf<LocalShareServer?>(null) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Stop any previous local-network sharing session first.
            server?.stop()
            server = null
            serverPort = null
            qrBitmap = null
            shareUrl = null

            val meta = FileUtils.readMeta(context, uri)
            picked = meta
            localFile = FileUtils.copyToShareCache(context, uri, meta.name)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "شارك أي حاجة",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "اختار ملف وشاركه بأكتر من طريقة",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        Button(
            onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.InsertDriveFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("اختار ملف")
        }

        picked?.let { file ->
            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(file.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        FileUtils.humanReadableSize(file.sizeBytes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    localFile?.let { f ->
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", f
                        )
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = context.contentResolver.getType(uri) ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "شارك عن طريق"))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("شارك عبر التطبيقات (واتساب، تليجرام...)")
            }

            Spacer(Modifier.height(12.dp))

            if (serverPort == null) {
                OutlinedButton(
                    onClick = {
                        localFile?.let { f ->
                            val s = LocalShareServer(f, file.name)
                            val port = s.start()
                            server = s
                            serverPort = port
                            onServerCreated(s)

                            val ip = LocalShareServer.getLocalIpAddress()
                            val url = if (ip != null) "http://$ip:$port/" else null
                            shareUrl = url
                            if (url != null) {
                                qrBitmap = generateQrCodeBitmap(url)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("شارك عبر الواي فاي المحلي")
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("جاهز للتنزيل من أي جهاز على نفس شبكة الواي فاي", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        qrBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "QR code",
                                modifier = Modifier.size(220.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        shareUrl?.let { url ->
                            Text(url, textAlign = TextAlign.Center)
                        } ?: Text(
                            "تعذر تحديد عنوان الشبكة، تأكد إنك متصل بالواي فاي",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = {
                            server?.stop()
                            server = null
                            serverPort = null
                            qrBitmap = null
                            shareUrl = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("إيقاف المشاركة")
                        }
                    }
                }
            }
        }
    }
}
