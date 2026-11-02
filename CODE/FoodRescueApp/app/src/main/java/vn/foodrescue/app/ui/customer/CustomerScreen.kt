package vn.foodrescue.app.ui.customer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import vn.foodrescue.app.data.model.*
import vn.foodrescue.app.data.sample.SampleBoxes
import vn.foodrescue.app.ui.components.*
import vn.foodrescue.app.ui.theme.FoodRescueTheme

/** UI prototype only: sample IDs never leave this screen or create orders. */
@Composable
fun CustomerScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf(FoodCategory.ALL.name) }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var favorites by rememberSaveable(
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() })
    ) { mutableStateOf<List<String>>(emptyList()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf(false) }
    val selectedBox = SampleBoxes.items.find { it.id == selectedId }
    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
    }
    BackHandler(enabled=selectedBox!=null || savedOnly) {
        if (selectedBox!=null) selectedId=null else savedOnly=false
    }
    Scaffold(
        containerColor=MaterialTheme.colorScheme.background,
        bottomBar={
            if(selectedBox==null) NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                NavigationBarItem(selected=!savedOnly, onClick={savedOnly=false}, icon={AppIcon(AppSymbol.SEARCH, null)}, label={Text("Khám phá")})
                NavigationBarItem(selected=savedOnly, onClick={savedOnly=true}, icon={AppIcon(AppSymbol.HEART, null)}, label={Text("Đã lưu (${favorites.size})")})
            } else Surface(shadowElevation=6.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Column { Text("Giá mỗi hộp",style=MaterialTheme.typography.bodySmall); Text(selectedBox.price.vnd(),style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary) }
                        StatusBadge("Còn ${selectedBox.remaining} hộp")
                    }
                    FoodRescueButton("Đặt hộp · sắp ra mắt",onClick={},modifier=Modifier.fillMaxWidth(),enabled=false)
                    Text("Bản mẫu giao diện · chưa hỗ trợ đặt hàng hoặc thanh toán.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { padding ->
        if(selectedBox!=null) {
            BoxDetails(selectedBox,selectedBox.id in favorites,{toggleFavorite(selectedBox.id)},{selectedId=null},Modifier.padding(padding))
        } else {
            val source=if(savedOnly) SampleBoxes.items.filter{it.id in favorites} else SampleBoxes.items
            val visible=source.filterBoxes(query,FoodCategory.valueOf(categoryName))
            LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Icon(AppSymbol.PIN)
                            Column(Modifier.weight(1f)) {
                                Text("KHÁM PHÁ QUANH BẠN",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("TP. Hồ Chí Minh",style=MaterialTheme.typography.titleSmall)
                            }
                            TextButton(onClick={notice=true}) { Text("Bản mẫu") }
                        }
                        Text(if(savedOnly) "Những hộp bạn thích" else "Ngon hơn khi\ncùng sẻ chia.",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(if(savedOnly) "Lưu lại một lựa chọn ngon cho lần tới." else "Khám phá hộp ngon, giá tốt. Bớt lãng phí mỗi ngày.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
                            label={Text("Tìm cửa hàng hoặc hộp")},leadingIcon={AppIcon(AppSymbol.SEARCH, null)},
                            trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}){AppIcon(AppSymbol.CLOSE,"Xóa tìm kiếm")}},
                            shape=MaterialTheme.shapes.medium)
                    }
                }
                item {
                    LazyRow(contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(FoodCategory.entries) { category ->
                            FilterChip(selected=categoryName==category.name,onClick={categoryName=category.name},label={Text(category.label)},modifier=Modifier.heightIn(min=48.dp))
                        }
                    }
                }
                if(!savedOnly && query.isBlank() && categoryName==FoodCategory.ALL.name) item {
                    Surface(Modifier.padding(horizontal=20.dp).fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer) {
                        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Icon(AppSymbol.LEAF)
                            Column {
                                Text("Một hộp nhỏ. Một thay đổi xanh.",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Nhận tại cửa hàng trong khung giờ đã chọn.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.padding(horizontal=20.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(if(savedOnly) "Đã lưu" else "Hộp ngon quanh bạn",style=MaterialTheme.typography.titleLarge)
                        Text("${visible.size} lựa chọn",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(visible.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        AppIcon(if(savedOnly) AppSymbol.HEART else AppSymbol.SEARCH, null,modifier=Modifier.size(40.dp))
                        Text(if(savedOnly && favorites.isEmpty()) "Bạn chưa lưu hộp nào" else "Chưa tìm thấy hộp phù hợp",style=MaterialTheme.typography.titleMedium)
                        Text(if(savedOnly && favorites.isEmpty()) "Chạm biểu tượng trái tim trên thẻ để lưu hộp bạn thích." else "Thử từ khóa khác hoặc chọn lại loại thực phẩm.",style=MaterialTheme.typography.bodyMedium)
                        TextButton(onClick={query="";categoryName=FoodCategory.ALL.name;if(favorites.isEmpty())savedOnly=false}) { Text("Khám phá tất cả") }
                    }
                }
                items(visible,key={it.id}) { box ->
                    Box(Modifier.padding(horizontal=20.dp)) {
                        SurpriseBoxCard(box,box.id in favorites,{toggleFavorite(box.id)},{selectedId=box.id})
                    }
                }
                item { Text("Dữ liệu và ảnh minh họa · không phải ưu đãi đang mở bán.",Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if(notice) AlertDialog(onDismissRequest={notice=false},title={Text("Bản mẫu khách hàng")},text={Text("Cửa hàng, giá, khoảng cách và giờ nhận hiện là dữ liệu minh họa. Vị trí chưa dùng GPS. Danh sách đã lưu chỉ được giữ trong phiên xem. Chức năng đăng nhập và đặt hàng sẽ được kết nối API sau.")},confirmButton={TextButton(onClick={notice=false}){Text("Đã hiểu")}})
}

@Composable
private fun Icon(symbol: AppSymbol) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { AppIcon(symbol, null) }
}

@Composable
private fun BoxDetails(box: SurpriseBox, favorite: Boolean, onFavorite: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box {
            BoxPhoto(box,Modifier.fillMaxWidth().height(240.dp))
            Surface(Modifier.padding(12.dp),shape=MaterialTheme.shapes.large) { IconButton(onClick=onBack){AppIcon(AppSymbol.BACK,"Quay lại")} }
            Surface(Modifier.align(Alignment.TopEnd).padding(12.dp),shape=MaterialTheme.shapes.large) {
                IconToggleButton(checked=favorite,onCheckedChange={onFavorite()}){AppIcon(AppSymbol.HEART,if(favorite)"Bỏ lưu hộp" else "Lưu hộp")}
            }
            StatusBadge("Ảnh minh họa",Modifier.align(Alignment.BottomStart).padding(16.dp))
        }
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(box.storeName,style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                Text(box.name,style=MaterialTheme.typography.headlineMedium)
                Text("${box.category.label} · ${box.distanceKm.toString().replace('.', ',')} km (minh họa)",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("Thời gian nhận hộp",fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(box.pickup,color=MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Bạn đến nhận trực tiếp tại cửa hàng.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            DetailSection("Có gì trong hộp?",box.description)
            DetailSection("Lưu ý về dị ứng",box.allergens)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            DetailSection("Địa điểm nhận",box.address)
            DetailSection("Giá trị tham chiếu", "Khoảng ${box.referenceValue.vnd()}. Đây là giá trị ước tính; thành phần và số lượng thực tế có thể thay đổi.")
            Text("Thông tin trên là dữ liệu mẫu để xem trước giao diện.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailSection(title: String, body: String) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium)
        Text(body,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground=true, widthDp=390, heightDp=844)
@Composable
private fun CustomerPreview() { FoodRescueTheme { CustomerScreen() } }
