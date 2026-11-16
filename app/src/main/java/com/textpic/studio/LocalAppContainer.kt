package com.textpic.studio

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("TextPic AppContainer was not provided.")
}
