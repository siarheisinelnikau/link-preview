package com.android.link.preview

import com.android.link.preview.library.DefaultImagePickingStrategy
import com.android.link.preview.library.DefaultUrlExtractionStrategy
import com.android.link.preview.library.Regex
import com.android.link.preview.library.SourceContent
import org.jsoup.Jsoup
import org.jsoup.UnsupportedMimeTypeException
import org.jsoup.nodes.Document
import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.util.*

class Preview(val title: String, val description: String, val imageUrls: List<String>)

object LinkPreloader {

    private const val HTTP_PROTOCOL = "http://"
    private const val HTTPS_PROTOCOL = "https://"

    internal const val ALL = -1
    private const val NONE = -2

    /**
     * Synchronously parsing link to get metadata
     */
    @Throws(Throwable::class)
    fun load(link: String): Preview {
        val urlExtractionStrategy = DefaultUrlExtractionStrategy()
        val imagePickingStrategy = DefaultImagePickingStrategy()

        val urlStrings = urlExtractionStrategy.extractUrls(link)
        val url = if (!urlStrings.isEmpty()) unshortenUrl(urlStrings[0]) else ""

        val sourceContent = SourceContent()

        sourceContent.finalUrl = url
        var wasPreviewGenerationSuccessful = false
        if (url != "") {
            if (isImage(url) && !url.contains("dropbox")) {
                sourceContent.setSourceContentForImage(sourceContent.finalUrl)
                wasPreviewGenerationSuccessful = true
            } else {
                val document: Document = Jsoup.connect(sourceContent.finalUrl).userAgent("Mozilla").get()

                try {
                    sourceContent.htmlCode = extendedTrim(document.toString())

                    val metaTags = getMetaTags(sourceContent.htmlCode)

                    sourceContent.metaTags = metaTags

                    // must not be null every time
                    sourceContent.title = metaTags["title"]!!
                    sourceContent.description = metaTags["description"]!!

                    if (sourceContent.title == "") {
                        val matchTitle = Regex.pregMatch(sourceContent.htmlCode, Regex.TITLE_PATTERN, 2)
                        if (matchTitle != "") sourceContent.title = htmlDecode(matchTitle)
                    }

                    if (sourceContent.description == "") sourceContent.description = crawlCode(sourceContent.htmlCode)

                    sourceContent.description = sourceContent.description.replace(Regex.SCRIPT_PATTERN.toRegex(), "")

                    if (imagePickingStrategy.imageQuantity != NONE) {
                        val images: List<String> = imagePickingStrategy.getImages(document, metaTags)
                        sourceContent.images = images.toMutableList()
                    }

                    wasPreviewGenerationSuccessful = true
                } catch (t: Throwable) {
                    if (t is UnsupportedMimeTypeException) {
                        val mimeType = t.mimeType
                        if (mimeType != null && mimeType.startsWith("image")) {
                            sourceContent.setSourceContentForImage(sourceContent.finalUrl)
                            wasPreviewGenerationSuccessful = true
                        }
                    }
                }

            }
            sourceContent.isSuccess = wasPreviewGenerationSuccessful
        }

        val finalLinkSet = sourceContent.finalUrl.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        sourceContent.url = finalLinkSet[0]

        sourceContent.cannonicalUrl = cannonicalPage(sourceContent.finalUrl)
        sourceContent.description = stripTags(sourceContent.description)

        return Preview(sourceContent.title, sourceContent.description, sourceContent.images)
    }


    /**
     * Removes extra spaces and trim the string
     */
    fun extendedTrim(content: String): String = content
        .replace("\\s+".toRegex(), " ").replace("\n", " ")
        .replace("\r", " ").trim { it <= ' ' }


    /**
     * Gets content from a html tag
     */
    private fun getTagContent(tag: String, content: String): String {

        val pattern = "<$tag(.*?)>(.*?)</$tag>"
        var result = ""
        var currentMatch: String

        val matches = Regex.pregMatchAll(content, pattern, 2)

        val matchesSize = matches.size
        for (i in 0 until matchesSize) {
            currentMatch = stripTags(matches[i])
            if (currentMatch.length >= 120) {
                result = extendedTrim(currentMatch)
                break
            }
        }

        if (result == "") {
            val matchFinal = Regex.pregMatch(content, pattern, 2)
            result = extendedTrim(matchFinal)
        }

        result = result.replace("&nbsp;".toRegex(), "")

        return htmlDecode(result)
    }

    /**
     * Transforms from html to normal string
     */
    private fun htmlDecode(content: String): String = Jsoup.parse(content).text()


