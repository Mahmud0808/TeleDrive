package com.drdisagree.teledrive.presentation.gallery

import org.jetbrains.compose.resources.StringResource
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.gallery_tab_albums
import com.drdisagree.teledrive.resources.gallery_tab_all
import com.drdisagree.teledrive.resources.gallery_tab_photos
import com.drdisagree.teledrive.resources.gallery_tab_videos

enum class GalleryTab(val labelRes: StringResource) {
    ALL(Res.string.gallery_tab_all),
    PHOTOS(Res.string.gallery_tab_photos),
    VIDEOS(Res.string.gallery_tab_videos),
    ALBUMS(Res.string.gallery_tab_albums)
}
