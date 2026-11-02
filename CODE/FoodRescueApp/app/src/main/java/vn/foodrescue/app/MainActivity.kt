package vn.foodrescue.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import vn.foodrescue.app.ui.customer.CustomerApp
import vn.foodrescue.app.ui.theme.FoodRescueTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setOnExitAnimationListener { screen ->
            screen.view.animate().alpha(0f).setDuration(180L)
                .withEndAction { screen.remove() }.start()
        }
        enableEdgeToEdge()
        setContent { FoodRescueTheme { CustomerApp(startInPreview = BuildConfig.DEBUG && intent.getBooleanExtra("preview_discovery", false)) } }
    }
}
