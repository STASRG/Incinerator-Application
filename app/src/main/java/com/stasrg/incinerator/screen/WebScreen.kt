package com.stasrg.incinerator.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.DisposableEffect
@Composable
fun WebScreen(navController: NavHostController) {
    val context = LocalContext.current

    // State for permission request (camera, storage, location)
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // Handle permission results if needed
        }
    )

    // Check for permissions and request if not granted
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissionsToRequest = mutableListOf<String>()

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.CAMERA)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (permissionsToRequest.isNotEmpty()) {
                requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }
    }

    // Initialize WebView
    val webView = remember { WebView(context) }

    // Capture the back press dispatcher
    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    // Handle back button press
    DisposableEffect(Unit) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack() // Navigate back in WebView if possible
                } else {
                    navController.popBackStack() // Navigate back in the app if WebView can't go back
                }
            }
        }

        backPressedDispatcher?.addCallback(callback)

        onDispose {
            callback.remove() // Clean up the callback when the composable is disposed
        }
    }

    AndroidView(
        factory = { ctx ->
            // Create a SwipeRefreshLayout
            val swipeRefreshLayout = SwipeRefreshLayout(ctx)

            // Configure WebView
            webView.apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.setGeolocationEnabled(true)

                // Configure WebChromeClient to handle file upload, camera, etc.
                webChromeClient = object : WebChromeClient() {
                    // For camera and file upload
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        // Handle file chooser for camera or file upload
                        // Implement the necessary logic for file handling here
                        return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
                    }

                    // Handle location permission requests
                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?,
                        callback: GeolocationPermissions.Callback?
                    ) {
                        // Automatically grant location permissions for web
                        callback?.invoke(origin, true, false)
                    }
                }

                // Configure WebViewClient to manage page loading and URL redirection
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        swipeRefreshLayout.isRefreshing = false
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        // Handle page load errors here if needed
                    }
                }

                loadUrl("https://stas.davin.id") // Load URL or a local file as needed
            }

            // Add WebView to SwipeRefreshLayout
            swipeRefreshLayout.addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            swipeRefreshLayout.setOnRefreshListener {
                webView.reload() // Reload the web page on swipe down
            }

            swipeRefreshLayout // Return SwipeRefreshLayout as root view
        },
        modifier = Modifier.fillMaxSize(),
        update = {
            // Update WebView if needed
        }
    )
}