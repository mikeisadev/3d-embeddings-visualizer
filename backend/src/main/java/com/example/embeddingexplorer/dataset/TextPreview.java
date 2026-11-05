package com.example.embeddingexplorer.dataset;

import java.util.regex.Pattern;

/**
 * Short, single-line versions of chunk text for tooltips and lists. Cuts happen on code
 * point boundaries, so emoji and other surrogate pairs are never split.
 */
public final class TextPreview {

    /** Length of the previews sent with the points. */
    public static final int PREVIEW_LENGTH = 160;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String ELLIPSIS = "…";

    private TextPreview() {}

    /** The text on one line (PDF text is full of line breaks), cut at {@value #PREVIEW_LENGTH} characters. */
    public static String preview(String content) {
        return truncate(WHITESPACE.matcher(content).replaceAll(" ").strip(), PREVIEW_LENGTH);
    }

    /** Cuts the text at {@code maxCodePoints} characters, adding an ellipsis when something was removed. */
    public static String truncate(String text, int maxCodePoints) {
        if (text.codePointCount(0, text.length()) <= maxCodePoints) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxCodePoints);
        return text.substring(0, end) + ELLIPSIS;
    }

    /** Length in characters (code points), as a person would count them. */
    public static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
