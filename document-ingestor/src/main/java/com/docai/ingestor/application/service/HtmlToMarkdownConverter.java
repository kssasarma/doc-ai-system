package com.docai.ingestor.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Converts a Tika-produced XHTML DOM tree into lightweight Markdown: headings become {@code #}
 * lines, tables become pipe tables, {@code <pre>} blocks become fenced code blocks, list items
 * become {@code -}/{@code 1.} bullets.
 *
 * This exists because {@link SemanticChunker}'s heading/code-block detection only fires on
 * literal Markdown syntax, but every real upload (CHM/PDF/HTML) previously arrived as one
 * flattened, undifferentiated blob of plain text (Tika's {@code BodyContentHandler}) — so the
 * chunker's structure-awareness never had anything to detect. This converter gives it real
 * structure to work with.
 */
@Component
public class HtmlToMarkdownConverter {

    private static final Set<String> SKIP_TAGS = Set.of("head", "script", "style", "meta", "link", "title");
    private static final Set<String> BLOCK_DESCENDANT_TAGS =
        Set.of("table", "ul", "ol", "pre", "div", "section", "article",
               "h1", "h2", "h3", "h4", "h5", "h6");

    public String convert(Document dom) {
        StringBuilder out = new StringBuilder();
        Element body = firstElementByTag(dom.getDocumentElement(), "body");
        walkBlock(body != null ? body : dom.getDocumentElement(), out, 0);
        return collapseBlankLines(out.toString()).trim();
    }

    private void walkBlock(Node node, StringBuilder out, int listDepth) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.TEXT_NODE) {
                appendInlineText(child.getTextContent(), out);
                continue;
            }
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;

            String tag = child.getNodeName().toLowerCase();
            if (SKIP_TAGS.contains(tag)) continue;

            switch (tag) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    int level = tag.charAt(1) - '0';
                    String text = inlineText(child);
                    if (!text.isBlank()) {
                        out.append("\n\n").append("#".repeat(level)).append(' ').append(text).append("\n\n");
                    }
                }
                case "p", "div", "section", "article" -> {
                    // Containers can hold either flowing text or nested block elements (tables,
                    // lists) — recurse as a block when they have block descendants, otherwise
                    // flatten to a single paragraph.
                    if (hasBlockDescendant(child)) {
                        walkBlock(child, out, listDepth);
                    } else {
                        String text = inlineText(child);
                        if (!text.isBlank()) out.append("\n\n").append(text).append("\n\n");
                    }
                }
                case "table" -> {
                    String md = tableToMarkdown((Element) child);
                    if (!md.isBlank()) out.append("\n\n").append(md).append("\n\n");
                }
                case "pre" -> {
                    String code = rawText(child).strip();
                    if (!code.isBlank()) out.append("\n\n```\n").append(code).append("\n```\n\n");
                }
                case "code" -> {
                    // Inline code outside <pre> — wrap as an inline span, not a fenced block.
                    String text = inlineText(child);
                    if (!text.isBlank()) out.append('`').append(text).append('`');
                }
                case "ul", "ol" -> walkList(child, out, listDepth);
                case "li" -> {
                    // Reached only for a <li> outside a recognized list ancestor (malformed
                    // source HTML) — still emit it as its own bullet rather than dropping it.
                    out.append("\n").append("  ".repeat(listDepth)).append("- ").append(inlineText(child)).append('\n');
                }
                case "br" -> out.append('\n');
                default -> walkBlock(child, out, listDepth);
            }
        }
    }

    private void walkList(Node listNode, StringBuilder out, int depth) {
        boolean ordered = "ol".equalsIgnoreCase(listNode.getNodeName());
        int n = 1;
        for (Node li = listNode.getFirstChild(); li != null; li = li.getNextSibling()) {
            if (li.getNodeType() != Node.ELEMENT_NODE || !"li".equalsIgnoreCase(li.getNodeName())) continue;
            String marker = ordered ? (n++ + ". ") : "- ";
            out.append('\n').append("  ".repeat(depth)).append(marker).append(inlineText(li)).append('\n');

            for (Node nested = li.getFirstChild(); nested != null; nested = nested.getNextSibling()) {
                if (nested.getNodeType() == Node.ELEMENT_NODE) {
                    String nestedTag = nested.getNodeName().toLowerCase();
                    if (nestedTag.equals("ul") || nestedTag.equals("ol")) {
                        walkList(nested, out, depth + 1);
                    }
                }
            }
        }
        out.append('\n');
    }

    private String tableToMarkdown(Element table) {
        List<List<String>> rows = new ArrayList<>();
        collectRows(table, rows);
        if (rows.isEmpty()) return "";

        int cols = rows.stream().mapToInt(List::size).max().orElse(0);
        if (cols == 0) return "";

        StringBuilder md = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            md.append('|');
            for (int c = 0; c < cols; c++) {
                String cell = c < row.size() ? row.get(c) : "";
                md.append(' ').append(cell.replace("|", "\\|").replace("\n", " ").trim()).append(" |");
            }
            md.append('\n');
            if (r == 0) {
                md.append('|');
                for (int c = 0; c < cols; c++) md.append(" --- |");
                md.append('\n');
            }
        }
        return md.toString();
    }

    private void collectRows(Node node, List<List<String>> rows) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            String tag = child.getNodeName().toLowerCase();
            if (tag.equals("tr")) {
                List<String> cells = new ArrayList<>();
                for (Node cell = child.getFirstChild(); cell != null; cell = cell.getNextSibling()) {
                    if (cell.getNodeType() != Node.ELEMENT_NODE) continue;
                    String cellTag = cell.getNodeName().toLowerCase();
                    if (cellTag.equals("td") || cellTag.equals("th")) {
                        cells.add(inlineText(cell));
                    }
                }
                if (!cells.isEmpty()) rows.add(cells);
            } else {
                // thead/tbody/tfoot — transparent containers, recurse to find <tr> inside.
                collectRows(child, rows);
            }
        }
    }

    private boolean hasBlockDescendant(Node node) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            String tag = child.getNodeName().toLowerCase();
            if (BLOCK_DESCENDANT_TAGS.contains(tag)) return true;
            if (hasBlockDescendant(child)) return true;
        }
        return false;
    }

    /** Flattens a node's descendant content into a single normalized-whitespace inline string —
     * recurses rather than using getTextContent() directly so a nested {@code <code>} span (e.g.
     * {@code <p>Run <code>npm install</code> first.</p>}) still gets its backtick treatment
     * instead of being silently flattened to plain text along with everything else. */
    private String inlineText(Node node) {
        StringBuilder sb = new StringBuilder();
        appendInline(node, sb);
        return sb.toString().trim();
    }

    private void appendInline(Node node, StringBuilder out) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.TEXT_NODE) {
                appendInlineText(child.getTextContent(), out);
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tag = child.getNodeName().toLowerCase();
                if (SKIP_TAGS.contains(tag)) {
                    continue;
                } else if (tag.equals("code")) {
                    String codeText = child.getTextContent().replaceAll("\\s+", " ").trim();
                    if (!codeText.isBlank()) out.append('`').append(codeText).append('`');
                } else if (tag.equals("br")) {
                    out.append(' ');
                } else {
                    appendInline(child, out);
                }
            }
        }
    }

    private void appendInlineText(String text, StringBuilder out) {
        if (text == null) return;
        String normalized = text.replaceAll("\\s+", " ");
        if (normalized.isBlank()) return;
        if (!out.isEmpty() && !Character.isWhitespace(out.charAt(out.length() - 1)) && !normalized.startsWith(" ")) {
            out.append(' ');
        }
        out.append(normalized.strip());
    }

    /** Preserves original whitespace/newlines — used for {@code <pre>} content only. */
    private String rawText(Node node) {
        return node.getTextContent();
    }

    private Element firstElementByTag(Element root, String tag) {
        NodeList list = root.getElementsByTagName(tag);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private String collapseBlankLines(String text) {
        return text.replaceAll("\n{3,}", "\n\n");
    }
}
