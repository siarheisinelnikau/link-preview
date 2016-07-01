package com.link.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Regex {

    static final String IMAGE_PATTERN = "(.+?)\\.(jpg|png|gif|bmp)$";
    static final String IMAGE_TAG_PATTERN = "<img(.*?)src=(\"|')(.+?)(gif|jpg|png|bmp)(\"|')(.*?)(/)?>(</img>)?";
    static final String ICON_TAG_PATTERN = "<img(.*?)src=(\"|')(.+?)(gif|jpg|png|bmp)(\"|')(.*?)(/)?>(</img>)?";
    static final String ICON_REV_TAG_PATTERN = "<img(.*?)src=(\"|')(.+?)(gif|jpg|png|bmp)(\"|')(.*?)(/)?>(</img>)?";
    static final String ITEMPROP_IMAGE_TAG_PATTERN = "<img(.*?)src=(\"|')(.+?)(gif|jpg|png|bmp)(\"|')(.*?)(/)?>(</img>)?";
    static final String ITEMPROP_IMAGE_REV_TAG_PATTERN = "<img(.*?)src=(\"|')(.+?)(gif|jpg|png|bmp)(\"|')(.*?)(/)?>(</img>)?";
    static final String TITLE_PATTERN = "<title(.*?)>(.*?)</title>";
    static final String SCRIPT_PATTERN = "<script(.*?)>(.*?)</script>";
    static final String METATAG_PATTERN = "<meta(.*?)>";
    static final String METATAG_CONTENT_PATTERN = "content=\"(.*?)\"";
    static final String URL_PATTERN = "<\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]>";

    static String pregMatch(String content, String pattern, int index) {
        String match = "";
        Matcher matcher = Pattern.compile(pattern).matcher(content);
        if (matcher.find()) {
            match = matcher.group(index);
        }
        return HtmlParser.extendedTrim(match);
    }

    static List<String> pregMatchAll(String content, String pattern, int index) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = Pattern.compile(pattern).matcher(content);
        while (matcher.find()) {
            matches.add(HtmlParser.extendedTrim(matcher.group(index)));
        }
        return matches;
    }

    static List<String> pregMatchAllImages(String content, String pattern) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = Pattern.compile(pattern).matcher(content);
        while (matcher.find()) {
            matches.add(HtmlParser.extendedTrim(matcher.group(3)) + matcher.group(4));
        }
        return matches;
    }

    static List<String> pregMatchAllExtraImages(String content, String pattern) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = Pattern.compile(pattern).matcher(content);
        while (matcher.find()) {
            matches.add(HtmlParser.extendedTrim(matcher.group(3)) + matcher.group(4));
        }
        return matches;
    }
}