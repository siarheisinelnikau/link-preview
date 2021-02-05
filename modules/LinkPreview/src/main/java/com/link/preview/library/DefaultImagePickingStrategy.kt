package com.link.preview.library

import com.link.preview.library.BaseImagePickingStrategy
import org.jsoup.nodes.Document
import java.util.*

internal class DefaultImagePickingStrategy : BaseImagePickingStrategy() {

    /**
     * Gets images from the html code
     */
    override fun getImages(doc: Document, metaTags: HashMap<String, String>): List<String> {
        val tags = getMetaImage(metaTags)
        return if (tags.isEmpty()) getImagesFromImgTags(doc) else tags
    }
}
