package com.foodrescue.app.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foodrescue.app.feature.auth.presentation.AuthRoute

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    AuthRoute(modifier = modifier)
}
