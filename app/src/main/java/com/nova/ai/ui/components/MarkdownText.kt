package com.nova.ai.ui.components

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false
) {
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                if (isUser) {
                    setTextColor(android.graphics.Color.WHITE)
                } else {
                    setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
                }
                textSize = 16f
            }
        },
        update = { textView ->
            val markwon = Markwon.create(textView.context)
            markwon.setMarkdown(textView, markdown)
        },
        modifier = modifier
    )
}
