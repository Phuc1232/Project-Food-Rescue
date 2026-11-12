package com.foodrescue.app.feature.auth.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodrescue.app.R
import com.foodrescue.app.core.designsystem.component.FoodRescueBrand
import com.foodrescue.app.core.designsystem.component.FoodRescueButton
import com.foodrescue.app.core.designsystem.theme.FoodRescueTheme

@Composable
fun WelcomeScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.background)) {
        val largeText = LocalDensity.current.fontScale > 1.2f
        val photoHeight = (maxHeight * if (largeText) .43f else .52f).coerceIn(220.dp, 460.dp)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(photoHeight)) {
                Image(
                    painter = painterResource(R.drawable.welcome_bakery),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = .52f), Color.Transparent, Color.Black.copy(alpha = .12f)),
                        ),
                    ),
                )
                FoodRescueBrand(
                    modifier = Modifier.statusBarsPadding().padding(start = 24.dp, top = 16.dp),
                    color = Color.White,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth().offset(y = (-24).dp),
                color = colors.background,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 28.dp)) {
                        Spacer(Modifier.height(30.dp))
                        Text(
                            text = buildAnnotatedString {
                                append(stringResource(R.string.welcome_title))
                                append('\n')
                                withStyle(SpanStyle(color = colors.primary)) {
                                    append(stringResource(R.string.welcome_title_accent))
                                }
                            },
                            modifier = Modifier.semantics { heading() },
                            color = colors.onBackground,
                            fontSize = 27.sp,
                            lineHeight = 33.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-.7).sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.welcome_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.ic_foodrescue_mark),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.welcome_pickup_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(28.dp))
                        FoodRescueButton(
                            text = stringResource(R.string.welcome_register),
                            onClick = onRegister,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = onLogin,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = colors.onSurfaceVariant, fontWeight = FontWeight.Normal)) {
                                        append(stringResource(R.string.welcome_login_prompt))
                                    }
                                    withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)) {
                                        append(stringResource(R.string.welcome_login))
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }
}

@Preview(name = "Welcome", widthDp = 384, heightDp = 832)
@Preview(name = "Welcome · compact", widthDp = 320, heightDp = 568)
@Preview(name = "Welcome · large text", widthDp = 384, heightDp = 832, fontScale = 1.5f)
@Composable
private fun WelcomePreview() {
    FoodRescueTheme { WelcomeScreen(onLogin = {}, onRegister = {}) }
}
