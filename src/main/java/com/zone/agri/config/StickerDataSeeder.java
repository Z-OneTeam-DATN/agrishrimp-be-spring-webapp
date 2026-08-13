package com.zone.agri.config;

import com.zone.agri.entity.Sticker;
import com.zone.agri.entity.StickerPack;
import com.zone.agri.repository.StickerPackRepository;
import com.zone.agri.repository.StickerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.startup.seed-data.stickers.enabled", havingValue = "true", matchIfMissing = true)
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class StickerDataSeeder implements CommandLineRunner {

    private final StickerPackRepository stickerPackRepository;
    private final StickerRepository stickerRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (stickerPackRepository.count() > 0) {
            log.info("Sticker packs already seeded, skipping.");
            return;
        }

        log.info(">>> SEEDING RICH STICKER LIBRARY...");

        // 1. Noto Emoji
        StickerPack notoPack = stickerPackRepository.save(StickerPack.builder()
                .name("Noto Emoji")
                .iconUrl("https://fonts.gstatic.com/s/e/notoemoji/latest/1f60d/512.gif")
                .build());

        List<Sticker> notoStickers = new ArrayList<>();
        notoStickers.add(Sticker.builder().pack(notoPack).label("Haha").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f602/512.gif").build());
        notoStickers.add(Sticker.builder().pack(notoPack).label("Yêu").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f60d/512.gif").build());
        notoStickers.add(Sticker.builder().pack(notoPack).label("Thích").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f44d/512.gif").build());
        notoStickers.add(Sticker.builder().pack(notoPack).label("Khóc").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f62d/512.gif").build());
        notoStickers.add(Sticker.builder().pack(notoPack).label("Suy nghĩ").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f914/512.gif").build());
        notoStickers.add(Sticker.builder().pack(notoPack).label("Vỗ tay").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f44f/512.gif").build());
        notoStickers.add(Sticker.builder().pack(notoPack).label("Lửa").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f525/512.gif").build());
        notoStickers.add(Sticker.builder().pack(notoPack).label("Wow").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f62e/512.gif").build());
        notoStickers.add(Sticker.builder().pack(notoPack).label("Tiệc").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f389/512.gif").build());
        notoStickers.add(Sticker.builder().pack(notoPack).label("Giận dữ").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f621/512.gif").build());
        notoStickers.add(Sticker.builder().pack(notoPack).label("Đổ mồ hôi").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f605/512.gif").build());
        notoStickers.add(Sticker.builder().pack(notoPack).label("Buồn ngủ").url("https://fonts.gstatic.com/s/e/notoemoji/latest/1f62a/512.gif").build());
        stickerRepository.saveAll(notoStickers);

        // 2. Dino
        StickerPack dinoPack = stickerPackRepository.save(StickerPack.builder()
                .name("Dinosaur")
                .iconUrl("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExM3hxdWZ6cTNuOW5udWZndXp4OGc0czdud3I4ZmpkcHRoc2p1cWc3NiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/jI2mzYZtwqF1KKQA2d/giphy.gif")
                .build());

        List<Sticker> dinoStickers = new ArrayList<>();
        dinoStickers.add(Sticker.builder().pack(dinoPack).label("Khủng long vui vẻ").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExM3hxdWZ6cTNuOW5udWZndXp4OGc0czdud3I4ZmpkcHRoc2p1cWc3NiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/jI2mzYZtwqF1KKQA2d/giphy.gif").build());
        dinoStickers.add(Sticker.builder().pack(dinoPack).label("Khủng long đáng yêu").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExMnU4aDB2ZHBmdThidmdrNXdjaWZibWphMTBoazYyODNmdWdrOTZ6bCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/JmDsg11lU4WaUrZkmk/giphy.gif").build());
        dinoStickers.add(Sticker.builder().pack(dinoPack).label("Khủng long đang ăn").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExOTV2cTBvMHloMDhmdTZ6YW95M3Vsd2tvdDVqZngybWh5ZHJ2eXVvcyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/Wwddp29U3sw8A/giphy.gif").build());
        dinoStickers.add(Sticker.builder().pack(dinoPack).label("Khủng long buồn ngủ").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExNWVjcTZndzdrZ2szdnE1enZhdGtxamg2Z3FodjV6bTZvMGgwaG9idSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/l0CLUG2n7wPf6s33G/giphy.gif").build());
        dinoStickers.add(Sticker.builder().pack(dinoPack).label("Khủng long chạy bộ").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHgzcWhzcm1odHoxcm9iaWRoZjhsbzhsbjBvczdxcmlmdXpxYnVyNCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/3o7TKuylrX8kTdOBEI/giphy.gif").build());
        dinoStickers.add(Sticker.builder().pack(dinoPack).label("Khủng long buồn").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExaTJmaG5ubHZsNmF2MGdtam4xaHNhdXRmdWJ6czJ2eHhhc2szZWlyMSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/7SF5scGB2AFrCJJasI/giphy.gif").build());
        stickerRepository.saveAll(dinoStickers);

        // 3. Cute Cat
        StickerPack catPack = stickerPackRepository.save(StickerPack.builder()
                .name("Cute Cat")
                .iconUrl("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExN3JpNGtxZWsybmk0ZGlhdDFvNXdkaHBkZGRrZHptamg2am1vZW5rdyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/mlvseq9yvZhba/giphy.gif")
                .build());

        List<Sticker> catStickers = new ArrayList<>();
        catStickers.add(Sticker.builder().pack(catPack).label("Mèo nhảy múa").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExN3JpNGtxZWsybmk0ZGlhdDFvNXdkaHBkZGRrZHptamg2am1vZW5rdyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/mlvseq9yvZhba/giphy.gif").build());
        catStickers.add(Sticker.builder().pack(catPack).label("Mèo thả tim").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExdHpsYWthOXlpcHFtOHZ4NXF1dm11ejNlMmdvOHU3ejNlc3E4MGZ2dyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/111ebonMs9xYoo/giphy.gif").build());
        catStickers.add(Sticker.builder().pack(catPack).label("Mèo bối rối").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExdnQxOGoxdWU0bWdydWswdm90ejZ6azUzdHRzNDV6MTNnMXJ6NXdvaCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/26gR2fIIciCo8ue3u/giphy.gif").build());
        catStickers.add(Sticker.builder().pack(catPack).label("Mèo ngủ ngon").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExNGdyNjV1bnl4NmpsdzU4M3Vncmh2YWpvdDF3dTAxczBnaTlpZWZscSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/12PA1eI8FBqEUM/giphy.gif").build());
        catStickers.add(Sticker.builder().pack(catPack).label("Mèo mếu máo").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExNmxrNjJxdHNzdW50dHBmbmxjNHBsbTBkMnhoZnJjcWhkMnFjcHZsZSZlcD12MV9pbnRlcm5hbF9naWZfYSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/X83YJN3uf21TEc0RxK/giphy.gif").build());
        catStickers.add(Sticker.builder().pack(catPack).label("Mèo nổi giận").url("https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExN3lzdzgwcTkwZzBsczR4dmFqMHppZThqenUweXo2Y2Y2dmVnd2g1YiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/AC1m4EBK7YURy/giphy.gif").build());
        stickerRepository.saveAll(catStickers);

        // 4. Quby / Pentol
        StickerPack qubyPack = stickerPackRepository.save(StickerPack.builder()
                .name("Quby Pentol")
                .iconUrl("https://media.tenor.com/t335r-F6F6YAAAAM/quby-pentol.gif")
                .build());

        List<Sticker> qubyStickers = new ArrayList<>();
        qubyStickers.add(Sticker.builder().pack(qubyPack).label("Vui vẻ").url("https://media.tenor.com/t335r-F6F6YAAAAM/quby-pentol.gif").build());
        qubyStickers.add(Sticker.builder().pack(qubyPack).label("Khóc nhè").url("https://media.tenor.com/bcrL8-1WkHMAAAAM/quby-pentol.gif").build());
        qubyStickers.add(Sticker.builder().pack(qubyPack).label("Thả tim").url("https://media.tenor.com/tq9364g6C90AAAAM/pentol-sticker.gif").build());
        qubyStickers.add(Sticker.builder().pack(qubyPack).label("Ăn uống").url("https://media.tenor.com/ZzR8-q7HuxEAAAAM/quby-pentol.gif").build());
        qubyStickers.add(Sticker.builder().pack(qubyPack).label("Hờn dỗi").url("https://media.tenor.com/eBifLsz3Z9YAAAAM/pentol-quby.gif").build());
        qubyStickers.add(Sticker.builder().pack(qubyPack).label("Ngủ ngon").url("https://media.tenor.com/e-yF92f03fMAAAAM/quby-pentol.gif").build());
        qubyStickers.add(Sticker.builder().pack(qubyPack).label("Đấm nè").url("https://media.tenor.com/Qj0479g0W6YAAAAM/quby-pentol.gif").build());
        stickerRepository.saveAll(qubyStickers);

        // 5. Pusheen Cat
        StickerPack pusheenPack = stickerPackRepository.save(StickerPack.builder()
                .name("Pusheen Cat")
                .iconUrl("https://media.tenor.com/s6_6F3B1tH4AAAAM/pusheen-hi.gif")
                .build());

        List<Sticker> pusheenStickers = new ArrayList<>();
        pusheenStickers.add(Sticker.builder().pack(pusheenPack).label("Chào bạn").url("https://media.tenor.com/s6_6F3B1tH4AAAAM/pusheen-hi.gif").build());
        pusheenStickers.add(Sticker.builder().pack(pusheenPack).label("Bắn tim").url("https://media.tenor.com/e7q3uXQoNlIAAAAM/pusheen-heart.gif").build());
        pusheenStickers.add(Sticker.builder().pack(pusheenPack).label("Ăn pizza").url("https://media.tenor.com/4Jp1lX8hE-sAAAAM/pusheen-pizza.gif").build());
        pusheenStickers.add(Sticker.builder().pack(pusheenPack).label("Nhảy múa").url("https://media.tenor.com/47s9aXkE_UIAAAAM/pusheen-happy.gif").build());
        pusheenStickers.add(Sticker.builder().pack(pusheenPack).label("Khóc").url("https://media.tenor.com/5yG5626Ff3kAAAAM/pusheen-cry.gif").build());
        pusheenStickers.add(Sticker.builder().pack(pusheenPack).label("Nằm lười").url("https://media.tenor.com/r_z_h8099GIAAAAM/pusheen-sleep.gif").build());
        stickerRepository.saveAll(pusheenStickers);

        log.info(">>> Rich sticker library seeded successfully with 5 packs.");
    }
}
