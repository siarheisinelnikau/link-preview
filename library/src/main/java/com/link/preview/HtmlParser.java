package com.link.preview;

import com.link.preview.exception.HtmlParseException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import rx.Observable;
import rx.functions.Func1;

class HtmlParser {

    private static final String HTTP_PROTOCOL = "http://";
    private static final String HTTPS_PROTOCOL = "https://";

    static Observable<SourceEntity> preview(String url) {
        return Observable.create(subscriber -> {
            subscriber.onNext(mapUrl().call(url));
            subscriber.onCompleted();
        });
    }

    static Observable<SourceEntity> preview(String url, Observable<String> htmlLoader) {
        return htmlLoader.map(Jsoup::parse).map(mapDocument(url));
    }

    static Func1<String, SourceEntity> mapUrl() {
        return url -> {
            try {
                String unshortenUrl = unshortenUrl(extendedTrim(url));
                Document document = Jsoup.connect(unshortenUrl).userAgent("Mozilla").get();
                return mapDocument(unshortenUrl).call(document);
            } catch (IOException e) {
                throw new HtmlParseException("Can't load html");
            }
        };
    }

    static Func1<Document, SourceEntity> mapDocument(String url) {
        return document -> {
            final int imageQuantity = 1;

            SourceEntity sourceContent = new SourceEntity();
            final ArrayList<String> urls = new ArrayList<>();


            urls.addAll(SearchUrls.matches(url));

            if (urls.size() > 0) {
                sourceContent.setFinalUrl(url);
            } else {
                sourceContent.setFinalUrl("");
            }

            if (!sourceContent.getFinalUrl().equals("")) {
                if (isImage(sourceContent.getFinalUrl()) && !sourceContent.getFinalUrl().contains("dropbox")) {
                    sourceContent.setSuccess(true);
                    sourceContent.getImages().add(sourceContent.getFinalUrl());
                    sourceContent.setTitle("");
                    sourceContent.setDescription("");
                } else {
                    try {
                        sourceContent.setHtmlCode(extendedTrim(document.toString()));
                        HashMap<String, String> metaTags = getMetaTags(sourceContent.getHtmlCode());
                        sourceContent.setMetaTags(metaTags);
                        sourceContent.setTitle(metaTags.get("title"));
                        sourceContent.setDescription(metaTags.get("description"));
                        if (sourceContent.getTitle().equals("")) {
                            String matchTitle = Regex.pregMatch(sourceContent.getHtmlCode(), Regex.TITLE_PATTERN, 2);
                            if (!matchTitle.equals("")) {
                                sourceContent.setTitle(htmlDecode(matchTitle));
                            }
                        }

                        if (sourceContent.getDescription().equals("")) {
                            sourceContent.setDescription(crawlCode(sourceContent.getHtmlCode()));
                        }

                        sourceContent.setDescription(sourceContent.getDescription().replaceAll(Regex.SCRIPT_PATTERN, ""));

                        if (!metaTags.get("image").equals("")) {
                            sourceContent.getImages().add(metaTags.get("image"));
                        } else {
                            sourceContent.setImages(getImages(document, imageQuantity));
                        }

                        sourceContent.setSuccess(true);
                    } catch (Exception e) {
                        sourceContent.setSuccess(false);
                    }
                }
            }

            String[] finalLinkSet = sourceContent.getFinalUrl().split("&");
            sourceContent.setUrl(finalLinkSet[0]);

            sourceContent.setCannonicalUrl(cannonicalPage(sourceContent.getFinalUrl()));
            sourceContent.setDescription(stripTags(sourceContent.getDescription()));

            if (!sourceContent.isSuccess() && extendedTrim(sourceContent.getHtmlCode()).equals("") && !isImage(sourceContent.getFinalUrl())) {
                throw new HtmlParseException(String.format("Can't fetch data from %s", url));
            }

            return sourceContent;
        };
    }

    private static String getTagContent(String tag, String content) {
        String pattern = "<" + tag + "(.*?)>(.*?)</" + tag + ">";
        String result = "", currentMatch;

        List<String> matches = Regex.pregMatchAll(content, pattern, 2);

        int matchesSize = matches.size();
        for (int i = 0; i < matchesSize; i++) {
            currentMatch = stripTags(matches.get(i));
            if (currentMatch.length() >= 120) {
                result = extendedTrim(currentMatch);
                break;
            }
        }

        if (result.equals("")) {
            String matchFinal = Regex.pregMatch(content, pattern, 2);
            result = extendedTrim(matchFinal);
        }

        result = result.replaceAll("&nbsp;", "");

        return htmlDecode(result);
    }

    static List<String> getImages(Document document, int imageQuantity) {
        List<String> matches = new ArrayList<>();

        Elements media = document.select("[src]");

        for (Element srcElement : media) {
            if (srcElement.tagName().equals("img")) {
                matches.add(srcElement.attr("abs:src"));
            }
        }

        matches = matches.subList(0, imageQuantity);
        return matches;
    }

