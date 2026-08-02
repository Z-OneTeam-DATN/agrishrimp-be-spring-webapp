package com.zone.agri.service;

import com.zone.agri.entity.SiteVisit;
import com.zone.agri.repository.SiteVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VisitService {

    private static final int MAX_VISITOR_ID_LENGTH = 64;
    private static final int MAX_TEXT_LENGTH = 500;

    private final SiteVisitRepository siteVisitRepository;

    @Transactional
    public void trackVisit(String visitorId, String path, String userAgent) {
        if (visitorId == null || visitorId.isBlank() || isLikelyBot(userAgent)) {
            return;
        }

        siteVisitRepository.save(SiteVisit.builder()
                .visitorId(truncate(visitorId, MAX_VISITOR_ID_LENGTH))
                .path(truncate(path, MAX_TEXT_LENGTH))
                .userAgent(truncate(userAgent, MAX_TEXT_LENGTH))
                .visitedAt(LocalDateTime.now())
                .build());
    }

    public record VisitInsights(long visitors, long pageViews) {
    }

    public VisitInsights getInsights(LocalDateTime start, LocalDateTime end) {
        return new VisitInsights(
                siteVisitRepository.countDistinctVisitors(start, end),
                siteVisitRepository.countPageViews(start, end));
    }

    // Lọc bot cơ bản qua User-Agent — không chính xác bằng GA (không có captcha/challenge,
    // dễ bị bot giả UA người dùng thật qua mặt) nhưng loại được phần lớn crawler/script rõ ràng.
    private boolean isLikelyBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true;
        }
        String ua = userAgent.toLowerCase();
        return ua.contains("bot")
                || ua.contains("spider")
                || ua.contains("crawl")
                || ua.contains("headless")
                || ua.contains("curl")
                || ua.contains("wget")
                || ua.contains("python-requests")
                || ua.contains("axios")
                || ua.contains("postman");
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
