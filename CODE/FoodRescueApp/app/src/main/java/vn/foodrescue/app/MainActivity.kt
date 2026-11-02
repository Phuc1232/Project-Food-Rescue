package vn.foodrescue.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import vn.foodrescue.app.ui.home.HomeUiState
import vn.foodrescue.app.ui.home.HomeViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF16624C), background = Color(0xFFF6F8F4))) {
                Scaffold { padding ->
                    Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("FoodRescue", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Cùng giảm lãng phí thực phẩm", style = MaterialTheme.typography.titleMedium)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Kết nối hệ thống", style = MaterialTheme.typography.titleLarge)
                                when (val current = state) {
                                    HomeUiState.Loading -> { CircularProgressIndicator(); Text("Đang kiểm tra máy chủ và cơ sở dữ liệu…") }
                                    is HomeUiState.Ready -> Text("Đã kết nối ${current.serviceName}. Backend và PostgreSQL đang hoạt động.")
                                    is HomeUiState.Error -> Text(current.message, color = MaterialTheme.colorScheme.error)
                                }
                                Button(onClick = viewModel::refresh, enabled = state !is HomeUiState.Loading) { Text("Kiểm tra lại") }
                            }
                        }

                    }
                }
            }
        }
    }
}
