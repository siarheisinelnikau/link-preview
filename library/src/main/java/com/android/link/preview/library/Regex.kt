package com.android.link.preview.library

import com.android.link.preview.LinkPreloader.extendedTrim
import java.util.ArrayList
import java.util.regex.Pattern


@Suppress("unused")
internal object Regex {
    const val IMAGE_PATTERN = "(.+?)\\.(jpg|png|gif|bmp)$"
    const val IMAGE_TAG_PATTERN = "<img(.*?)src=(\"|')(.+?)(gif|jpg|png|bmp)(\"|')(.*?)(/)?>(</img>)?"
    const val ICON_TAG_PATTERN = "<img(.*?)src=(\"|')(.+?)(gif|jpg|png|bmp)(\"|')(.*?)(/)?>(</img>)?"
    const val ICON_REV_TAG_PATTERN = "<img(.*?)src=(\"|')(.+?)(gif|jpg|png|bmp)(\"|')(.*?)(/)?>(</img>)?"
    const val ITEMPROP_IMAGE_TAG_PATTERN = "<img(.*?)src=(\"|')(.+?)(gif|jpg|png|bmp)(\"|')(.*?)(/)?>(</img>)?"
    const val ITEMPROP_IMAGE_REV_TAG_PATTERN = "<img(.*?)src=(\"|')(.+?)(gif|jpg|png|bmp)(\"|')(.*?)(/)?>(</img>)?"
    const val TITLE_PATTERN = "<title(.*?)>(.*?)</title>"
    const val SCRIPT_PATTERN = "<script(.*?)>(.*?)</script>"
    const val METATAG_PATTERN = "<meta(.*?)>"
    const val METATAG_CONTENT_PATTERN = "content=\"(.*?)\""
    const val URL_PATTERN = "<\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]>"

    fun pregMatch(content: String, pattern: String, index: Int): String {

        var match = ""
        val matcher = Pattern.compile(pattern).matcher(content)

        while (matcher.find()) {
            match = matcher.group(index)
            break
        }

        return extendedTrim(match)
    }

    fun pregMatchAll(
        content: String, pattern: String,
        index: Int
    ): List<String> {

        val matches = ArrayList<String>()
        val matcher = Pattern.compile(pattern).matcher(content)

        while (matcher.find()) {
            matches.add(extendedTrim(matcher.group(index)))
        }

        return matches
    }

    fun pregMatchAllImages(content: String, pattern: String): List<String> {

        val matches = ArrayList<String>()
        val matcher = Pattern.compile(pattern).matcher(content)

        while (matcher.find()) {
            matches.add(extendedTrim(matcher.group(3)) + matcher.group(4))
        }

        return matches
    }

    fun pregMatchAllExtraImages(
        content: String,
        pattern: String
    ): List<String> {

        val matches = ArrayList<String>()
        val matcher = Pattern.compile(pattern).matcher(content)

        while (matcher.find()) {
            matches.add(extendedTrim(matcher.group(3)) + matcher.group(4))
        }

        return matches
    }
}