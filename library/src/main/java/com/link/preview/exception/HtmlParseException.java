package com.link.preview.exception;

public class HtmlParseException extends RuntimeException {

    public HtmlParseException(String message) {
        super(message);
    }

    public HtmlParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public HtmlParseException(Throwable cause) {
        super(cause);
    }
}