    private static String htmlDecode(String content) {
        return Jsoup.parse(content).text();
    }

    private static String crawlCode(String content) {
        String result;
        String resultSpan;
        String resultParagraph;
        String resultDiv;

        resultSpan = getTagContent("span", content);
        resultParagraph = getTagContent("p", content);
        resultDiv = getTagContent("div", content);

        if (resultParagraph.length() > resultSpan.length() && resultParagraph.length() >= resultDiv.length()) {
            result = resultParagraph;
        } else if (resultParagraph.length() > resultSpan.length() && resultParagraph.length() < resultDiv.length()) {
            result = resultDiv;
        } else {
            result = resultParagraph;
        }

        return htmlDecode(result);
    }

    private static String cannonicalPage(String url) {

        String cannonical = "";
        if (url.startsWith(HTTP_PROTOCOL)) {
            url = url.substring(HTTP_PROTOCOL.length());
        } else if (url.startsWith(HTTPS_PROTOCOL)) {
            url = url.substring(HTTPS_PROTOCOL.length());
        }

        int urlLength = url.length();
        for (int i = 0; i < urlLength; i++) {
            if (url.charAt(i) != '/') {
                cannonical += url.charAt(i);
            } else {
                break;
            }
        }

        return cannonical;

    }

    private static String stripTags(String content) {
        return Jsoup.parse(content).text();
    }

    private static boolean isImage(String url) {
        return url.matches(Regex.IMAGE_PATTERN);
    }

    private static HashMap<String, String> getMetaTags(String content) {

        HashMap<String, String> metaTags = new HashMap<>();
        metaTags.put("url", "");
        metaTags.put("title", "");
        metaTags.put("description", "");
        metaTags.put("image", "");

        List<String> matches = Regex.pregMatchAll(content, Regex.METATAG_PATTERN, 1);

        for (String match : matches) {
            final String lowerCase = match.toLowerCase();
            if (lowerCase.contains("property=\"og:url\"")
                    || lowerCase.contains("property='og:url'")
                    || lowerCase.contains("name=\"url\"")
                    || lowerCase.contains("name='url'")) {
                updateMetaTag(metaTags, "url", separeMetaTagsContent(match));
            } else if (lowerCase.contains("property=\"og:title\"")
                    || lowerCase.contains("property='og:title'")
                    || lowerCase.contains("name=\"title\"")
                    || lowerCase.contains("name='title'")) {
                updateMetaTag(metaTags, "title", separeMetaTagsContent(match));
            } else if (lowerCase.contains("property=\"og:description\"")
                    || lowerCase.contains("property='og:description'")
                    || lowerCase.contains("name=\"description\"")
                    || lowerCase.contains("name='description'")) {
                updateMetaTag(metaTags, "description", separeMetaTagsContent(match));
            } else if (lowerCase.contains("property=\"og:image\"")
                    || lowerCase.contains("property='og:image'")
                    || lowerCase.contains("name=\"image\"")
                    || lowerCase.contains("name='image'")) {
                updateMetaTag(metaTags, "image", separeMetaTagsContent(match));
            }
        }

        return metaTags;
    }

    private static void updateMetaTag(HashMap<String, String> metaTags, String url, String value) {
        if (value != null && (value.length() > 0)) {
            metaTags.put(url, value);
        }
    }

    private static String separeMetaTagsContent(String content) {
        String result = Regex.pregMatch(content, Regex.METATAG_CONTENT_PATTERN, 1);
        return htmlDecode(result);
    }

    static String extendedTrim(String content) {
        return content.replaceAll("\\s+", " ").replace("\n", " ")
                .replace("\r", " ").trim();
    }

    private static String unshortenUrl(String shortURL) {
        if (!shortURL.startsWith(HTTP_PROTOCOL)
                && !shortURL.startsWith(HTTPS_PROTOCOL))
            return "";

        URLConnection urlConn = connectURL(shortURL);
        urlConn.getHeaderFields();

        String finalResult = urlConn.getURL().toString();

        urlConn = connectURL(finalResult);
        urlConn.getHeaderFields();

        shortURL = urlConn.getURL().toString();

        while (!shortURL.equals(finalResult)) {
            finalResult = unshortenUrl(finalResult);
        }

        return finalResult;
    }

    private static URLConnection connectURL(String strURL) {
        URLConnection conn = null;
        try {
            URL inputURL = new URL(strURL);
            conn = inputURL.openConnection();
        } catch (MalformedURLException e) {
            System.out.println("Please input a valid URL");
        } catch (IOException ioe) {
            System.out.println("Can not connect to the URL");
        }
        return conn;
    }

    private HtmlParser() {
    }
}