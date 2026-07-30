package com.example.fileshare

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSelectFiles = findViewById<Button>(R.id.btnSelectFiles)
        val btnShareApp = findViewById<Button>(R.id.btnShareApp)

        // اختيار ملفات من الجهاز ومشاركتها عبر البلوتوث
        btnSelectFiles?.setOnClickListener {
            openFilePicker()
        }

        // استخراج المشاركة لأي تطبيق مثبت
        btnShareApp?.setOnClickListener {
            shareInstalledApp("com.example.fileshare") // يمكن اختيار أي تطبيق
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "اختر الملفات للمشاركة"), 101)
    }

    private fun shareInstalledApp(packageName: String) {
        try {
            val appInfo: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val appFile = File(appInfo.publicSourceDir)

            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                appFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                setPackage("com.android.bluetooth") // توجيه مباشر للبلوتوث
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة التطبيق عبر البلوتوث"))
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر مشاركة التطبيق: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}