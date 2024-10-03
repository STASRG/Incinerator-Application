package com.stasrg.incinerator.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background // Import the background modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Import Color class
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.stasrg.incinerator.R
import com.stasrg.incinerator.navigation.Screen
import com.stasrg.incinerator.ui.theme.IncineratorTheme
import kotlinx.coroutines.delay

@Composable
fun AwalScreen(navController: NavController) {
    LaunchedEffect(Unit) {
        delay(1000)
        navController.navigate(Screen.Web.route)
    }
    LogoScreen() // Menyertakan parameter NavController
}

@Composable
fun LogoScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF15283C)), // Set background color here
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Image
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.mipmap.incinerators),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(vertical = 10.dp)
                    .graphicsLayer {
                        translationX = 10f // Menggeser gambar ke kanan sebanyak 50 dp
                    }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AwalScreenPreview() {
    IncineratorTheme {
        AwalScreen(rememberNavController())
    }
}