package com.stasrg.incinerator.screen

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.stasrg.incinerator.R
import com.stasrg.incinerator.receiver.ConnectivityReceiver
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun WebScreen() {
    val context = LocalContext.current as Activity
    var isConnected by remember { mutableStateOf(checkNetworkConnection(context)) }
    var showExitDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val webView = remember { WebView(context) }
    val url = context.getString(R.string.base_url)

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { _ -> }
    )

    DisposableEffect(Unit) {
        val receiver = ConnectivityReceiver { isConnected = it }
        val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        context.registerReceiver(receiver, intentFilter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    fun checkAndReload() {
        isConnected = checkNetworkConnection(context)
        if (isConnected) {
            webView.reload()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
        isConnected = checkNetworkConnection(context)
    }

    if (!isConnected) {
        NoSignalScreen { checkAndReload() }
        return
    }

    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(Unit) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    showExitDialog = true
                }
            }
        }
        backPressedDispatcher?.addCallback(callback)
        onDispose {
            callback.remove()
        }
    }

    if (showExitDialog) {
        DisplayExitConfirmationDialog(
            onConfirm = { context.finishAffinity() },
            onDismiss = { showExitDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val swipeRefreshLayout = SwipeRefreshLayout(ctx)
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setGeolocationEnabled(true)

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun processBlobData(base64Data: String) {
                            saveBlobDataAsPDF(context, base64Data)
                        }
                    }, "AndroidBlobDownloader")

                    webChromeClient = object : WebChromeClient() {
                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: GeolocationPermissions.Callback?
                        ) {
                            callback?.invoke(origin, true, false)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            injectBlobDownloadHandler(view)
                            swipeRefreshLayout.isRefreshing = false
                            isLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            swipeRefreshLayout.isRefreshing = false
                            isLoading = false
                        }
                    }

                    setDownloadListener { url, _, _, _, _ ->
                        if (url.startsWith("blob:")) {
                            evaluateJavascript("handleBlobURL('$url')", null)
                        } else {
                            downloadPDF(context, url)
                        }
                    }

                    loadUrl(url)
                }

                swipeRefreshLayout.apply {
                    addView(
                        webView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                    setOnRefreshListener {
                        webView.reload()
                        isRefreshing = true
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = {}
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .background(Color.White)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlideImage(
                        model = R.drawable.loading,
                        contentDescription = "Loading",
                        modifier = Modifier.size(300.dp)
                    )
                }
            }
        }
    }
}


fun saveBlobDataAsPDF(context: Context, base64Data: String) {
    val folderName = "Incinerator"
    val fileName = "PDF_${System.currentTimeMillis()}.pdf"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveFileToMediaStore(context, fileName, Base64.decode(base64Data, Base64.DEFAULT))
        showNotification(context, "Unduh Berhasil", "File disimpan di Download/$folderName", true)
        showToast(context, "File berhasil diunduh", true)
    } else {
        val folderPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folderName)
        if (!folderPath.exists()) folderPath.mkdirs()

        val file = File(folderPath, fileName)
        try {
            val decodedData = Base64.decode(base64Data, Base64.DEFAULT)
            FileOutputStream(file).use { it.write(decodedData) }
            showNotification(context, "Unduh Berhasil", "File disimpan di ${file.absolutePath}", true, file)
            showToast(context, "File berhasil diunduh", true)
        } catch (e: Exception) {
            e.printStackTrace()
            showNotification(context, "Unduh Gagal", "Terjadi kesalahan saat menyimpan file", false)
            showToast(context, "Gagal menyimpan file", false)
        }
    }
}

fun downloadPDF(context: Context, fileUrl: String) {
    val folderName = "Incinerator"
    val fileName = "PDF_${System.currentTimeMillis()}.pdf"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Thread {
            try {
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.inputStream.use { input ->
                    val data = input.readBytes()
                    saveFileToMediaStore(context, fileName, data)
                    showNotification(context, "Unduh Berhasil", "File disimpan di Download/$folderName", true)
                    showToast(context, "File berhasil diunduh", true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showNotification(context, "Unduh Gagal", "Terjadi kesalahan saat mengunduh file", false)
                showToast(context, "Gagal mengunduh file", true)
            }
        }.start()
    } else {
        val folderPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folderName)
        if (!folderPath.exists()) folderPath.mkdirs()

        val file = File(folderPath, fileName)
        Thread {
            try {
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                showNotification(context, "Unduh Berhasil", "File disimpan di ${file.absolutePath}", true)
                showToast(context, "File berhasil diunduh", true)
            } catch (e: Exception) {
                e.printStackTrace()
                showNotification(context, "Unduh Gagal", "Terjadi kesalahan saat mengunduh file", false)
                showToast(context, "Gagal mengunduh file", true)
            }
        }.start()
    }
}

fun showNotification(context: Context, title: String, message: String, success: Boolean, file: File? = null) {
    val channelId = "download_channel"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Membuat NotificationChannel untuk Android 8.0+ (API 26+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Unduh File",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifikasi unduhan file"
        }
        notificationManager.createNotificationChannel(channel)
    }

    // Intent untuk membuka file PDF jika berhasil diunduh
    val openFileIntent = file?.let {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    // PendingIntent untuk notifikasi
    val pendingIntent = openFileIntent?.let {
        PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(), // RequestCode unik
            it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Membuat notifikasi
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true) // Hapus notifikasi setelah diklik
        .apply {
            // Tambahkan PendingIntent hanya jika file tersedia
            if (success && pendingIntent != null) {
                setContentIntent(pendingIntent)
            }
        }
        .build()

    // Menampilkan notifikasi
    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
}


// Menampilkan Toast di Bagian Atas
fun showToast(context: Context, message: String, b: Boolean) {
    val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
    toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 200)
    toast.show()
}

fun saveFileToMediaStore(context: Context, fileName: String, data: ByteArray) {
    val resolver = context.contentResolver
    val contentValues = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Incinerator")
    }

    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        resolver.openOutputStream(it).use { outputStream ->
            outputStream?.write(data)
        }
    }
}

fun injectBlobDownloadHandler(webView: WebView?) {
    webView?.evaluateJavascript(
        """
        (function() {
            window.handleBlobURL = function(blobURL) {
                fetch(blobURL).then(res => res.blob()).then(blob => {
                    const reader = new FileReader();
                    reader.onload = function() {
                        AndroidBlobDownloader.processBlobData(reader.result.split(',')[1]);
                    };
                    reader.readAsDataURL(blob);
                });
            };
        })();
        """.trimIndent(), null
    )
}

fun checkNetworkConnection(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
    return when {
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        else -> false
    }
}
