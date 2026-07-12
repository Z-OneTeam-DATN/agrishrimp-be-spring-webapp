package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.zone.agri.client.ai.CccdOcrClient;

class OcrServiceTest {

    private final OcrService ocrService = new OcrService(mock(CccdOcrClient.class));

    @Test
    void parseExtractedText_shouldExtractMainFieldsFromVietnameseCccdText() {
        String text = """
                CĂN CƯỚC CÔNG DÂN
                Số 079203001234
                Họ và tên: NGUYỄN THỊ MINH ANH
                Ngày, tháng, năm sinh: 03/02/1998
                Giới tính: Nữ
                Quốc tịch: Việt Nam
                Nơi thường trú: 123 Đường ABC, Phường DEF, Quận GHI, TP Hồ Chí Minh
                """;

        var result = ocrService.parseExtractedText(text);

        assertThat(result.getCitizenId()).isEqualTo("079203001234");
        assertThat(result.getFullName()).isEqualTo("NGUYỄN THỊ MINH ANH");
        assertThat(result.getDateOfBirth()).isEqualTo("1998-02-03");
        assertThat(result.getGender()).isEqualTo("FEMALE");
        assertThat(result.getAddress()).contains("123 Đường ABC");
        assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.95);
    }

    @Test
    void parseExtractedText_shouldHandleLabelsOnSeparateLines() {
        String text = """
                CAN CUOC CONG DAN
                SO: 001203000999
                HO VA TEN
                TRAN VAN BINH
                NGAY SINH 1-1-2000
                GIOI TINH
                NAM
                """;

        var result = ocrService.parseExtractedText(text);

        assertThat(result.getCitizenId()).isEqualTo("001203000999");
        assertThat(result.getFullName()).isEqualTo("TRAN VAN BINH");
        assertThat(result.getDateOfBirth()).isEqualTo("2000-01-01");
        assertThat(result.getGender()).isEqualTo("MALE");
    }

    @Test
    void parseExtractedText_shouldIgnoreLabelLikeNameAndTrimAddressNoise() {
        String text = """
                CAN CUOC CONG DAN
                SO: 091206016882
                FULL NAME
                NGUYEN HOANG GIA HUY
                NGAY SINH: 20/06/2006
                GIOI TINH: NAM
                NOI THUONG TRU: Tan Hiep A, Tan n Hiep, Kien Giang T E
                """;

        var result = ocrService.parseExtractedText(text);

        assertThat(result.getCitizenId()).isEqualTo("091206016882");
        assertThat(result.getFullName()).isEqualTo("NGUYỄN HOANG GIA HUY");
        assertThat(result.getAddress()).isEqualTo("Tan Hiep, Kien Giang");
    }

    @Test
    void parseExtractedText_shouldTrimSingleCharacterNoiseAtEndOfName() {
        String text = """
                CAN CUOC CONG DAN
                SO: 091206016882
                HO VA TEN: NGUYEN HOANG GIA HUY e
                NGAY SINH: 20/06/2006
                GIOI TINH: NAM
                """;

        var result = ocrService.parseExtractedText(text);

        assertThat(result.getFullName()).isEqualTo("NGUYỄN HOANG GIA HUY");
    }
}
