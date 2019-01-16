package com.android.link.preview.library

import java.net.URL
import java.util.*


object SearchUrls {

    private const val ALL = 0
    private const val FIRST = 1

    /** It finds urls inside the text and return the matched ones  */
    fun matches(text: String, results: Int = ALL): ArrayList<String> {

        val urls = ArrayList<String>()

        val splitString = text.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (string in splitString) {

            try {
                val item = URL(string)
                urls.add(item.toString())
            } catch (e: Exception) {
            }

            if (results == FIRST && urls.size > 0)
                break
        }

        return urls
    }

}
/** It finds urls inside the text and return the matched ones  */