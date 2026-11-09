package com.foodrescue.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodRescuePage(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
            IconButton(onClick = onBack) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Quay lại") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }, bottomBar = {
        footer?.let { Surface(shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 12.dp)) { it() }
        } }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}
@Composable
internal fun FoodRescueCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
