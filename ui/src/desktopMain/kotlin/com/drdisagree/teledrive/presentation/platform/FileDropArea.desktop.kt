package com.drdisagree.teledrive.presentation.platform

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun FileDropArea(
    onDropped: (List<String>) -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val target = remember(onDropped) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false
                }
                val files = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                }.getOrNull().orEmpty()
                val paths = files.filter { it.isFile }.map { it.absolutePath }
                if (paths.isEmpty()) return false
                onDropped(paths)
                return true
            }
        }
    }
    Box(
        modifier = modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
            },
            target = target
        )
    ) {
        content()
    }
}
