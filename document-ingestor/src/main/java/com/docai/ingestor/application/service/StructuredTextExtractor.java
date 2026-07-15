package com.docai.ingestor.application.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared Tika-based extraction used by {@link com.docai.ingestor.application.service.impl.ChmParser},
 * {@link com.docai.ingestor.application.service.impl.PdfParser}, and
 * {@link com.docai.ingestor.application.service.impl.HtmlParser}.
 *
 * Parses to Tika's structured XHTML output (headings, tables, paragraphs, code blocks as real
 * tags) rather than the flattened plain text those parsers previously used, then converts that
 * structure to lightweight Markdown via {@link HtmlToMarkdownConverter} so downstream chunking
 * has real boundaries to detect. Falls back to plain flattened text if the structured path fails
 * for any reason (malformed embedded HTML, an unusual encoding, etc.) so a parsing edge case
 * degrades ingestion quality rather than failing it outright.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredTextExtractor {

    /** A decompression-bomb-style document (e.g. a small file that expands to gigabytes of
     * extracted text) fails cleanly once this limit is hit, rather than growing this process's
     * heap unboundedly. Chosen generously above any legitimate document's extracted text size. */
    private static final int MAX_EXTRACTED_CHARS = 50_000_000;

    private static final Duration PARSE_TIMEOUT = Duration.ofMinutes(5);

    private static final ExecutorService PARSE_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tika-parse");
        t.setDaemon(true);
        return t;
    });

    private final HtmlToMarkdownConverter markdownConverter;

    public String extract(File file) throws Exception {
        try {
            String markdown = extractStructured(file);
            if (markdown != null && !markdown.isBlank()) {
                return markdown;
            }
            log.warn("Structured extraction produced no content for {}, falling back to plain text",
                file.getName());
        } catch (Exception e) {
            log.warn("Structured extraction failed for {} ({}), falling back to plain text",
                file.getName(), e.getMessage());
        }
        return extractPlainText(file);
    }

    private String extractStructured(File file) throws Exception {
        String xhtml = parseToXhtml(file);
        org.w3c.dom.Document dom = parseXml(xhtml);
        return markdownConverter.convert(dom);
    }

    private String parseToXhtml(File file) throws Exception {
        return runWithTimeout(file, () -> {
            try (FileInputStream inputStream = new FileInputStream(file)) {
                ToXMLContentHandler handler = new ToXMLContentHandler();
                new AutoDetectParser().parse(inputStream, handler, new Metadata(), new ParseContext());
                return handler.toString();
            }
        });
    }

    private org.w3c.dom.Document parseXml(String xhtml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Defense-in-depth against XXE, even though this XML is Tika's own trusted output rather
        // than untrusted external input.
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xhtml)));
    }

    private String extractPlainText(File file) throws Exception {
        return runWithTimeout(file, () -> {
            try (FileInputStream inputStream = new FileInputStream(file)) {
                BodyContentHandler handler = new BodyContentHandler(MAX_EXTRACTED_CHARS);
                new AutoDetectParser().parse(inputStream, handler, new Metadata(), new ParseContext());
                return handler.toString();
            }
        });
    }

    /** Apache Tika has no built-in parse timeout — a maliciously or accidentally pathological
     * document (e.g. a PDF/CHM crafted to trigger catastrophic parser backtracking) can otherwise
     * hang a parse indefinitely, tying up the async ingestion thread forever. Runs the parse on a
     * separate thread so it can be abandoned (not forcibly killed — the JDK has no safe way to do
     * that) once the timeout elapses; the caller gets a clear, document-specific failure instead
     * of a silently stuck PROCESSING document (2.5's stuck-processing reaper is the last-resort
     * backstop for exactly this, but failing fast here is far better than waiting 30 minutes). */
    private String runWithTimeout(File file, Callable<String> parseTask) throws Exception {
        Future<String> future = PARSE_EXECUTOR.submit(parseTask);
        try {
            return future.get(PARSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("Parsing " + file.getName() + " exceeded the "
                + PARSE_TIMEOUT.toMinutes() + "-minute limit — the file may be malformed or a decompression bomb", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }
}
