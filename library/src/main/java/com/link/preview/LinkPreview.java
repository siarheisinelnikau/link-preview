package com.link.preview;

import rx.Observable;

public final class LinkPreview {

    private static final Mapper MAPPER = new Mapper();

    public static Observable<Preview> preview(String url) {
        return HtmlParser.preview(url).map(MAPPER::transform);
    }

    public static Observable<Preview> preview(String url, Observable<String> htmlLoader) {
        return HtmlParser.preview(url, htmlLoader).map(MAPPER::transform);
    }

    private LinkPreview() {
    }
}
