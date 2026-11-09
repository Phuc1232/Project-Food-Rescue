package com.foodrescue.app.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.foodrescue.app.R
import com.foodrescue.app.core.designsystem.component.FoodRescueBrand
import com.foodrescue.app.core.designsystem.component.FoodRescueButton
import com.foodrescue.app.core.designsystem.theme.FoodRescueTheme

@Composable
fun LegalScreen(
    document: LegalDocument,
    onBack: () -> Unit,
    onOpenDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = legalContent(document)
    val otherDocument = if (document == LegalDocument.TERMS) LegalDocument.PRIVACY else LegalDocument.TERMS
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 28.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_auth_back), stringResource(R.string.auth_back))
                }
                Spacer(Modifier.weight(1f))
                FoodRescueBrand()
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = .6f))
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                item(key = "introduction") {
                    Column {
                        Text(
                            stringResource(content.title),
                            Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.legal_version), style = MaterialTheme.typography.bodySmall, color = colors.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(content.summary), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                itemsIndexed(content.sections, key = { _, section -> section.title }) { index, section ->
                    Column {
                        Text(
                            "${index + 1}. ${stringResource(section.title)}",
                            Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        SelectionContainer {
                            Text(stringResource(section.body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
                item(key = "contact") {
                    Column {
                        HorizontalDivider(color = colors.outlineVariant)
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.legal_contact_label), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(stringResource(R.string.legal_contact_email), style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                        }
                        Spacer(Modifier.height(20.dp))
                        FoodRescueButton(stringResource(R.string.legal_return), onBack, Modifier.fillMaxWidth())
                        TextButton(onClick = { onOpenDocument(otherDocument) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(legalContent(otherDocument).title))
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Terms", widthDp = 384, heightDp = 832)
@Preview(name = "Terms · large text", widthDp = 320, heightDp = 568, fontScale = 1.5f)
@Composable
private fun LegalPreview() {
    FoodRescueTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LegalScreen(LegalDocument.TERMS, {}, {})
        }
    }
}
