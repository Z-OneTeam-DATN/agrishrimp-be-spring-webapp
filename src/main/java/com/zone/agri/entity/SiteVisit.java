package com.zone.agri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

// Lượt xem trang do middleware Next.js tự ghi nhận (visitor_id ẩn danh qua cookie),
// dùng để hiển thị "Lượt truy cập" trên trang tổng quan khi chưa tích hợp GA4 thật.
@Entity
@Table(
        name = "site_visits",
        indexes = {
                @Index(name = "idx_site_visits_visited_at", columnList = "visited_at"),
                @Index(name = "idx_site_visits_visitor_id", columnList = "visitor_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SiteVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "visitor_id", length = 64, nullable = false)
    String visitorId;

    @Column(name = "path", length = 500)
    String path;

    @Column(name = "user_agent", length = 500)
    String userAgent;

    @Column(name = "visited_at", nullable = false)
    LocalDateTime visitedAt;
}
