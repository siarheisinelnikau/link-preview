package com.link.preview;

public class Mapper {

    public Preview transform(SourceEntity source) {
        String url = source.getCannonicalUrl();
        String title = source.getTitle();
        String image = null;
        if (source.getImages().size() > 0) {
            image = source.getImages().get(0);
        }
        return new Preview(url, title, image);
    }

}
