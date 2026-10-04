package com.justcal.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.justcal.app.R
import com.justcal.app.camera.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException

@HiltViewModel
class PhotoReviewViewModel @Inject constructor(val images: ImagePreprocessor) : ViewModel() {
    private var owned: PreparedImage? = null
    fun attach(image: PreparedImage) { owned = image }
    override fun onCleared() { owned?.let { images.discard(it.uri) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoReviewScreen(
    image: PreparedImage, images: ImagePreprocessor,
    onReplace: () -> Unit, onBack: () -> Unit,
) {
    var bitmap by remember(image.uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(image.uri) { mutableStateOf(false) }
    var verified by rememberSaveable(image.uri) { mutableStateOf(false) }
    LaunchedEffect(image.uri) {
        try {
            bitmap = images.decode(image, preview = true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; verified = false }
    }
    val date = LocalDate.ofEpochDay(image.request.dayEpoch).format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]))
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(stringResource(image.request.mode.label()), style = MaterialTheme.typography.titleLarge)
                    Text(date, style = MaterialTheme.typography.bodyMedium)
                }
            }, navigationIcon = { IconButton(onClick = onBack) { CalIcon(R.drawable.ic_back, stringResource(R.string.close)) } })
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val landscape = maxWidth > maxHeight
            val maxActionsHeight = maxHeight * 0.6f
            val preview: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier, contentAlignment = Alignment.Center) {
                    bitmap?.let {
                        Image(it.asImageBitmap(), stringResource(R.string.photo_description),
                            Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                    }
                    if (bitmap == null && !failed) CircularProgressIndicator()
                }
            }
            val actions: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(if (verified) R.string.photo_ready else R.string.review_photo),
                        style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.image_dimensions, image.width, image.height),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (failed) Text(stringResource(R.string.image_error), color = MaterialTheme.colorScheme.error)
                    if (verified) {
                        Text(stringResource(R.string.photo_ready_message))
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.done))
                        }
                    } else {
                        Button(onClick = {
                            try {
                                // Milestone endpoint: this exact input will be handed to local recognition.
                                images.verify(image)
                                verified = true
                            } catch (_: Exception) { failed = true }
                        }, enabled = bitmap != null && !failed,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.use_photo))
                        }
                    }
                    OutlinedButton(onClick = onReplace, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(if (image.source == ImageSource.CAMERA) R.string.retake else R.string.choose_another))
                    }
                }
            }
            if (landscape) Row(Modifier.fillMaxSize()) {
                preview(Modifier.weight(1f).fillMaxHeight())
                actions(Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                preview(Modifier.weight(1f).fillMaxWidth())
                actions(Modifier.fillMaxWidth().heightIn(max = maxActionsHeight))
            }
        }
    }
}
