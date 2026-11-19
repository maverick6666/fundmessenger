package com.fundmessenger.config;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public final class InputSanitizer {
    private InputSanitizer() {}

    private static final Safelist HTML_SAFELIST = Safelist.relaxed()
            .addTags("pre", "code", "blockquote", "figure", "figcaption", "hr")
            .addAttributes("a", "target", "rel")
            .addAttributes("img", "loading", "alt", "width", "height")
            .addAttributes("code", "class")
            .addAttributes("pre", "class")
            .addAttributes(":all", "class", "style");

    /** TipTap 에디터 HTML 콘텐츠용 — 안전한 태그만 허용 */
    public static String sanitizeHtml(String input) {
        if (input == null) return null;
        return Jsoup.clean(input, HTML_SAFELIST);
    }

    /** Markdown 본문용 — 구조 문법은 유지하고 개행만 정규화 */
    public static String sanitizeMarkdown(String input) {
        if (input == null) return null;
        return input
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\u0000", "");
    }

    /** 제목/설명 등 순수 텍스트 — 모든 HTML 태그 제거 */
    public static String sanitizePlainText(String input) {
        if (input == null) return null;
        return Jsoup.clean(input, Safelist.none());
    }
}
