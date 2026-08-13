package com.zone.agri.utils;

import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Chuyen van ban thuan (co the co dong bat dau bang "- ") tu Gemini thanh HTML an toan de render
 * trong bong bong chat (dangerouslySetInnerHTML phia FE) — thay the viec chi xuong dong bang \n
 * kho doc khi noi dung dai/nhieu y.
 */
public final class AiTextFormatUtils {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("</?[a-zA-Z][^>]*>");
    private static final Document.OutputSettings COMPACT_HTML_OUTPUT =
            new Document.OutputSettings().prettyPrint(false);
    private static final Safelist SAFE_RICH_TEXT = Safelist.basic()
            .addTags("h1", "h2", "h3", "h4", "s")
            .addAttributes("a", "target", "rel")
            .addProtocols("a", "href", "http", "https", "mailto", "tel");

    private AiTextFormatUtils() {
    }

    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static boolean looksLikeHtml(String value) {
        return value != null && HTML_TAG_PATTERN.matcher(value).find();
    }

    public static String sanitizeRichHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, "", SAFE_RICH_TEXT, COMPACT_HTML_OUTPUT).trim();
    }

    /**
     * Gom cac doan (cach nhau boi dong trong) thanh <p>; 1 doan neu MOI dong deu bat dau "- " thi
     * render thanh <ul><li>, nguoc lai noi dong bang <br>.
     */
    public static String plainTextToHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n").trim();
        StringBuilder html = new StringBuilder();
        for (String paragraph : normalized.split("\n\\s*\n")) {
            String trimmedParagraph = paragraph.trim();
            if (trimmedParagraph.isEmpty()) {
                continue;
            }
            String[] lines = trimmedParagraph.split("\n");
            boolean allBullets = true;
            for (String line : lines) {
                if (!line.trim().startsWith("- ")) {
                    allBullets = false;
                    break;
                }
            }
            if (allBullets) {
                html.append("<ul>");
                for (String line : lines) {
                    html.append("<li>").append(escapeHtml(line.trim().substring(2).trim())).append("</li>");
                }
                html.append("</ul>");
            } else {
                html.append("<p>");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        html.append("<br>");
                    }
                    html.append(escapeHtml(lines[i].trim()));
                }
                html.append("</p>");
            }
        }
        return html.toString();
    }

    /**
     * Bang HTML danh sach benh nghi ngo (2 cot: ten benh / dau hieu dien hinh), kem anh chan doan
     * (neu co) ngay sau bang — dung cho luong hoi lam ro benh AI Doctor, thay the viec Gemini tu
     * mo ta cac benh candidate bang van xuoi kho doc.
     */
    public static String buildDiseaseCandidatesTableHtml(
            java.util.List<String[]> rows, String imageUrl) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        html.append("<table><thead><tr><th>Bệnh nghi ngờ</th><th>Dấu hiệu điển hình</th></tr></thead><tbody>");
        for (String[] row : rows) {
            html.append("<tr><td>").append(escapeHtml(row[0])).append("</td><td>")
                    .append(escapeHtml(row[1])).append("</td></tr>");
        }
        html.append("</tbody></table>");
        if (imageUrl != null && !imageUrl.isBlank()) {
            html.append("<img src=\"").append(escapeHtml(imageUrl)).append("\" alt=\"Ảnh tôm đã gửi\">");
        }
        return html.toString();
    }
}
