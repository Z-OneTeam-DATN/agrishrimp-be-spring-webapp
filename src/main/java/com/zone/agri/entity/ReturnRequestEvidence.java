package com.zone.agri.entity;

import com.zone.agri.entity.enums.ReturnEvidenceType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "return_request_evidences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class ReturnRequestEvidence extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 20, nullable = false)
    ReturnEvidenceType mediaType;

    @Column(name = "file_url", columnDefinition = "TEXT", nullable = false)
    String fileUrl;

    @Column(name = "public_id", length = 255)
    String publicId;

    @Column(name = "file_name", length = 255)
    String fileName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ReturnRequest returnRequest;
}
