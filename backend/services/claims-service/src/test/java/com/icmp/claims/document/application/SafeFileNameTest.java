package com.icmp.claims.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.icmp.claims.document.domain.FileKind;

class SafeFileNameTest {

    @Test
    void keepsReadableNamesAndFixesTheExtensionToTheRealType() {
        assertThat(DocumentService.safeFileName("front bumper.jpeg", FileKind.JPEG)).isEqualTo("front bumper.jpg");
        assertThat(DocumentService.safeFileName("photo.exe", FileKind.PNG)).isEqualTo("photo.png");
        assertThat(DocumentService.safeFileName("police-report", FileKind.PDF)).isEqualTo("police-report.pdf");
    }

    @Test
    void dropsPathsAndCharactersThatBreakHeaders() {
        assertThat(DocumentService.safeFileName("../../etc/passwd.jpg", FileKind.JPEG)).isEqualTo("passwd.jpg");
        assertThat(DocumentService.safeFileName("C:\\Users\\a\\IMG_1.png", FileKind.PNG)).isEqualTo("IMG_1.png");
        assertThat(DocumentService.safeFileName("a\"b<c>\r\n.pdf", FileKind.PDF)).isEqualTo("abc.pdf");
    }

    @Test
    void blankOrHugeNamesStillGiveAUsableName() {
        assertThat(DocumentService.safeFileName(null, FileKind.JPEG)).isEqualTo("document.jpg");
        assertThat(DocumentService.safeFileName(".jpg", FileKind.JPEG)).isEqualTo("document.jpg");
        assertThat(DocumentService.safeFileName("x".repeat(500) + ".pdf", FileKind.PDF)).hasSize(200).endsWith(".pdf");
    }
}
