package io.github.vanilds.creditdesk.proposal.document;

import io.github.vanilds.creditdesk.shared.web.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentServiceTest {

    @Test
    void keepsOnlyTheFileName() {
        assertThat(DocumentService.cleanFileName("payslip.pdf")).isEqualTo("payslip.pdf");
        assertThat(DocumentService.cleanFileName("C:\\Users\\ana\\Desktop\\payslip.pdf")).isEqualTo("payslip.pdf");
        assertThat(DocumentService.cleanFileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentService.cleanFileName("  extrato\n.pdf ")).isEqualTo("extrato.pdf");
    }

    @Test
    void refusesNamesThatAreOnlyAPath() {
        assertThatThrownBy(() -> DocumentService.cleanFileName("../")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DocumentService.cleanFileName("..")).isInstanceOf(ApiException.class);
    }
}
