package com.presensi.incinerator.screen

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
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
import com.presensi.incinerator.R
import com.presensi.incinerator.receiver.ConnectivityReceiver
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun WebScreen() {
    val context = LocalContext.current as Activity
    var isConnected by remember { mutableStateOf(checkNetworkConnection(context)) }
    var showExitDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val webView = remember { WebView(context) }
    val url = context.getString(R.string.base_url)

    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val resultUris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            fileChooserCallback?.onReceiveValue(resultUris)
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
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
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE // Nonaktifkan cache
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setGeolocationEnabled(true)
                    clearCache(true) // Bersihkan cache
                    clearHistory()   // Hapus riwayat

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun processBlobData(base64Data: String) {
                            saveBlobDataAsPDF(context, base64Data)
                        }
                    }, "AndroidBlobDownloader")

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            fileChooserCallback = filePathCallback

                            // Buat intent untuk memilih file
                            val fileIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*" // Tampilkan semua jenis file
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }

                            // Gunakan launcher untuk membuka file chooser
                            return try {
                                fileChooserLauncher.launch(
                                    Intent.createChooser(
                                        fileIntent,
                                        "Pilih File"
                                    )
                                )
                                true
                            } catch (e: Exception) {
                                fileChooserCallback = null
                                Toast.makeText(
                                    context,
                                    "Tidak dapat membuka file chooser",
                                    Toast.LENGTH_SHORT
                                ).show()
                                false
                            }
                        }
                        override fun onPermissionRequest(request: PermissionRequest) {
                            request.grant(request.resources)
                        }
                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: GeolocationPermissions.Callback?
                        ) {
                            callback?.invoke(origin, true, false)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url.toString()

                            // Periksa apakah URL tidak dimulai dengan http:// atau https://
                            return if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                try {
                                    // Buat intent dari URL
                                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                    // Periksa apakah aplikasi yang sesuai tersedia
                                    if (intent.resolveActivity(view?.context?.packageManager!!) != null) {
                                        view.context.startActivity(intent)
                                    } else {
                                        // Tampilkan pesan jika aplikasi tidak ditemukan
                                        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                        if (!fallbackUrl.isNullOrEmpty()) {
                                            view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
                                        } else {
                                            Toast.makeText(view.context, "Aplikasi tidak ditemukan", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    true // URL sudah ditangani
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(view?.context, "Tidak dapat membuka URL", Toast.LENGTH_SHORT).show()
                                    false
                                }
                            } else {
                                // Jika URL dimulai dengan http:// atau https://, biarkan WebView menangani
                                false
                            }
                        }

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

                    webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                        try {
                            if (url.startsWith("blob:")) {
                                // Tangani file `blob:` menggunakan JavaScript
                                webView.evaluateJavascript("handleBlobURL('$url')", null)
                            } else {
                                // Tangani unduhan dengan URL biasa menggunakan DownloadManager
                                val request = DownloadManager.Request(Uri.parse(url)).apply {
                                    setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                                    setDescription("Mengunduh file...")
                                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    setAllowedOverMetered(true)
                                    setAllowedOverRoaming(true)
                                    setDestinationInExternalPublicDir(
                                        Environment.DIRECTORY_DOWNLOADS,
                                        URLUtil.guessFileName(url, contentDisposition, mimeType)
                                    )
                                }

                                val downloadManager =
                                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                downloadManager.enqueue(request)

                                Toast.makeText(context, "Unduhan dimulai...", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Terjadi kesalahan saat mengunduh file", Toast.LENGTH_SHORT).show()
                            e.printStackTrace()
                        }
                    }

                    loadUrl("$url?timestamp=${System.currentTimeMillis()}")
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
    // Ambil nama aplikasi dari resource string
    val appName = context.getString(R.string.app_name)

    // Path folder penyimpanan: /Download/AppName/
    val folderPath = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        appName
    )
    if (!folderPath.exists()) folderPath.mkdirs()

    // Format tanggal dan waktu
    val currentDate = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())
    val currentTime = SimpleDateFormat("HH.mm.ss", Locale.getDefault()).format(Date())

    // Nama file
    val fileName = "Invoice, $currentDate pada $currentTime.pdf"
    val file = File(folderPath, fileName)

    try {
        val decodedData = Base64.decode(base64Data, Base64.DEFAULT)
        FileOutputStream(file).use { it.write(decodedData) }
        showNotification(context, "Unduh Berhasil", "Ketuk Untuk Membuka Folder", true, file)
        showToast(context, "File berhasil diunduh dengan nama: $fileName", true)
    } catch (e: Exception) {
        e.printStackTrace()
        showNotification(context, "Unduh Gagal", "Terjadi kesalahan saat menyimpan file", false)
        showToast(context, "Gagal menyimpan file", false)
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

    // Intent untuk membuka folder tempat file disimpan
    val folderIntent = Intent(Intent.ACTION_VIEW).apply {
        file?.parentFile?.let { folder ->
            val folderUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                folder
            )
            setDataAndType(folderUri, "resource/folder") // Format folder
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    // PendingIntent untuk notifikasi
    val pendingIntent = PendingIntent.getActivity(
        context,
        System.currentTimeMillis().toInt(),
        folderIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Membuat notifikasi
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent) // Arahkan ke folder tempat file disimpan
        .setAutoCancel(true) // Hapus notifikasi setelah diklik
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

