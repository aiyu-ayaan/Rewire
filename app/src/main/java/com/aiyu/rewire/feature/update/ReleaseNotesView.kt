package com.aiyu.rewire.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.domain.update.NoteBlock
import com.aiyu.rewire.domain.update.ReleaseNotes

/** A GitHub release body drawn as what it is (headings, bullets, paragraphs), not as raw `###` and `**`. */
@Composable
fun ReleaseNotesView(notes: String, modifier: Modifier = Modifier) {
    val blocks = remember(notes) { ReleaseNotes.parse(notes) }
    val code = SpanStyle(fontFamily = FontFamily.Monospace, background = MaterialTheme.colorScheme.surfaceContainerHighest)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Heading -> Text(block.text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                is NoteBlock.Bullet -> Row {
                    Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
                    Text(inline(block.text, code), style = MaterialTheme.typography.bodyMedium)
                }
                is NoteBlock.Paragraph -> Text(inline(block.text, code), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val INLINE = Regex("""\*\*(.+?)\*\*|`([^`]+)`""")

private fun inline(text: String, code: SpanStyle): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(last, m.range.first))
        m.groups[1]?.let { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it.value) } }
        m.groups[2]?.let { withStyle(code) { append(it.value) } }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
