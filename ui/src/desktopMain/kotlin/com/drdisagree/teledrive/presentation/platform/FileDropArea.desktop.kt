package com.drdisagree.teledrive.presentation.platform

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var indication by remember { mutableStateOf(DropIndication.NONE) }
    val target = remember(onDropped) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                indication = classify(event)
            }

            override fun onExited(event: DragAndDropEvent) {
                indication = DropIndication.NONE
            }

            override fun onEnded(event: DragAndDropEvent) {
                indication = DropIndication.NONE
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                indication = DropIndication.NONE
                val paths = draggedFiles(event)
                    .filter { it.isFile }
                    .map { it.absolutePath }
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
        DropOverlay(indication, modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun classify(event: DragAndDropEvent): DropIndication {
    val files = draggedFiles(event)
    return when {
        files.isNotEmpty() && files.none { it.isFile } -> DropIndication.FOLDERS
        else -> DropIndication.FILES
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun draggedFiles(event: DragAndDropEvent): List<File> = runCatching {
    val transferable = event.awtTransferable
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        return emptyList()
    }
    @Suppress("UNCHECKED_CAST")
    transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
}.getOrNull().orEmpty()
