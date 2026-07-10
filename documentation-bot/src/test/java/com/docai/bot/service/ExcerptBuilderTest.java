package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.docai.bot.domain.model.ExcerptBuilder;

class ExcerptBuilderTest {

    @Test
    void shortContent_returnedUnchanged() {
        String content = "Short answer.";
        assertThat(ExcerptBuilder.build(content, 240)).isEqualTo(content);
    }

    @Test
    void longPlainText_cutsAtSentenceBoundary() {
        String content = "First sentence is here. Second sentence follows. " +
            "Third sentence keeps going well past the target length to force truncation of this excerpt.";

        String excerpt = ExcerptBuilder.build(content, 50);

        assertThat(excerpt).startsWith("First sentence is here. Second sentence follows.");
        assertThat(excerpt).doesNotContain("Third sentence");
    }

    @Test
    void tableStraddlingCutPoint_keptIntactRatherThanSlicedMidRow() {
        String table = "| Server | Supported |\n| --- | --- |\n| Tomcat 9 | Yes |\n| JBoss 7 | Yes |\n| WebLogic 12c | Yes |";
        String content = "Intro text before the table.\n\n" + table;

        // A target length that lands inside the table (well before the table's own end).
        String excerpt = ExcerptBuilder.build(content, 45);

        assertThat(excerpt).contains("| Tomcat 9 | Yes |");
        assertThat(excerpt).contains("| WebLogic 12c | Yes |"); // the whole table survives intact
    }

    @Test
    void codeBlockStraddlingCutPoint_keptIntactRatherThanSlicedMidBlock() {
        String content = "Run this to start the server:\n\n```\nserver.start()\nserver.listen(8080)\n```\n\nThen check the logs.";

        String excerpt = ExcerptBuilder.build(content, 40);

        assertThat(excerpt).contains("server.start()");
        assertThat(excerpt).contains("server.listen(8080)");
        assertThat(excerpt).contains("```");
    }

    @Test
    void noSentenceBoundaryNearby_fallsBackToWordBoundary() {
        String content = "supercalifragilisticexpialidocious ".repeat(20).trim();

        String excerpt = ExcerptBuilder.build(content, 50);

        assertThat(excerpt).endsWith("…");
        assertThat(excerpt.length()).isLessThanOrEqualTo(60);
    }

    @Test
    void nullContent_returnsEmptyStringRatherThanThrowing() {
        assertThat(ExcerptBuilder.build(null, 100)).isEmpty();
    }
}
