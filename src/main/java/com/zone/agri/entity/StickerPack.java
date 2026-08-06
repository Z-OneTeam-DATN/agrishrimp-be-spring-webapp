package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import java.util.List;

@Entity
@Table(name = "sticker_packs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StickerPack extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, length = 100)
    String name;

    @Column(length = 255)
    String iconUrl;

    @OneToMany(mappedBy = "pack", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    List<Sticker> stickers;
}
