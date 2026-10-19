package com.moodtunes.app.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.moodtunes.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            Timber.i("AppUpdateManager: Starting APK download from: %s", apkUrl)
            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "MoodTunes/${BuildConfig.VERSION_NAME} (Android; Music Player App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("AppUpdateManager: HTTP download failed with status code %d", response.code)
                    return@withContext null
                }
                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()

                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(updateDir, "MoodTunes_Update.apk")
                if (apkFile.exists()) apkFile.delete()

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)
                val buffer = ByteArray(32768)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                        withContext(Dispatchers.Main) {
                            onProgress(progress)
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                apkFile.setReadable(true, false)
                Timber.i("AppUpdateManager: APK download finished (%d bytes) at %s", apkFile.length(), apkFile.absolutePath)
                return@withContext apkFile
            }
        } catch (e: Exception) {
            Timber.e(e, "AppUpdateManager: Failed to download update APK")
            return@withContext null
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Timber.w("AppUpdateManager: REQUEST_INSTALL_PACKAGES not granted yet, requesting permission")
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Timber.i("AppUpdateManager: Launched package installer for %s", apkUri)
        } catch (e: Exception) {
            Timber.e(e, "AppUpdateManager: Failed to launch package installer directly, falling back to browser")
            openBrowserFallback(context)
        }
    }

    fun openBrowserFallback(
        context: Context,
        fallbackUrl: String = "https://github.com/cyberlog69/MoodTunes/releases/latest"
    ) {
        try {
            Toast.makeText(context, "Opening latest release page...", Toast.LENGTH_LONG).show()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "AppUpdateManager: Failed to open browser fallback")
        }
    }
}
