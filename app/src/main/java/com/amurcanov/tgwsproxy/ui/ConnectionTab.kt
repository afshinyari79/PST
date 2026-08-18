package com.amurcanov.tgwsproxy.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amurcanov.tgwsproxy.AccessControl
import com.amurcanov.tgwsproxy.ProxyController
import com.amurcanov.tgwsproxy.ProxyService
import com.amurcanov.tgwsproxy.SettingsStore
import com.amurcanov.tgwsproxy.R
import kotlinx.coroutines.launch

@Composable
fun ConnectionTab(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val isRunning by ProxyService.isRunning.collectAsStateWithLifecycle()
    val isVerifiedRunning by ProxyService.isVerifiedRunning.collectAsStateWithLifecycle()

    val isReady by settingsStore.isReady.collectAsStateWithLifecycle(initialValue = false)

    val savedPort by settingsStore.port.collectAsStateWithLifecycle(initialValue = "1443")
    val savedBindIp by settingsStore.bindIp.collectAsStateWithLifecycle(initialValue = "127.0.0.1")
    val savedSecretKey by settingsStore.secretKey.collectAsStateWithLifecycle(initialValue = "LOADING")

    val scope = rememberCoroutineScope()

    var showAccessDialog by remember { mutableStateOf(false) }
    var accessDeviceCode by remember { mutableStateOf("") }
    var showNetworkErrorDialog by remember { mutableStateOf<String?>(null) }
    var showPausedDialog by remember { mutableStateOf(false) }
    var isCheckingAccess by remember { mutableStateOf(false) }

    // Persistent bottom message — stays visible until replaced by a new one
    var bottomMessage by remember { mutableStateOf("") }

    val clientsNotFoundText = stringResource(R.string.clients_not_found)
    val errorOpeningClientText = stringResource(R.string.error_opening_client)
    val chooseClientText = stringResource(R.string.choose_client)
    val errorChoosingClientText = stringResource(R.string.error_choosing_client)

    if (!isReady || savedSecretKey == "LOADING") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
        return
    }

    LaunchedEffect(savedSecretKey) {
        if (savedSecretKey == "") {
            val bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(bytes)
            val generated = bytes.joinToString("") { "%02x".format(it) }
            scope.launch { settingsStore.saveSecretKey(generated) }
        }
    }

    var isStarting by remember { mutableStateOf(false) }
    val statusText = when {
        isVerifiedRunning -> stringResource(R.string.status_connected)
        isStarting || isRunning -> stringResource(R.string.status_connecting)
        else -> stringResource(R.string.status_disconnected)
    }

    LaunchedEffect(isRunning, isVerifiedRunning) {
        if (isVerifiedRunning || !isRunning) {
            isStarting = false
        }
    }

    val port = savedPort.toIntOrNull() ?: 1443
    val secretForUrl = remember(savedSecretKey) {
        val raw = savedSecretKey.trim()
        if (raw.isNotEmpty() && raw != "LOADING") raw else "00000000000000000000000000000000"
    }
    val bindIp = savedBindIp.trim().takeIf { it.isNotEmpty() } ?: "127.0.0.1"
    val proxyUrl = "https://t.me/proxy?server=$bindIp&port=$port&secret=dd$secretForUrl"

    val connectAction = {
        if (!isRunning && !isStarting && !isCheckingAccess) {
            isCheckingAccess = true
            scope.launch {
                val outcome = AccessControl.checkAccess(context)
                isCheckingAccess = false
                when (outcome.result) {
                    AccessControl.AccessResult.OK -> {
                        isStarting = true
                        val started = ProxyController.startFromSavedSettings(
                            context = context,
                            showInvalidPortToast = true
                        )
                        if (!started) {
                            isStarting = false
                        }
                    }
                    AccessControl.AccessResult.NOT_REGISTERED -> {
                        accessDeviceCode = AccessControl.getOrCreateDeviceCode(context)
                        showAccessDialog = true
                    }
                    AccessControl.AccessResult.SERVICE_PAUSED -> {
                        showPausedDialog = true
                    }
                    AccessControl.AccessResult.NETWORK_ERROR -> {
                        showNetworkErrorDialog = outcome.detail ?: ""
                    }
                }
            }
        }
    }

    val disconnectAction = {
        if (isRunning || isStarting) {
            ProxyController.stop(context)
        }
    }

    val isActiveVisual = isRunning || isStarting
    val logoScale by animateFloatAsState(
        targetValue = if (isActiveVisual) 1.12f else 0.94f,
        animationSpec = tween(durationMillis = 650, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "logo_scale"
    )
    val verifiedReveal by animateFloatAsState(
        targetValue = if (isVerifiedRunning) 1f else 0f,
        animationSpec = if (isVerifiedRunning) {
            tween(durationMillis = 620, easing = FastOutSlowInEasing)
        } else {
            snap()
        },
        label = "verified_logo_reveal"
    )
    val logoInteractionSource = remember { MutableInteractionSource() }
    val statusColor by animateColorAsState(
        targetValue = if (isVerifiedRunning) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "connection_status_color"
    )

    if (showAccessDialog) {
        AlertDialog(
            onDismissRequest = { showAccessDialog = false },
            title = { Text("نیاز به مجوز دسترسی") },
            text = { Text("این کد را کپی کن و به ربات @proxysabetbot در تلگرام بفرست تا مجوز دسترسی برایت فعال شود:\n\n$accessDeviceCode") },
            confirmButton = {
                TextButton(onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("device code", accessDeviceCode))
                    bottomMessage = "کد کپی شد"
                }) { Text("کپی کد") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessDialog = false }) { Text("باشه") }
            }
        )
    }

    if (showPausedDialog) {
        AlertDialog(
            onDismissRequest = { showPausedDialog = false },
            title = { Text("سرویس موقتاً غیرفعال است") },
            text = { Text("دسترسی این ماه هنوز فعال نشده. کمی بعد دوباره امتحان کن.") },
            confirmButton = {
                TextButton(onClick = { showPausedDialog = false }) { Text("باشه") }
            }
        )
    }

    showNetworkErrorDialog?.let { detail ->
        AlertDialog(
            onDismissRequest = { showNetworkErrorDialog = null },
            title = { Text("خطای شبکه") },
            text = { Text("امکان بررسی مجوز دسترسی وجود نداشت.\n\nجزئیات فنی:\n$detail") },
            confirmButton = {
                TextButton(onClick = { showNetworkErrorDialog = null }) { Text("باشه") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "پروکسی ثابت تلگرام",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "@P500Y   •   @P1000Y",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(44.dp))
                    .clickable(
                        interactionSource = logoInteractionSource,
                        indication = null,
                        onClick = if (isActiveVisual) disconnectAction else connectAction
                    )
                    .scale(logoScale)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_telegram_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                    alpha = 0.52f
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_telegram_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            if (verifiedReveal > 0f) {
                                val radius = maxOf(size.width, size.height) * verifiedReveal
                                val revealPath = Path().apply {
                                    addOval(
                                        Rect(
                                            left = center.x - radius,
                                            top = center.y - radius,
                                            right = center.x + radius,
                                            bottom = center.y + radius
                                        )
                                    )
                                }
                                clipPath(revealPath) { this@drawWithContent.drawContent() }
                            }
                        }
                )
            }

            Text(
                text = if (isCheckingAccess) "در حال بررسی مجوز دسترسی..." else statusText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                textAlign = TextAlign.Center
            )

            if (isRunning) {
                IconButton(
                    onClick = {
                        applyToTelegramPackages(
                            context, proxyUrl,
                            clientsNotFoundText, errorOpeningClientText,
                            chooseClientText, errorChoosingClientText
                        ) { msg -> bottomMessage = msg }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_telegram_logo),
                        contentDescription = stringResource(R.string.apply_in_telegram),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = bottomMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        )
    }
}

