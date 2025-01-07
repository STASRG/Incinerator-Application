package com.stasrg.incinerator.screen

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import java.io.IOException
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
    val fileChooserCallback = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val fileUri: Uri? = data?.data ?: cameraUri


            val finalUri = fileUri?.let { uri ->
                if (data?.data != null) {
                    val localFile = createTempImageFileFromUri(context, uri)
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", localFile)
                } else uri
            }

            fileChooserCallback.value?.onReceiveValue(finalUri?.let { arrayOf(it) })
        } else {
            fileChooserCallback.value?.onReceiveValue(null)
        }
        fileChooserCallback.value = null
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
                            fileChooserCallback.value = filePathCallback

                            // Buat intent untuk galeri
                            val galleryIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "image/*"
                            }

                            // Buat intent untuk kamera
                            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                            val photoFile: File? = try {
                                createImageFile(context).also {
                                    // Simpan URI hasilnya ke state cameraUri
                                    cameraUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        it
                                    )
                                }
                            } catch (ex: IOException) {
                                ex.printStackTrace()
                                null
                            }

                            photoFile?.let {
                                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
                            }

                            // Gabungkan intent kamera dan galeri
                            val intentChooser = Intent.createChooser(galleryIntent, "Pilih Sumber Gambar").apply {
                                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                            }

                            try {
                                fileChooserLauncher.launch(intentChooser)
                            } catch (e: ActivityNotFoundException) {
                                fileChooserCallback.value = null
                                Toast.makeText(context, "Tidak dapat membuka file chooser", Toast.LENGTH_SHORT).show()
                                return false
                            }

                            return true
                        }

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

// Fungsi untuk menyalin file dari URI pemilih media ke file lokal
fun createTempImageFileFromUri(context: Context, uri: Uri): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val tempFile = File.createTempFile(
        "temp_image_${System.currentTimeMillis()}",
        ".jpg",
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    )
    inputStream?.use { input ->
        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
    }
    return tempFile
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

@Throws(IOException::class)
fun createImageFile(context: Context): File {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile(
        "JPEG_${timeStamp}_", /* prefix */
        ".jpg",              /* suffix */
        storageDir           /* directory */
    )
}