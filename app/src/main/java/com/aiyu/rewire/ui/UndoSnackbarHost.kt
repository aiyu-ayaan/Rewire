package com.aiyu.rewire.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.rewire.R
import com.aiyu.rewire.core.undo.UndoCenter

/**
 * Shows the latest [UndoCenter] offer: "Undo" takes the change back, then "Redo" puts it again, and so on.
 * Once two or more changes are saved it says so and "View" opens the history instead.
 */
@Composable
fun UndoSnackbarHost(modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    val offer by UndoCenter.offer.collectAsStateWithLifecycle()
    val res = LocalResources.current
    LaunchedEffect(offer) {
        val o = offer ?: return@LaunchedEffect
        val many = !o.undone && o.saved >= 2
        val result = host.showSnackbar(
            message = when {
                o.undone -> res.getString(R.string.undo_undone)
                many -> res.getString(R.string.undo_changes_saved, o.saved)
                else -> res.getString(o.message)
            },
            actionLabel = res.getString(if (o.undone) R.string.undo_redo else if (many) R.string.undo_view else R.string.undo_action),
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (many) {
            if (result == SnackbarResult.ActionPerformed) UndoCenter.requestHistory()
            UndoCenter.settle(o, null)
        } else if (result == SnackbarResult.ActionPerformed) {
            if (o.undone) o.redo() else o.undo()
            UndoCenter.settle(o, o.copy(undone = !o.undone))
        } else {
            UndoCenter.settle(o, null)
        }
    }
    SnackbarHost(host, modifier)
}
