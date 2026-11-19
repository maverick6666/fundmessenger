package com.fundmessenger.ai.util;

public final class AiMarkdownTitleExtractor {

    private AiMarkdownTitleExtractor() {
    }

    public static String extractTitle(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "AI 생성 글";
        }

        String[] lines = markdown.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                return trimmed.substring(3).trim();
            }
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }

        String first = lines[0].trim();
        return first.length() > 50 ? first.substring(0, 50) : first;
    }
}
