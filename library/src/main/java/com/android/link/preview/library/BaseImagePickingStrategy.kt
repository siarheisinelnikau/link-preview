package com.android.link.preview.library

import com.android.link.preview.LinkPreloader
import org.jsoup.nodes.Document
import java.util.*


internal abstract class BaseImagePickingStrategy : ImagePickingStrategy {
    override var imageQuantity = LinkPreloader.ALL

    protected fun getMetaImage(metaTags: HashMap<String, String>): List<String> {
        val images = ArrayList<String>()
        val metaImage = metaTags["image"]

        if (!metaImage.isNullOrEmpty()) images.add(metaImage)
        return images
    }

    protected fun getImagesFromImgTags(document: Document): List<String> {
        val images = mutableListOf<String>()
        val media = document.select("[src]")

        for (srcElement in media) {
            if (srcElement.tagName() == "img") {
                images.add(srcElement.attr("abs:src"))
                if (imageQuantity != LinkPreloader.ALL && images.size == imageQuantity) break
            }
        }
        return images
    }
}
