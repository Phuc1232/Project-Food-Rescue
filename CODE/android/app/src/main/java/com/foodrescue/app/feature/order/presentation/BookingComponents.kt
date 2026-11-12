package com.foodrescue.app.feature.order.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.order.domain.PickupWindow
import com.foodrescue.app.feature.surprisebox.domain.CatalogBox
import com.foodrescue.app.feature.surprisebox.presentation.component.photo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun bookingDate(millis: Long): String = bookingTime(millis, "EEEE, dd/MM/yyyy")
internal fun bookingClock(millis: Long): String = bookingTime(millis, "HH:mm")
internal fun bookingTimestamp(millis: Long): String = bookingTime(millis, "HH:mm · dd/MM/yyyy")
internal fun bookingHours(pickup: PickupWindow): String =
    bookingTime(pickup.startMillis, "HH:mm") + " – " + bookingTime(pickup.endMillis, "HH:mm")
private fun bookingTime(millis: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.forLanguageTag("vi-VN")).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }.format(Date(millis))
internal fun countdown(seconds: Long): String =
    (seconds / 60).toString().padStart(2, '0') + ":" + (seconds % 60).toString().padStart(2, '0')

@Composable
internal fun BookingBoxSummary(box: CatalogBox, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(box.photo()), contentDescription = null,
            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(box.store, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(box.category.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(box.price) + " / hộp", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun BookingPickupCard(box: CatalogBox, pickup: PickupWindow, modifier: Modifier = Modifier) {
    FoodRescueCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FoodRescueGlyph(FoodRescueIcon.CLOCK, tint = MaterialTheme.colorScheme.primary)
            Text("Giờ nhận tại cửa hàng", style = MaterialTheme.typography.titleMedium)
        }
        Text(bookingHours(pickup), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(bookingDate(pickup.startMillis), style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider()
        Text(box.location?.address ?: box.district, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun BookingAmount(label: String, amount: Long, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(amount), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
