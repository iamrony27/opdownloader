package com.opdownloader.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.opdownloader.app.R
import com.opdownloader.app.ui.components.AppHeader
import com.opdownloader.app.ui.components.CardSurface
import com.opdownloader.app.ui.components.KineticButton
import com.opdownloader.app.ui.components.ScreenPaddingNoTabs
import com.opdownloader.app.ui.components.SettingRow
import com.opdownloader.app.ui.theme.LocalAppColors
import com.opdownloader.app.ui.toast

const val SUPPORT_UPI = "7074018142@ptsbi"
const val SUPPORT_BTC = "bc1qc3dkwnzmjvw97dc633rrmccplt6ae2mhwp7jm0"
const val SUPPORT_GITHUB = "https://github.com/iamrony27"
const val SUPPORT_WHATSAPP = "https://wa.me/917074018142"
const val APP_GITHUB_REPO = "https://github.com/iamrony27/opdownloader"
const val APP_GITHUB_ISSUES = "https://github.com/iamrony27/opdownloader/issues"

fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("oP Downloader", text))
    }
}

fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { toast(context, "No app available for this link") }
}

@Composable
fun SupportScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var showBtcQr by remember { mutableStateOf(true) }
    var showUpiQr by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = ScreenPaddingNoTabs,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppHeader(
                title = "Buy me a coffee",
                subtitle = "From the one human behind this app",
                showBack = true,
                onBack = { nav.popBackStack() },
            )
        }

        item {
            CardSurface(color = colors.primaryDeep.copy(alpha = 0.12f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colors.primaryDeep.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = colors.primaryDeep,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "A note from the dev",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Hey — thanks for opening this.",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "I vibe-coded oP Downloader alone: long nights, a phone that runs too hot, a hundred “why is this not compiling”, and an AI keeping me company at 2am. No ads, no tracking, no company behind it — just one person who wanted downloading to feel effortless.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "If it saved you a headache today, the nicest thank-you is a coffee. And if you'd rather build something of your own, my inbox is wide open.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }

        item {
            CardSurface {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Coffee fund ☕",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Any amount, any day — GPay, PhonePe, Paytm, whatever you already have.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            SUPPORT_UPI,
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.primary.copy(alpha = 0.16f))
                                .clickable {
                                    copyToClipboard(context, SUPPORT_UPI)
                                    toast(context, "UPI ID copied")
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Copy",
                                color = colors.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KineticButton(
                            label = "Donate with UPI",
                            onClick = {
                                openLink(
                                    context,
                                    "upi://pay?pa=${Uri.encode(SUPPORT_UPI)}&pn=${Uri.encode("oP Downloader")}&cu=INR",
                                )
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Favorite,
                            height = 46.dp,
                        )
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceVariant)
                                .clickable { showUpiQr = !showUpiQr },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (showUpiQr) Icons.Default.VisibilityOff else Icons.Default.QrCode,
                                contentDescription = "Toggle UPI QR",
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (showUpiQr) {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .border(2.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                            ) {
                                QrCodePatternCanvas(
                                    pattern = UPI_QR_PATTERN,
                                    modifier = Modifier.size(170.dp),
                                    color = Color.Black,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No UPI app on this phone? Copy the ID above and send it from any device.",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }

        item {
            // Deep yellow/amber palette for Bitcoin
            val btcYellow = Color(0xFFF59E0B)
            val btcBg = if (colors.isDark) Color(0xFF261D08) else Color(0xFFFFFBEB)
            val btcBorder = if (colors.isDark) Color(0xFF784E0C) else Color(0xFFFDE68A)
            val btcSurface = if (colors.isDark) Color(0xFF332408) else Color(0xFFFEF3C7)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(btcBg)
                    .border(1.5.dp, btcBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(btcYellow.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.CurrencyBitcoin,
                                contentDescription = "Bitcoin",
                                tint = btcYellow,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Bitcoin Donation",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "On-chain BTC network (SegWit)",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(btcYellow.copy(alpha = 0.2f))
                                .clickable { showBtcQr = !showBtcQr }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (showBtcQr) Icons.Default.VisibilityOff else Icons.Default.QrCode,
                                contentDescription = if (showBtcQr) "Hide QR" else "Show QR",
                                tint = btcYellow,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (showBtcQr) "Hide QR" else "Show QR",
                                color = btcYellow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    if (showBtcQr) {
                        Spacer(Modifier.height(14.dp))

                        // QR code display
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .border(2.dp, btcYellow.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                            ) {
                                QrCodePatternCanvas(
                                    pattern = BTC_QR_PATTERN,
                                    modifier = Modifier.size(180.dp),
                                    color = Color.Black,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // BTC address container + copy button
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(btcSurface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "BITCOIN ADDRESS",
                            color = btcYellow,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            SUPPORT_BTC,
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    KineticButton(
                        label = "Copy BTC Address",
                        onClick = {
                            copyToClipboard(context, SUPPORT_BTC)
                            toast(context, "Bitcoin address copied")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.ContentCopy,
                        color = btcYellow,
                        height = 46.dp,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Scan the QR code in any crypto wallet (Cash App, Binance, Trust Wallet, Strike, etc.) or copy the address.",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }

        item {
            CardSurface {
                Column {
                    SettingRow(
                        title = "GitHub · iamrony27",
                        subtitle = "Read the code, fork it, ship your own version",
                        onClick = { openLink(context, SUPPORT_GITHUB) },
                        trailing = {
                            Text("↗", color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(1.dp)
                            .background(colors.divider),
                    )
                    SettingRow(
                        title = "WhatsApp · +91 70740 18142",
                        subtitle = "Want your own project built with me? wa.me/917074018142",
                        onClick = { openLink(context, SUPPORT_WHATSAPP) },
                        trailing = {
                            Text("↗", color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}
