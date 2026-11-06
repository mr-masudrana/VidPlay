package com.tubex.app.core

import com.tubex.app.ui.Sub
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** নতুন ভার্সন এলে অ্যাপ খুললে দেখানো পপআপ */
@Composable
fun UpdateDialog(info: UpdateInfo, onDownload: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(L.t("TubeX আপডেট")) },
        text = {
            Column {
                Text(
                    L.f("নতুন ভার্সন {0} এসেছে (আপনার ভার্সন {1})", info.version, Updater.currentVersion),
                    fontWeight = FontWeight.SemiBold
                )
                if (info.notes.isNotBlank()) Text(
                    info.notes.trim().take(500), color = Sub, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).heightIn(max = 200.dp).verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text(L.t("আপডেট ডাউনলোড করুন")) } },
        dismissButton = { TextButton(onClick = onLater) { Text(L.t("পরে")) } }
    )
}
