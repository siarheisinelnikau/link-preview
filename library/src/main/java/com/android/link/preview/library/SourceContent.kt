@file:Suppress("unused")

package com.android.link.preview.library

import java.util.*


class SourceContent {

    /**
     * the success to set
     */
    var isSuccess = false

    /**
     * the htmlCode to set
     */
    var htmlCode = ""

    /**
     * the raw to set
     */
    var raw = ""

    /**
     * the title to set
     */
    var title = ""

    /**
     * the description to set
     */
    var description = ""

    /**
     * the url to set
     */
    var url = ""

    /**
     * the finalUrl to set
     */
    var finalUrl = ""

    /**
     * the cannonicalUrl to set
     */
    var cannonicalUrl = ""

    /**
     * the metaTags to set
     */
    var metaTags = HashMap<String, String>()

    /**
     * the images to set
     */
    var images: MutableList<String> = ArrayList()

    /**
     * the urlData to set
     */
    var urlData = arrayOfNulls<String>(2)

}
