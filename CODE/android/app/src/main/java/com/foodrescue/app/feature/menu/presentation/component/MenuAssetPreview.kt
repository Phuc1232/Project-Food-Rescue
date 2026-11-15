package com.foodrescue.app.feature.menu.presentation.component

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.menu.domain.MenuAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MenuAssetPreview(asset: MenuAsset, onClose: () -> Unit) {
    var page by rememberSaveable(asset.id) { mutableIntStateOf(0) }
    val isPdf = asset.mimeType == "application/pdf"
    val preview by produceState<Result<Bitmap>?>(null, asset.id, page) {
        value = null
        if (isPdf) value = try { Result.success(withContext(Dispatchers.IO) { renderPage(asset.sourceUri, page) }) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }
    Dialog(onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.let { WindowCompat.getInsetsController(it, it.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        } } }
        Scaffold(topBar = { TopAppBar(title = { Text(asset.name, style = MaterialTheme.typography.titleSmall, maxLines = 2) },
            navigationIcon = { IconButton(onClose) { FoodRescueGlyph(FoodRescueIcon.CLOSE, description = "Đóng tài liệu") } }) },
            bottomBar = { if (isPdf) Surface { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton({ page-- }, enabled = page > 0) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Trang trước") }
                Text("${page + 1} / ${asset.pageCount}")
                IconButton({ page++ }, enabled = page + 1 < asset.pageCount) { FoodRescueGlyph(FoodRescueIcon.CHEVRON, description = "Trang sau") }
            } } }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(12.dp), contentAlignment = Alignment.Center) {
                if (!isPdf) MenuPhoto(asset, Modifier.fillMaxSize(), fit = true)
                else when {
                    preview == null -> CircularProgressIndicator()
                    preview?.isFailure == true -> Text("Không mở được trang này. Vui lòng đóng và thử lại.")
                    else -> Image(checkNotNull(preview?.getOrNull()).asImageBitmap(), "${asset.name}, trang ${page + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

private fun renderPage(uri: String, index: Int): Bitmap {
    val file = File(checkNotNull(uri.toUri().path))
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer -> renderer.openPage(index.coerceIn(0, renderer.pageCount - 1)).use { page ->
            val scale = minOf(1200f / page.width, 1800f / page.height)
            createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1)).also {
                it.eraseColor(android.graphics.Color.WHITE)
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        } }
    }
}
