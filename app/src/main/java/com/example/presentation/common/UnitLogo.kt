package com.example.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun UnitLogo(
    logoBase64: String? = null,
    modifier: Modifier = Modifier.size(48.dp),
    contentDescription: String? = null
) {
    AppLogo(
        modifier = modifier,
        contentDescription = contentDescription,
        logoBase64 = logoBase64
    )
}
