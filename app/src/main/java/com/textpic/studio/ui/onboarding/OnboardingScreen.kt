package com.textpic.studio.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.textpic.studio.LocalAppContainer
import com.textpic.studio.R
import com.textpic.studio.ime.ImeStatus
import com.textpic.studio.ui.components.GhostButton
import com.textpic.studio.ui.components.PrimaryButton
import com.textpic.studio.ui.theme.LocalTextPicColors

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val colors = LocalTextPicColors.current
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lastImeActiveAt by container.settingsRepository.lastImeActiveAt
        .collectAsStateWithLifecycle(initialValue = 0L)
    // null means "couldn't check" — never let a restricted system read crash the app.
    var keyboardEnabled by remember { mutableStateOf(ImeStatus.isEnabled(context).getOrNull()) }
    var systemSelected by remember { mutableStateOf(ImeStatus.selectedBySystem(context).getOrNull()) }
    var testText by remember { mutableStateOf("") }

    fun refreshKeyboardChecks() {
        keyboardEnabled = ImeStatus.isEnabled(context).getOrNull()
        systemSelected = ImeStatus.selectedBySystem(context).getOrNull()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKeyboardChecks()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selection = ImeStatus.resolveSelected(lastImeActiveAt, systemSelected)
    val keyboardSelected = selection == true
    val keyboardStatusUnknown = keyboardEnabled == null || selection == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgCanvas)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(colors.bgGlowStart, colors.bgGlowEnd)))
                .padding(24.dp),
        ) {
            Column {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Chat images that look native",
                    color = colors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 34.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "TextPic turns your message into a crisp PNG that fits Facebook Messenger. Everything stays on this device.",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        OnboardingStep(
            number = 1,
            title = "Enable TextPic Keyboard",
            description = "Allow the keyboard in Android settings. TextPic never reads other apps.",
            complete = keyboardEnabled == true,
            action = "Open keyboard settings",
            onAction = {
                ImeStatus.openKeyboardSettings(context)
            },
        )
        Spacer(Modifier.height(12.dp))
        OnboardingStep(
            number = 2,
            title = "Select TextPic Keyboard",
            description = "Choose TextPic from the system keyboard picker.",
            complete = keyboardSelected,
            action = "Show keyboard picker",
            onAction = {
                ImeStatus.showKeyboardPicker(context)
            },
        )
        Spacer(Modifier.height(12.dp))
        OnboardingStep(
            number = 3,
            title = "Try it in a test field",
            description = "Type a message here — the TextPic Render key appears in the keyboard.",
            complete = testText.isNotBlank(),
            action = null,
            onAction = null,
        ) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = testText,
                onValueChange = { if (it.length <= 2000) testText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type something like: Hi") },
                singleLine = false,
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {}),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceInput,
                    unfocusedContainerColor = colors.surfaceInput,
                    focusedBorderColor = colors.borderFocus,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))

        Surface(
            color = colors.surfaceCard,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Two more ways to send text",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Select text in any app and choose TextPic, or share text to TextPic from the share sheet.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }

        if (keyboardStatusUnknown) {
            Spacer(Modifier.height(12.dp))
            GhostButton(
                text = ImeStatus.CHECK_FAILED_MESSAGE,
                onClick = { onComplete() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = "Start using TextPic",
            onClick = { onComplete() },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Outlined.Check,
        )
        Spacer(Modifier.height(8.dp))
        GhostButton(
            text = "Continue without setup",
            onClick = { onComplete() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "You can finish setup any time in Settings.",
            color = colors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OnboardingStep(
    number: Int,
    title: String,
    description: String,
    complete: Boolean,
    action: String?,
    onAction: (() -> Unit)?,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = LocalTextPicColors.current
    Surface(
        color = colors.surfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (complete) colors.success.copy(alpha = 0.18f) else colors.accentSoft,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (complete) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "Completed",
                            tint = colors.success,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            number.toString(),
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        description,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                if (complete) {
                    Text(
                        "Done",
                        color = colors.success,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            content?.invoke()
            if (action != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                GhostButton(
                    text = action,
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Keyboard,
                )
            }
        }
    }
}
