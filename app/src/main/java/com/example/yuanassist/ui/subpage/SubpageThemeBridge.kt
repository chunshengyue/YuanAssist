package com.example.yuanassist.ui.subpage

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.yuanassist.R
import com.example.yuanassist.ui.main.theme.MainShellTheme

@Composable
fun SubpageThemeBridge(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    MainShellTheme {
        Box(modifier = modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.background_stretch_9x21),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                alignment = Alignment.TopCenter,
                contentScale = ContentScale.FillWidth,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun SubpageSurfaceColor() = MaterialTheme.colorScheme.surface
