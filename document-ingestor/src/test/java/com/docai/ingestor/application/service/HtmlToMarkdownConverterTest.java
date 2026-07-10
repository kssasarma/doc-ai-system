package com.docai.ingestor.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

class HtmlToMarkdownConverterTest {

    private final HtmlToMarkdownConverter converter = new HtmlToMarkdownConverter();

    @Test
    void heading_becomesMarkdownHeading() {
        String md = convert("<html><body><h2>Supported Application Servers</h2></body></html>");
        assertThat(md).contains("## Supported Application Servers");
    }

    @Test
    void paragraph_becomesPlainText() {
        String md = convert("<html><body><p>Install the agent before configuring the server.</p></body></html>");
        assertThat(md).contains("Install the agent before configuring the server.");
    }

    @Test
    void table_becomesMarkdownPipeTable() {
        String html = "<html><body><table>"
            + "<tr><th>Server</th><th>Supported</th></tr>"
            + "<tr><td>Tomcat 9</td><td>Yes</td></tr>"
            + "<tr><td>JBoss 7</td><td>Yes</td></tr>"
            + "</table></body></html>";

        String md = convert(html);

        assertThat(md).contains("| Server | Supported |");
        assertThat(md).contains("| --- | --- |");
        assertThat(md).contains("| Tomcat 9 | Yes |");
        assertThat(md).contains("| JBoss 7 | Yes |");
    }

    @Test
    void preBlock_becomesFencedCodeBlock() {
        String html = "<html><body><pre>server.start()\nserver.listen(8080)</pre></body></html>";
        String md = convert(html);

        assertThat(md).contains("```");
        assertThat(md).contains("server.start()");
        assertThat(md).contains("server.listen(8080)");
    }

    @Test
    void unorderedList_becomesBullets() {
        String html = "<html><body><ul><li>Tomcat</li><li>JBoss</li></ul></body></html>";
        String md = convert(html);

        assertThat(md).contains("- Tomcat");
        assertThat(md).contains("- JBoss");
    }

    @Test
    void orderedList_becomesNumberedList() {
        String html = "<html><body><ol><li>Install</li><li>Configure</li></ol></body></html>";
        String md = convert(html);

        assertThat(md).contains("1. Install");
        assertThat(md).contains("2. Configure");
    }

    @Test
    void inlineCode_becomesBacktickSpan() {
        String html = "<html><body><p>Run <code>npm install</code> first.</p></body></html>";
        String md = convert(html);

        assertThat(md).contains("`npm install`");
    }

    @Test
    void nestedDivWithHeadingAndTable_preservesBothAsStructure() {
        String html = "<html><body><div>"
            + "<h3>Compatibility</h3>"
            + "<table><tr><th>OS</th></tr><tr><td>Linux</td></tr></table>"
            + "</div></body></html>";

        String md = convert(html);

        assertThat(md).contains("### Compatibility");
        assertThat(md).contains("| OS |");
        assertThat(md).contains("| Linux |");
    }

    @Test
    void scriptAndStyleContent_isExcluded() {
        String html = "<html><head><style>body{color:red}</style></head>"
            + "<body><script>alert(1)</script><p>Real content</p></body></html>";

        String md = convert(html);

        assertThat(md).contains("Real content");
        assertThat(md).doesNotContain("alert(1)");
        assertThat(md).doesNotContain("color:red");
    }

    private String convert(String xhtml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document dom = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xhtml)));
            return converter.convert(dom);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
