package com.review.council.reviewer;

import com.review.council.state.Finding;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FindingParserTest {
    private final FindingParser parser = new FindingParser();

    @Test
    void parses_cleanJson() throws Exception {
        var json = """
            {"findings":[
              {"severity":"critical","line":42,"message":"SQL injection","suggested_fix":"use parameterized query"},
              {"severity":"minor","line":10,"message":"unused variable","suggested_fix":""}
            ]}
            """;
        var findings = parser.parse(json);
        assertThat(findings).hasSize(2);
        assertThat(findings.get(0).severity()).isEqualTo("critical");
        assertThat(findings.get(1).severity()).isEqualTo("minor");
    }

    @Test
    void strips_markdownFences() throws Exception {
        var json = """
            ```json
            {"findings":[{"severity":"major","line":1,"message":"x","suggested_fix":"y"}]}
            ```
            """;
        var findings = parser.parse(json);
        assertThat(findings).hasSize(1);
    }

    @Test
    void returns_empty_onBlank() throws Exception {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("no json here")).isEmpty();
    }
}
