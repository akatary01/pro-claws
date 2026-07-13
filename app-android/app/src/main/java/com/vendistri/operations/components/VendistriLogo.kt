package com.vendistri.operations.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.vendistri.operations.R

@Composable
fun VendistriLogo(
    modifier: Modifier = Modifier,
    contentDescription: String = "Vendistri"
) {
    Image(
        painter = painterResource(id = R.drawable.vendistri_logo),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun VendistriMark(
    modifier: Modifier = Modifier,
    contentDescription: String = "Vendistri"
) {
    Image(
        painter = painterResource(id = R.drawable.vendistri_v_logo),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
