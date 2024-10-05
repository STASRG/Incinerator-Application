package com.stasrg.incinerator.screen

import android.app.Activity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.stasrg.incinerator.R
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate

@Composable
fun NoSignalScreen(onRetry: () -> Unit) {
    var showExitDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) } // Track loading state
    var retryTrigger by remember { mutableStateOf(false) } // Used to trigger the retry process

    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val context = LocalContext.current as Activity

    DisposableEffect(Unit) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog = true
            }
        }

        backPressedDispatcher?.addCallback(callback)

        onDispose {
            callback.remove()
        }
    }

    // This will be triggered when retryTrigger changes to true
    LaunchedEffect(retryTrigger) {
        if (retryTrigger) {
            kotlinx.coroutines.delay(2000) // Simulate retry delay
            onRetry() // Retry logic
            isLoading = false // Stop loading after retry
            retryTrigger = false // Reset trigger
        }
    }

    // Infinite transition for rotating animation
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1000, easing = LinearEasing), // 1 second for a full rotation
            repeatMode = RepeatMode.Restart
        ), label = ""
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.mipmap.no_internet),
            contentDescription = "No Signal",
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(vertical = 10.dp)
        )

        // Box to overlay loading image and button in the same position
        Box(
            modifier = Modifier
                .padding(top = 24.dp)
                .size(150.dp), // Adjust to fit both the button and loading image
            contentAlignment = Alignment.Center // Align content in the center
        ) {
            if (isLoading) {
                Image(
                    painter = painterResource(id = R.drawable.loading_img), // Loading image
                    contentDescription = "Loading",
                    modifier = Modifier
                        .size(150.dp) // Adjust the size of the loading image
                        .rotate(rotationAngle) // Apply rotation animation here
                )
            } else {
                Button(
                    onClick = {
                        isLoading = true // Start loading state
                        retryTrigger = true // Trigger retry logic
                    }
                ) {
                    Text(text = "Try Again")
                }
            }
        }
    }

    if (showExitDialog) {
        DisplayExitConfirmationDialog(
            onConfirm = {
                context.finishAffinity()
            },
            onDismiss = {
                showExitDialog = false
            }
        )
    }
}




