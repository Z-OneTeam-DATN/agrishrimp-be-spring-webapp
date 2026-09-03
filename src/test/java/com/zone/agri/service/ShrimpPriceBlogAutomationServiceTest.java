package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zone.agri.dto.ai.ShrimpPriceBlogDraftSuggestion;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShrimpPriceBlogAutomationServiceTest {

    private final ShrimpPriceBlogAutomationService service = new ShrimpPriceBlogAutomationService(
            null, null, null, null, null, null);

    @Test
    void normalizeSuggestion_replacesAccentlessAiTitleWithVietnameseFallback() throws Exception {
        ShrimpPriceBlogDraftSuggestion suggestion = ShrimpPriceBlogDraftSuggestion.builder()
                .title("Gia tom hom nay 03/09/2026: cap nhat tom su va tom the")
                .excerpt("Excerpt")
                .marketSummary("Market summary")
                .seoClosing("SEO closing")
                .build();

        ShrimpPriceBlogDraftSuggestion normalized = normalizeSuggestion(suggestion);

        assertThat(normalized.getTitle()).startsWith("Giá tôm hôm nay 03/09/2026");
        assertThat(normalized.getTitle()).contains("tôm sú", "tôm thẻ");
    }

    @Test
    void normalizeSuggestion_keepsAiTitleWhenRequiredPhraseHasVietnameseAccents() throws Exception {
        ShrimpPriceBlogDraftSuggestion suggestion = ShrimpPriceBlogDraftSuggestion.builder()
                .title("Giá tôm hôm nay 03/09/2026: thị trường giữ nhịp ổn định")
                .excerpt("Excerpt")
                .marketSummary("Market summary")
                .seoClosing("SEO closing")
                .build();

        ShrimpPriceBlogDraftSuggestion normalized = normalizeSuggestion(suggestion);

        assertThat(normalized.getTitle()).isEqualTo(suggestion.getTitle());
    }

    private ShrimpPriceBlogDraftSuggestion normalizeSuggestion(
            ShrimpPriceBlogDraftSuggestion suggestion) throws Exception {
        Method method = ShrimpPriceBlogAutomationService.class.getDeclaredMethod(
                "normalizeSuggestion",
                ShrimpPriceBlogDraftSuggestion.class,
                String.class,
                String.class,
                List.class);
        method.setAccessible(true);
        return (ShrimpPriceBlogDraftSuggestion) method.invoke(
                service,
                suggestion,
                "03/09/2026",
                "dao động từ 100.000 đến 150.000 đồng/kg",
                List.of());
    }
}
