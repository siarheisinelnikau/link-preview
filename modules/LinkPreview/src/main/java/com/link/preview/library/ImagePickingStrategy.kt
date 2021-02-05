package com.link.preview.library

import org.jsoup.nodes.Document
import java.util.*

internal interface ImagePickingStrategy {

    var imageQuantity: Int
    fun getImages(doc: Document, metaTags: HashMap<String, String>): List<String>
}
