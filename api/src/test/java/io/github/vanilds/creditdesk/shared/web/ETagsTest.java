package io.github.vanilds.creditdesk.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ETagsTest {

    @Test
    void acceptsStrongAndWeakFormsOfTheCurrentVersion() {
        ETags.require("\"3\"", 3);
        ETags.require("W/\"3\"", 3);
        ETags.require(" \"3\" ", 3);
    }

    @Test
    void missingIs428AndAnythingElseIs412() {
        assertThat(catchThrowableOfType(ApiException.class, () -> ETags.require(null, 3)).getStatus())
                .isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        for (String bad : new String[]{"\"2\"", "3", "*", "\"abc\"", "\"99999999999999999999\""}) {
            assertThatThrownBy(() -> ETags.require(bad, 3))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.PRECONDITION_FAILED));
        }
    }
}
