package com.link.preview;

public final class Preview {

    private String url;
    private String title;
    private String imageUrl;

    public Preview(String url, String title, String imageUrl) {
        this.url = url;
        this.title = title;
        this.imageUrl = imageUrl;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
