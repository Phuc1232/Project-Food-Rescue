package vn.foodrescue.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import vn.foodrescue.app.ui.customer.CustomerScreen
import vn.foodrescue.app.ui.theme.FoodRescueTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FoodRescueTheme { CustomerScreen() } }
    }
}