private val telegramPackages = listOf(
    "org.telegram.messenger",
    "com.radolyn.ayugram",
    "com.exteragram.messenger",
    "org.telegram.plus",
    "ir.ilmili.telegraph",
    "org.telegram.BifToGram",
    "tw.nekomimi.nekogram",
    "xyz.nextalone.nagram",
    "uz.unnarsx.cherrygram",
    "org.telegram.mdgram",
    "org.forkclient.messenger.beta",
    "app.nicegram",
    "top.qwq2333.nullgram",
    "com.iMe.android",
    "ru.dahl.messenger",
    "com.scriptsaz.litegram",
    "org.thunderdog.challegram"
)

private fun applyToTelegramPackages(
    context: Context,
    url: String,
    clientsNotFoundText: String,
    errorOpeningClientText: String,
    chooseClientText: String,
    errorChoosingClientText: String,
    onMessage: (String) -> Unit
) {
    val pm = context.packageManager
    val availablePackages = telegramPackages.filter {
        try {
            pm.getPackageInfo(it, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    if (availablePackages.isEmpty()) {
        onMessage(clientsNotFoundText)
        return
    }

    val targetedIntents = availablePackages.map { pkg ->
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(pkg)
        }
    }

    if (targetedIntents.size == 1) {
        val intent = targetedIntents.first().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            onMessage(errorOpeningClientText)
        }
    } else {
        val chooserIntent = Intent.createChooser(targetedIntents.first(), chooseClientText)
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedIntents.drop(1).toTypedArray())
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            onMessage(errorChoosingClientText)
        }
    }
}
