package com.zone.agri.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.AiKnowledgeCategory;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import com.zone.agri.repository.AiDiseaseKnowledgeRepository;
import com.zone.agri.repository.AiKnowledgeCategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.startup.seed-data.enabled", havingValue = "true", matchIfMissing = true)
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class AiDiseaseKnowledgeSeeder implements CommandLineRunner {

    private static final String CATEGORY_SLUG = "benh-tom-thuong-gap";
    private static final String GREEN_BIO_CONTACT_PHONE = "0982 266 640";
    private static final String SOURCE_LABEL = "Green Bio, FAO, WOAH và tài liệu kỹ thuật thủy sản";

    private final AiKnowledgeCategoryRepository categoryRepository;
    private final AiDiseaseKnowledgeRepository diseaseKnowledgeRepository;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        if (!Boolean.parseBoolean(environment.getProperty(
                "app.startup.seed-data.ai-knowledge.enabled",
                "true"))) {
            log.info(">>> SEED TRI THỨC AI DOCTOR ĐANG TẮT.");
            return;
        }

        AiKnowledgeCategory category = ensureDiseaseCategory();
        List<DiseaseSeed> seeds = List.of(
                bacterialGillSeed(),
                whiteSpotSeed(),
                whiteFecesSeed(),
                yellowHeadSeed());

        for (DiseaseSeed seed : seeds) {
            upsertDisease(category, seed);
        }

        log.info(">>> ĐÃ ĐỒNG BỘ {} PHÁC ĐỒ BỆNH TÔM CHO AI DOCTOR.", seeds.size());
    }

    private AiKnowledgeCategory ensureDiseaseCategory() {
        AiKnowledgeCategory category = categoryRepository.findBySlug(CATEGORY_SLUG)
                .orElseGet(() -> AiKnowledgeCategory.builder()
                        .slug(CATEGORY_SLUG)
                        .build());
        category.setName("Bệnh tôm thường gặp");
        category.setDescription("Kho tri thức nền cho 4 nhóm bệnh tôm AI Doctor đang nhận diện: viêm/đen mang, đốm trắng, phân trắng và đầu vàng.");
        category.setEnabled(true);
        category.setSortOrder(10);
        return categoryRepository.save(category);
    }

    private void upsertDisease(AiKnowledgeCategory category, DiseaseSeed seed) {
        AiDiseaseKnowledge disease = diseaseKnowledgeRepository.findByCode(seed.code())
                .orElseGet(AiDiseaseKnowledge::new);

        disease.setCode(seed.code());
        disease.setNameVi(seed.nameVi());
        disease.setNameEn(seed.nameEn());
        disease.setCategory(category);
        disease.setAliasesRaw(seed.aliasesRaw());
        disease.setSymptomKeywordsRaw(seed.symptomKeywordsRaw());
        disease.setSignsSummary(seed.signsSummary());
        disease.setCausesJson(toJson(seed.causes()));
        disease.setTreatmentStagesJson(toJson(seed.stages()));
        disease.setImageUrlsJson(toJson(List.of()));
        disease.setEngineerName(SOURCE_LABEL);
        disease.setEngineerPhone(GREEN_BIO_CONTACT_PHONE);
        disease.setConfidenceThreshold(seed.confidenceThreshold());
        disease.setMatchThreshold(seed.matchThreshold());
        disease.setEnabled(true);
        disease.setPriority(seed.priority());
        disease.setCanonical(true);
        disease.setStatus(AiKnowledgeStatus.APPROVED);
        disease.setReviewNote(null);

        diseaseKnowledgeRepository.save(disease);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể serialize dữ liệu seed AI Doctor", e);
        }
    }

    private DiseaseSeed bacterialGillSeed() {
        return new DiseaseSeed(
                "BG",
                "Bệnh viêm/đen mang",
                "Bacterial Gill Disease / Black Gill",
                "BG, viêm mang, đen mang, bệnh mang, mang đen, mang bẩn, bacterial gill disease, black gill disease",
                "mang đen, mang nâu, mang bẩn, mang sưng, tôm nổi đầu, tôm tấp mé, tôm giảm ăn, thiếu oxy, nước đục, đáy ao dơ, khí độc cao",
                "Mang tôm chuyển xám nâu hoặc đen, bám bẩn, tôm giảm ăn, bơi yếu hoặc tập trung nơi nhiều oxy. Cần xem đây là hội chứng liên quan môi trường, chất hữu cơ và tác nhân cơ hội; không tự dùng kháng sinh khi chưa có xét nghiệm.",
                List.of(
                        "Chất hữu cơ, bùn đáy, thức ăn dư và tảo tàn làm tăng nhu cầu oxy, NH3/NO2/H2S và chất lơ lửng bám lên mang.",
                        "Vi khuẩn, nấm hoặc sinh vật bám mang phát triển mạnh khi nước ao xấu, mật độ cao hoặc oxy hòa tan thấp.",
                        "Thay đổi đột ngột pH, độ mặn, kiềm, nhiệt độ hoặc dùng hóa chất mạnh khiến mang tổn thương."
                ),
                List.of(
                        stage("1. Khoanh vùng và đo nhanh môi trường",
                                List.of(
                                        "Kiểm tra ngay DO sáng sớm và chiều, pH, kiềm, NH3, NO2, H2S; soi mang 10-20 con ở nhiều vị trí ao.",
                                        "Giảm 20-40% lượng ăn trong 24-48 giờ nếu tôm bỏ ăn hoặc sàng còn thức ăn; vớt bỏ tôm chết và chất nổi.",
                                        "Không tạt hóa chất/kháng sinh theo kinh nghiệm khi chưa biết nguyên nhân, vì có thể làm tôm sốc thêm và tạo tồn dư."
                                ),
                                List.of("Test nhanh NH3/NO2/H2S/DO/pH", "Kính soi mẫu mang")),
                        stage("2. Phục hồi oxy, giảm chất bám mang",
                                List.of(
                                        "Tăng quạt nước/sục khí liên tục, ưu tiên khu vực tôm tập trung và góc ao dễ thiếu oxy.",
                                        "Xi phông bùn đáy, phân và thức ăn dư; thay 10-20% nước đã xử lý nếu nước quá đục hoặc khí độc cao.",
                                        "Giữ kiềm, pH và độ mặn ổn định; tránh thay nước ồ ạt làm tôm sốc."
                                ),
                                List.of("Vôi/khoáng nâng kiềm dùng theo kết quả đo", "Khoáng điện giải cho tôm stress")),
                        stage("3. Hạ tải hữu cơ và vi khuẩn cơ hội",
                                List.of(
                                        "Sau khi ổn định oxy, dùng chế phẩm vi sinh xử lý đáy/nước để phân hủy hữu cơ và cạnh tranh vi khuẩn có hại.",
                                        "Nếu test vi khuẩn hoặc phòng lab cho thấy Vibrio cao, ưu tiên biện pháp sinh học/ức chế Vibrio; chỉ dùng thuốc đặc trị khi có chỉ định kỹ sư/thú y thủy sản.",
                                        "Theo dõi màu mang mỗi ngày; nếu mang không sáng dần sau 2-3 ngày hoặc tôm chết tăng, gửi mẫu xét nghiệm."
                                ),
                                List.of("Bio Clear - Green Bio", "Bio 102 - Green Bio", "Phage Pro - Green Bio")),
                        stage("4. Phòng tái phát",
                                List.of(
                                        "Duy trì xi phông định kỳ, quản lý sàng ăn, mật độ phù hợp và DO đáy ổn định trên ngưỡng an toàn.",
                                        "Lọc/lắng nước cấp, không cấp trực tiếp nước nhiều phù sa hoặc mầm bệnh vào ao nuôi.",
                                        "Ghi nhật ký chỉ số nước, lượng ăn và màu mang để phát hiện sớm trước khi tôm nổi đầu."
                                ),
                                List.of("Bộ test môi trường định kỳ", "Men vi sinh đáy/nước dùng định kỳ")),
                        sources("Nguồn tham khảo",
                                List.of(
                                        "FAO: môi trường ao xấu, khí độc/chất hữu cơ và oxy thấp là yếu tố chính trong hội chứng mang đen/viêm mang ở tôm.",
                                        "FAO Cultured Aquatic Species - Penaeus vannamei: kiểm soát bệnh dựa vào SPF, an toàn sinh học, xử lý nước, sàng lọc PCR và quản lý ao.",
                                        "Green Bio: sản phẩm Phage Pro, Bio 102, Bio Clear hỗ trợ kiểm soát Vibrio và môi trường ao; dùng theo nhãn và tư vấn kỹ sư.",
                                        "https://www.fao.org/4/AC210E/AC210E00.htm",
                                        "https://www.fao.org/fishery/docs/DOCUMENT/aquaculture/CulturedSpecies/file/en/en_whitelegshrimp.htm",
                                        "https://tapdoangreenbio.vn/san-pham/phage-pro-giai-phap-sinh-hoc-dot-pha-trong-dieu-tri-vibrio-tren-tom/"
                                ))),
                0.65D,
                0.35D,
                100);
    }

    private DiseaseSeed whiteSpotSeed() {
        return new DiseaseSeed(
                "WSSV",
                "Bệnh đốm trắng",
                "White Spot Syndrome Virus",
                "WSSV, đốm trắng, bệnh đốm trắng, white spot disease, white spot syndrome virus",
                "đốm trắng vỏ tôm, tôm đỏ thân, tôm chết nhanh, chết hàng loạt, giảm ăn đột ngột, tôm bơi lờ đờ, tôm tấp mé",
                "Bệnh virus nguy hiểm, có thể làm tôm chết nhanh và hàng loạt. Không có thuốc điều trị đặc hiệu cho WSSV; phác đồ đúng là xác nhận nhanh, khoanh vùng, giảm stress khi chờ kết quả và xử lý ao theo an toàn sinh học.",
                List.of(
                        "White spot syndrome virus lây qua tôm giống/mầm bệnh trong nước, giáp xác trung gian, thức ăn tươi sống, dụng cụ và nước thải chưa xử lý.",
                        "Biến động nhiệt độ, độ mặn, pH, oxy thấp, mật độ cao hoặc sốc môi trường có thể làm bệnh bùng phát mạnh.",
                        "Tôm nhiễm bệnh thải virus ra nước; nếu xả thẳng hoặc dùng chung dụng cụ, mầm bệnh lan rất nhanh sang ao khác."
                ),
                List.of(
                        stage("1. Nghi ngờ WSSV: cách ly và xác nhận",
                                List.of(
                                        "Ngưng chuyển nước, tôm, dụng cụ và người giữa ao nghi bệnh với các ao khác.",
                                        "Lấy mẫu tôm yếu/mới chết để xét nghiệm PCR WSSV; ghi lại tỷ lệ chết, nhiệt độ, pH, độ mặn, DO và lượng ăn 3 ngày gần nhất.",
                                        "Tăng oxy, vớt tôm chết liên tục và giảm/ngưng cho ăn nếu tôm không bắt mồi để hạn chế ô nhiễm đáy."
                                ),
                                List.of("Xét nghiệm PCR WSSV", "Vợt/bao chứa tôm chết xử lý riêng")),
                        stage("2. Khi chờ kết quả: giảm stress, không quảng cáo là trị virus",
                                List.of(
                                        "Giữ DO ổn định, hạn chế thay nước mạnh, không tạt hóa chất gây sốc.",
                                        "Chỉ dùng vi sinh/môi trường hoặc vitamin-khoáng ở liều hỗ trợ theo nhãn; không xem đây là thuốc diệt WSSV.",
                                        "Nếu tôm đạt cỡ thương phẩm và chết tăng nhanh, chuẩn bị phương án thu khẩn cấp theo tư vấn kỹ sư và yêu cầu địa phương."
                                ),
                                List.of("Bio 102 - Green Bio", "Bio Clear - Green Bio", "Vitamin C, beta-glucan, khoáng điện giải")),
                        stage("3. Ao dương tính hoặc chết nhanh: xử lý an toàn sinh học",
                                List.of(
                                        "Không xả nước ao bệnh trực tiếp ra kênh rạch; nước thải cần được khử trùng/lắng theo hướng dẫn kỹ thuật tại địa phương.",
                                        "Nếu không thể thu hoạch an toàn, loại bỏ đàn bệnh và khử trùng ao, dụng cụ, bờ ao; phơi đáy, dọn bùn và diệt giáp xác trung gian trước vụ mới.",
                                        "Báo kỹ sư hoặc cán bộ thú y thủy sản khi có chết nhanh/hàng loạt để được hướng dẫn xử lý đúng quy định."
                                ),
                                List.of("Chlorine/vôi xử lý ao theo chỉ định kỹ sư", "Dụng cụ khử trùng riêng cho ao bệnh")),
                        stage("4. Tái thả sau WSSV",
                                List.of(
                                        "Chỉ thả giống SPF hoặc âm tính PCR với WSSV; kiểm tra nguồn postlarvae, nước cấp và ao lắng.",
                                        "Lọc, lắng và khử trùng nước cấp; chắn cua/còng/chim và không dùng thức ăn tươi sống chưa xử lý.",
                                        "Duy trì an toàn sinh học theo từng ao: dụng cụ riêng, hố sát trùng, ghi nhật ký môi trường và theo dõi tỷ lệ chết hằng ngày."
                                ),
                                List.of("Tôm giống SPF/PCR âm tính WSSV", "Lưới chắn cua/còng/chim", "Bộ test môi trường")),
                        sources("Nguồn tham khảo",
                                List.of(
                                        "WOAH: white spot disease là bệnh virus ở giáp xác, quản lý bằng kiểm soát mầm bệnh và an toàn sinh học.",
                                        "FAO: với WSSV, biện pháp chính là SPF, sàng lọc PCR, xử lý nước và quản lý an toàn sinh học; không có thuốc điều trị đặc hiệu.",
                                        "Green Bio: Bio 102/Bio Clear là hỗ trợ môi trường, không phải thuốc trị WSSV.",
                                        "https://www.woah.org/en/disease/white-spot-disease/",
                                        "https://www.fao.org/fishery/docs/DOCUMENT/aquaculture/CulturedSpecies/file/en/en_whitelegshrimp.htm",
                                        "https://tapdoangreenbio.vn/san-pham/bio-102-gia-tang-he-vi-sinh-co-loi-trong-duong-ruot-tom-va-lam-sach-moi-truong-ao-nuoi/"
                                ))),
                0.65D,
                0.40D,
                98);
    }

    private DiseaseSeed whiteFecesSeed() {
        return new DiseaseSeed(
                "WSSV_BG",
                "Bệnh phân trắng",
                "White Feces Syndrome / White Feces Disease",
                "WSSV_BG, phân trắng, bệnh phân trắng, hội chứng phân trắng, white feces syndrome, white feces disease, WFS, WFD",
                "phân trắng nổi, dây phân trắng, đường ruột đứt khúc, ruột trống, gan tụy nhợt, tôm giảm ăn, tôm chậm lớn, tôm mềm vỏ, nước nhớt, đáy dơ",
                "Phân trắng là hội chứng đa yếu tố liên quan đường ruột, gan tụy, đáy ao, thức ăn, Vibrio/EHP và stress môi trường. Không có một thuốc đặc hiệu duy nhất; phác đồ cần giảm tải đường ruột, làm sạch đáy, kiểm tra mầm bệnh và phục hồi hệ vi sinh.",
                List.of(
                        "Đáy ao bẩn, thức ăn dư, tảo tàn và khí độc làm rối loạn tiêu hóa, đứt ruột và giảm bắt mồi.",
                        "Vibrio, ký sinh trùng/EHP, vi bào tử trùng hoặc rối loạn hệ vi sinh đường ruột có thể tham gia gây hội chứng.",
                        "Thức ăn kém chất lượng, bảo quản ẩm mốc, thay đổi môi trường đột ngột hoặc mật độ cao làm bệnh nặng hơn."
                ),
                List.of(
                        stage("1. Cắt tải đường ruột trong 24-48 giờ",
                                List.of(
                                        "Giảm 30-50% lượng ăn, kiểm tra sàng mỗi cữ; nếu tôm bỏ ăn rõ, tạm ngưng 1-2 cữ để tránh thức ăn dư.",
                                        "Vớt dây phân trắng nổi, xi phông phân và bùn đáy; tăng oxy trước khi can thiệp môi trường.",
                                        "Kiểm tra ruột, gan tụy và màu phân trên 30-50 con; ghi tỷ lệ tôm ruột trống/đứt khúc."
                                ),
                                List.of("Sàng ăn", "Bộ test DO/pH/kiềm/NH3/NO2")),
                        stage("2. Tìm nguyên nhân chính",
                                List.of(
                                        "Đo pH, kiềm, DO, NH3, NO2, H2S; soi phân/ruột nếu có điều kiện.",
                                        "Gửi mẫu xét nghiệm EHP và kiểm tra Vibrio khi phân trắng kéo dài, tôm chậm lớn hoặc chết rải rác.",
                                        "Kiểm tra thức ăn, hạn dùng, mùi mốc và điều kiện bảo quản; loại bỏ thức ăn nghi hỏng."
                                ),
                                List.of("Xét nghiệm EHP", "Cấy/định lượng Vibrio", "Bộ soi mẫu ruột/phân")),
                        stage("3. Làm sạch đáy, ổn định vi sinh và ức chế Vibrio",
                                List.of(
                                        "Dùng vi sinh xử lý nước/đáy sau khi DO ổn định; không tạt vi sinh khi ao đang thiếu oxy.",
                                        "Nếu Vibrio cao, dùng sản phẩm sinh học/phage theo nhãn để giảm áp lực Vibrio; theo dõi đáp ứng qua phân và lượng ăn sau 48-72 giờ.",
                                        "Không phối trộn nhiều hóa chất cùng lúc; nếu cần diệt khuẩn nước, tách thời điểm với vi sinh và hỏi kỹ sư."
                                ),
                                List.of("Bio Clear - Green Bio", "Bio 102 - Green Bio", "Phage Pro - Green Bio")),
                        stage("4. Phục hồi gan ruột và lên ăn lại",
                                List.of(
                                        "Khi phân trắng giảm và tôm bắt mồi tốt hơn, tăng thức ăn từ từ 10-15% mỗi ngày theo sàng.",
                                        "Bổ sung men/enzym, vitamin C, khoáng và sản phẩm hỗ trợ gan ruột 5-7 ngày theo nhãn.",
                                        "Nếu sau 5-7 ngày vẫn phân trắng nặng hoặc tôm mềm vỏ/chậm lớn, cần xét nghiệm lại EHP/Vibrio và rà soát toàn bộ quy trình ao."
                                ),
                                List.of("B52 - Green Bio", "Men tiêu hóa/enzym", "Vitamin C, khoáng điện giải")),
                        stage("5. Phòng bệnh sau khi ổn định",
                                List.of(
                                        "Duy trì xi phông, quản lý sàng ăn, men vi sinh định kỳ và kiểm tra khí độc; không để thức ăn dư kéo dài qua đêm.",
                                        "Dùng giống sạch bệnh, kiểm soát EHP từ trại giống và khử trùng dụng cụ giữa các ao.",
                                        "Giữ mật độ nuôi phù hợp khả năng quạt nước và hệ thống xử lý đáy."
                                ),
                                List.of("Tôm giống xét nghiệm EHP âm tính", "Men vi sinh định kỳ")),
                        sources("Nguồn tham khảo",
                                List.of(
                                        "Review MDPI/Fishes: WFS là hội chứng đa yếu tố, chưa có thuốc đặc hiệu; quản lý bằng thực hành nuôi tốt, nước sạch, an toàn sinh học, prebiotic/probiotic và miễn dịch.",
                                        "Green Bio: bài phác đồ phân trắng gợi ý phối hợp xử lý Vibrio, làm sạch đáy/nước và hỗ trợ gan ruột bằng Phage Pro, Bio Clear, Bio 102, B52.",
                                        "https://www.mdpi.com/2410-3888/7/6/302",
                                        "https://tapdoangreenbio.vn/benh-phan-trang-tren-tom/",
                                        "https://tapdoangreenbio.vn/san-pham/phage-pro-giai-phap-sinh-hoc-dot-pha-trong-dieu-tri-vibrio-tren-tom/",
                                        "https://tapdoangreenbio.vn/san-pham/b52-ho-tro-giai-doc-gan-tom-kich-thich-tom-bat-moi-manh/"
                                ))),
                0.65D,
                0.35D,
                96);
    }

    private DiseaseSeed yellowHeadSeed() {
        return new DiseaseSeed(
                "YELLOWHEAD",
                "Bệnh đầu vàng",
                "Yellow Head Disease / Yellow Head Virus",
                "Yellowhead, YELLOWHEAD, đầu vàng, bệnh đầu vàng, yellow head disease, yellow head virus, YHV",
                "đầu vàng, gan tụy vàng, tôm ăn mạnh rồi ngừng ăn, tôm chết nhanh, chết hàng loạt, tôm bơi ven bờ, thân nhợt hoặc đỏ nhẹ, mang vàng",
                "Bệnh đầu vàng là bệnh virus nguy hiểm, thường diễn biến rất nhanh. Không có thuốc điều trị đặc hiệu; cần xác nhận bằng xét nghiệm, khoanh vùng, giảm phát tán và xử lý an toàn sinh học.",
                List.of(
                        "Yellow head virus lây qua tôm nhiễm bệnh, vật chủ giáp xác, nước, dụng cụ và vận chuyển tôm giữa ao.",
                        "Ao mật độ cao, biến động môi trường, oxy thấp hoặc stress kéo dài làm bệnh bùng phát nhanh hơn.",
                        "Tôm có thể ăn mạnh bất thường trước khi đột ngột ngừng ăn và chết nhanh."
                ),
                List.of(
                        stage("1. Nghi ngờ YHV: dừng lây lan ngay",
                                List.of(
                                        "Ngưng chuyển tôm, nước, lưới, vợt và dụng cụ từ ao nghi bệnh sang ao khác.",
                                        "Lấy mẫu tôm yếu/mới chết gửi xét nghiệm YHV; ghi thời điểm tôm ngừng ăn, tỷ lệ chết và thông số môi trường.",
                                        "Tăng oxy, vớt tôm chết liên tục và giảm/ngưng cho ăn nếu tôm không bắt mồi."
                                ),
                                List.of("Xét nghiệm PCR YHV", "Dụng cụ riêng cho ao nghi bệnh")),
                        stage("2. Quản lý khi chưa có kết quả",
                                List.of(
                                        "Giữ môi trường ổn định, tránh thay nước hoặc tạt hóa chất mạnh làm tôm sốc.",
                                        "Bổ sung vi sinh môi trường/vitamin-khoáng chỉ nhằm giảm stress và ổn định ao, không xem là thuốc trị YHV.",
                                        "Nếu tôm đạt cỡ bán và tỷ lệ chết tăng nhanh, hỏi kỹ sư/cơ quan địa phương về phương án thu khẩn cấp an toàn."
                                ),
                                List.of("Bio 102 - Green Bio", "Bio Clear - Green Bio", "Vitamin C, beta-glucan, khoáng điện giải")),
                        stage("3. Ao dương tính hoặc chết hàng loạt",
                                List.of(
                                        "Không xả nước ao bệnh trực tiếp ra môi trường; xử lý nước, bùn, xác tôm và dụng cụ theo hướng dẫn kỹ thuật địa phương.",
                                        "Khử trùng ao, dụng cụ và khu vực thao tác; phơi đáy, dọn bùn và loại bỏ giáp xác trung gian trước khi cải tạo vụ mới.",
                                        "Theo dõi các ao lân cận mỗi ngày, dùng dụng cụ riêng và sát trùng lối ra vào."
                                ),
                                List.of("Chlorine/vôi xử lý ao theo chỉ định kỹ sư", "Hố sát trùng và dụng cụ riêng")),
                        stage("4. Phòng bệnh vụ sau",
                                List.of(
                                        "Thả giống sạch bệnh, ưu tiên nguồn có xét nghiệm âm tính với YHV và các bệnh virus chính.",
                                        "Lọc/lắng/khử trùng nước cấp, kiểm soát cua/còng/chim và không dùng thức ăn tươi sống chưa xử lý.",
                                        "Duy trì an toàn sinh học từng ao; không dùng chung vợt, chài, ống siphon giữa ao bệnh và ao khỏe."
                                ),
                                List.of("Tôm giống PCR âm tính YHV", "Lưới chắn vật chủ trung gian", "Bộ sát trùng dụng cụ")),
                        sources("Nguồn tham khảo",
                                List.of(
                                        "WOAH: Yellow head disease là bệnh virus ở giáp xác cần quản lý bằng kiểm soát mầm bệnh và an toàn sinh học.",
                                        "GOV.UK/Cefas: không có điều trị đặc hiệu cho yellow head disease; kiểm soát bằng cách loại bỏ nguồn nhiễm và an toàn sinh học.",
                                        "FAO: bệnh virus trên tôm chủ yếu phòng bằng SPF, xét nghiệm, xử lý nước và quản lý ao.",
                                        "https://www.woah.org/en/disease/yellow-head-disease/",
                                        "https://www.gov.uk/government/publications/yellow-head-disease",
                                        "https://www.fao.org/fishery/docs/DOCUMENT/aquaculture/CulturedSpecies/file/en/en_whitelegshrimp.htm"
                                ))),
                0.65D,
                0.35D,
                94);
    }

    private StageSeed stage(String title, List<String> instructions, List<String> extraProductNames) {
        return new StageSeed(title, instructions, List.of(), extraProductNames);
    }

    private StageSeed sources(String title, List<String> references) {
        return new StageSeed(title, references, List.of(), List.of());
    }

    private record DiseaseSeed(
            String code,
            String nameVi,
            String nameEn,
            String aliasesRaw,
            String symptomKeywordsRaw,
            String signsSummary,
            List<String> causes,
            List<StageSeed> stages,
            Double confidenceThreshold,
            Double matchThreshold,
            Integer priority) {
    }

    private record StageSeed(
            String stageTitle,
            List<String> instructions,
            List<Long> productIds,
            List<String> extraProductNames) {
    }
}