    /**
     * Crawls the code looking for relevant information
     */
    private fun crawlCode(content: String): String {
        val resultSpan = getTagContent("span", content)
        val resultParagraph = getTagContent("p", content)
        val resultDiv = getTagContent("div", content)

        val result: String

        result = when {
            resultParagraph.length > resultSpan.length && resultParagraph.length >= resultDiv.length -> resultParagraph
            resultParagraph.length > resultSpan.length && resultParagraph.length < resultDiv.length -> resultDiv
            else -> resultParagraph
        }

        return htmlDecode(result)
    }

    /**
     * Returns the cannoncial url
     */
    private fun cannonicalPage(url: String): String {
        @Suppress("NAME_SHADOWING") var url = url

        var cannonical = ""
        if (url.startsWith(HTTP_PROTOCOL)) {
            url = url.substring(HTTP_PROTOCOL.length)
        } else if (url.startsWith(HTTPS_PROTOCOL)) {
            url = url.substring(HTTPS_PROTOCOL.length)
        }

        for (i in 0 until url.length) if (url[i] != '/') cannonical += url[i] else break

        return cannonical

    }

    /**
     * Strips the tags from an element
     */
    private fun stripTags(content: String): String = Jsoup.parse(content).text()


    /**
     * Verifies if the url is an image
     */
    private fun isImage(url: String): Boolean = url.matches(Regex.IMAGE_PATTERN.toRegex())


    /**
     * Returns meta tags from html code
     */
    private fun getMetaTags(content: String): HashMap<String, String> {
        val metaTags = HashMap<String, String>()
        metaTags["url"] = ""
        metaTags["title"] = ""
        metaTags["description"] = ""
        metaTags["image"] = ""

        val matches = Regex.pregMatchAll(
            content,
            Regex.METATAG_PATTERN, 1
        )

        for (match in matches) {
            val lowerCase = match.toLowerCase()
            if (lowerCase.contains("property=\"og:url\"")
                || lowerCase.contains("property='og:url'")
                || lowerCase.contains("name=\"url\"")
                || lowerCase.contains("name='url'")
            )
                updateMetaTag(metaTags, "url", separeMetaTagsContent(match))
            else if (lowerCase.contains("property=\"og:title\"")
                || lowerCase.contains("property='og:title'")
                || lowerCase.contains("name=\"title\"")
                || lowerCase.contains("name='title'")
            )
                updateMetaTag(metaTags, "title", separeMetaTagsContent(match))
            else if (lowerCase
                    .contains("property=\"og:description\"")
                || lowerCase
                    .contains("property='og:description'")
                || lowerCase.contains("name=\"description\"")
                || lowerCase.contains("name='description'")
            )
                updateMetaTag(metaTags, "description", separeMetaTagsContent(match))
            else if (lowerCase.contains("property=\"og:image\"")
                || lowerCase.contains("property='og:image'")
                || lowerCase.contains("name=\"image\"")
                || lowerCase.contains("name='image'")
            )
                updateMetaTag(metaTags, "image", separeMetaTagsContent(match))
        }

        return metaTags
    }

    private fun updateMetaTag(metaTags: HashMap<String, String>, url: String, value: String?) {
        if (!value.isNullOrEmpty()) metaTags[url] = value
    }

    /**
     * Gets content from metatag
     */
    private fun separeMetaTagsContent(content: String): String {
        val result = Regex.pregMatch(content, Regex.METATAG_CONTENT_PATTERN, 1)
        return htmlDecode(result)
    }

    /**
     * Unshortens a short url
     */
    private fun unshortenUrl(originURL: String): String {
        if (!originURL.startsWith(HTTP_PROTOCOL) && !originURL.startsWith(HTTPS_PROTOCOL)) return ""

        var urlConn = connectURL(originURL)
        urlConn.headerFields

        val finalUrl = urlConn.url

        urlConn = connectURL(finalUrl)
        urlConn.headerFields

        val shortURL = urlConn.url

        var finalResult = shortURL.toString()

        while (!shortURL.sameFile(finalUrl)) {
            var isEndlesslyRedirecting = false
            if (shortURL.host == finalUrl.host) {
                if (shortURL.path == finalUrl.path) {
                    isEndlesslyRedirecting = true
                }
            }
            if (isEndlesslyRedirecting) {
                break
            } else {
                finalResult = unshortenUrl(shortURL.toString())
            }
        }

        return finalResult
    }

    /**
     * Takes a valid url string and returns a URLConnection object for the url.
     */
    @Throws(MalformedURLException::class)
    private fun connectURL(strURL: String): URLConnection {
        val inputURL = URL(strURL)
        return connectURL(inputURL)
    }

    /**
     * Takes a valid url and returns a URLConnection object for the url.
     */
    @Throws(MalformedURLException::class)
    private fun connectURL(inputURL: URL): URLConnection = inputURL.openConnection()


    private fun SourceContent.setSourceContentForImage(imageUrl: String) {
        images.add(imageUrl)

        title = ""
        description = ""
    }


}