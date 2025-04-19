package com.link.preview.sample

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.link.preview.Preview
import com.link.preview.PreviewState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (dark) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) { PreviewScreen() }
        }
    }
}

@Composable
private fun PreviewScreen(model: PreviewViewModel = viewModel()) {
    val input by model.input.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = input,
                    onValueChange = model::updateInput,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.url_label)) },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { model.load() }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = model::load, enabled = input.isNotBlank()) {
                        Text(stringResource(R.string.load_preview))
                    }
                    if (state == PreviewState.Loading) {
                        TextButton(onClick = model::cancel) { Text(stringResource(R.string.cancel)) }
                    }
                }
                when (val current = state) {
                    null -> Text(stringResource(R.string.empty_state))
                    PreviewState.Loading -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.loading))
                    }
                    is PreviewState.Error -> Text(
                        stringResource(R.string.error_prefix, current.cause.message.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                    )
                    is PreviewState.Success -> PreviewCard(current.preview)
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(preview: Preview) {
    val uriHandler = LocalUriHandler.current
    Card(Modifier.fillMaxWidth()) {
        preview.imageUrls.firstOrNull()?.let { image ->
            AsyncImage(
                model = image,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            preview.siteName?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            Text(
                preview.title.ifBlank { stringResource(R.string.no_title) },
                style = MaterialTheme.typography.titleLarge,
            )
            if (preview.description.isNotBlank()) Text(preview.description)
            Text(preview.finalUrl, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { uriHandler.openUri(preview.finalUrl) }) {
                Text(stringResource(R.string.open_page))
            }
        }
    }
}
