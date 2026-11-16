package com.textpic.studio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textpic.studio.R
import com.textpic.studio.ui.theme.LocalTextPicColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextPicTopBar(
    workspaceName: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onWorkspaceClick: () -> Unit,
) {
    val colors = LocalTextPicColors.current
    TopAppBar(
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                    )
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "TextPic Studio",
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Surface(
                    onClick = onWorkspaceClick,
                    color = colors.surfaceSelected,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Row(
                        modifier = Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = workspaceName.ifBlank { "My workspace" },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Workspace settings",
                            tint = colors.textMuted,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(16.dp),
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.bgCanvas,
            titleContentColor = colors.textPrimary,
            navigationIconContentColor = colors.textPrimary,
        ),
    )
}
