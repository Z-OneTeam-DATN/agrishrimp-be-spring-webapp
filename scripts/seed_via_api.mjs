/**
 * SEED SCRIPT — Tạo Categories + Products qua REST API
 * ======================================================
 * Dùng khi muốn sync dữ liệu qua web thay vì chạy SQL trực tiếp.
 *
 * HƯỚNG DẪN CHẠY:
 *  1. Đăng nhập vào web admin (https://agrishrimp.io.vn/admin)
 *  2. Mở DevTools → Application → Cookies → copy giá trị cookie "accessToken"
 *     HOẶC: DevTools → Network → bất kỳ request API → Headers → Authorization: Bearer <token>
 *  3. Chạy:
 *       TOKEN=<paste_token_here> node scripts/seed_via_api.mjs
 *     Hoặc trên Windows PowerShell:
 *       $env:TOKEN="paste_token_here"; node scripts/seed_via_api.mjs
 */

const BASE_URL = process.env.API_URL || "https://api.agrishrimp.io.vn/api";
const TOKEN    = process.env.TOKEN;

if (!TOKEN) {
  console.error("❌ Thiếu TOKEN. Chạy: TOKEN=<jwt> node scripts/seed_via_api.mjs");
  process.exit(1);
}

const headers = {
  "Content-Type": "application/json",
  "Authorization": `Bearer ${TOKEN}`,
};

// ─── HELPER ─────────────────────────────────────────────────────────────────

async function post(path, body) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`POST ${path} → ${res.status}: ${text.slice(0, 300)}`);
  }
  return JSON.parse(text);
}

function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
}

// ─── STEP 1: CATEGORIES ─────────────────────────────────────────────────────

async function seedCategories() {
  console.log("\n📁 Tạo Categories...");

  // Parent
  const parent = await post("/categories", {
    name: "Thuốc & Chế phẩm thủy sản",
    parentId: null,
    status: "ACTIVE",
    imageUrl: "",
  });
  const parentId = parent?.data?.id ?? parent?.id;
  console.log(`  ✅ Parent: "Thuốc & Chế phẩm thủy sản" → id=${parentId}`);

  // Sub-categories — tên phải chứa keyword khớp AiDoctorDiseaseProductMapping
  const subs = [
    "Kháng sinh thủy sản",
    "Men vi sinh / Probiotic",
    "Hỗ trợ gan & Đường ruột",
    "Vitamin & Khoáng chất thủy sản",
    "Tăng cường miễn dịch tôm",
    "Hóa chất & Diệt khuẩn ao nuôi",
    "Vôi & Khoáng xử lý ao",
    "Sản phẩm hỗ trợ tăng trưởng",
  ];

  const categoryMap = {}; // name → id
  for (const name of subs) {
    try {
      const res = await post("/categories", { name, parentId, status: "ACTIVE", imageUrl: "" });
      const id = res?.data?.id ?? res?.id;
      categoryMap[name] = id;
      console.log(`  ✅ Sub: "${name}" → id=${id}`);
    } catch (e) {
      console.warn(`  ⚠️  "${name}": ${e.message} — bỏ qua, lấy ID từ DB nếu đã tồn tại`);
      // Nếu đã tồn tại, lấy từ GET
      try {
        const listRes = await fetch(`${BASE_URL}/categories?keyword=${encodeURIComponent(name)}`, { headers });
        const list = await listRes.json();
        const items = list?.data ?? list;
        const found = Array.isArray(items) && items.find(c => c.name === name);
        if (found) {
          categoryMap[name] = found.id;
          console.log(`     → đã tồn tại, dùng id=${found.id}`);
        }
      } catch (_) {}
    }
    await sleep(150);
  }
  return categoryMap;
}

// ─── STEP 2: PRODUCTS ───────────────────────────────────────────────────────

function buildProducts(catMap) {
  const KS  = catMap["Kháng sinh thủy sản"];
  const VS  = catMap["Men vi sinh / Probiotic"];
  const GAN = catMap["Hỗ trợ gan & Đường ruột"];
  const VIT = catMap["Vitamin & Khoáng chất thủy sản"];
  const MD  = catMap["Tăng cường miễn dịch tôm"];
    const HC  = catMap["Hóa chất & Diệt khuẩn ao nuôi"];
  const VOI = catMap["Vôi & Khoáng xử lý ao"];

  return [
    // ── KHÁNG SINH ────────────────────────────────────────────────────────
    {
      name: "Florfenicol 10% Bột trộn thức ăn",
      categoryId: KS,
      brand: "BIO PHARMA",
      origin: "Việt Nam",
      baseSku: "FLO-10",
      status: "ACTIVE",
      description: "<p>Hoạt chất: Florfenicol 100g/kg. Liều dùng: 10–15 mg/kg tôm/ngày, trộn thức ăn, 5–7 ngày. Chỉ định: Vibriosis, AHPND. Ưu tiên hơn Oxytetracycline do nhiều chủng Vibrio đã kháng.</p>",
      variants: [
        { sku: "FLO-10-100G", barcode: null, attributeValueIds: [] },
        { sku: "FLO-10-500G", barcode: null, attributeValueIds: [] },
        { sku: "FLO-10-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Oxytetracycline HCl Bột trộn thức ăn",
      categoryId: KS,
      brand: "BIO PHARMA",
      origin: "Việt Nam",
      baseSku: "OXY-TC",
      status: "ACTIVE",
      description: "<p>Hoạt chất: Oxytetracycline HCl. Liều: 50–75 mg/kg tôm/ngày, 5–7 ngày. Chỉ định: Bệnh mang vi khuẩn (BG), Vibriosis. Lưu ý: nhiều chủng Vibrio đã kháng.</p>",
      variants: [
        { sku: "OXY-TC-100G", barcode: null, attributeValueIds: [] },
        { sku: "OXY-TC-500G", barcode: null, attributeValueIds: [] },
        { sku: "OXY-TC-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Doxycycline Hyclate Bột trộn thức ăn",
      categoryId: KS,
      brand: "BIO PHARMA",
      origin: "Việt Nam",
      baseSku: "DOXY-100",
      status: "ACTIVE",
      description: "<p>Hoạt chất: Doxycycline hyclate. Liều: 20–30 mg/kg tôm/ngày, theo kháng sinh đồ. Chỉ định: Bệnh mang vi khuẩn (BG) khi Oxytetracycline kém hiệu quả.</p>",
      variants: [
        { sku: "DOXY-100-100G", barcode: null, attributeValueIds: [] },
        { sku: "DOXY-100-500G", barcode: null, attributeValueIds: [] },
      ],
    },

    // ── MEN VI SINH ────────────────────────────────────────────────────────
    {
      name: "Probiotic Bacillus spp. Đa chủng",
      categoryId: VS,
      brand: "VINA AQUA",
      origin: "Việt Nam",
      baseSku: "PRO-BAC-SPP",
      status: "ACTIVE",
      description: "<p>Thành phần: Bacillus spp. ≥ 1×10⁹ CFU/g. Liều: 1–2 ppm/ngày xuống ao. Duy trì: 1 ppm, 3 lần/tuần. Công dụng: ức chế Vibrio, phân hủy hữu cơ đáy ao.</p>",
      variants: [
        { sku: "PRO-BAC-SPP-100G", barcode: null, attributeValueIds: [] },
        { sku: "PRO-BAC-SPP-500G", barcode: null, attributeValueIds: [] },
        { sku: "PRO-BAC-SPP-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Probiotic Bacillus subtilis + B. licheniformis",
      categoryId: VS,
      brand: "AQUA PRO",
      origin: "Việt Nam",
      baseSku: "PRO-BS-BL",
      status: "ACTIVE",
      description: "<p>Thành phần: Bacillus subtilis + B. licheniformis ≥ 2×10⁹ CFU/g. Liều: 1–2 ppm xuống ao giai đoạn xử lý môi trường. Chỉ định: Vibriosis/AHPND.</p>",
      variants: [
        { sku: "PRO-BS-BL-100G", barcode: null, attributeValueIds: [] },
        { sku: "PRO-BS-BL-500G", barcode: null, attributeValueIds: [] },
        { sku: "PRO-BS-BL-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Probiotic Bacillus subtilis + Lactobacillus",
      categoryId: VS,
      brand: "AQUA PRO",
      origin: "Việt Nam",
      baseSku: "PRO-BS-LC",
      status: "ACTIVE",
      description: "<p>Thành phần: Bacillus subtilis + Lactobacillus spp. ≥ 2×10⁹ CFU/g. Liều: 2 ppm/ngày. Duy trì: 1 ppm, 3 lần/tuần. Chỉ định: WFS/EHP.</p>",
      variants: [
        { sku: "PRO-BS-LC-100G", barcode: null, attributeValueIds: [] },
        { sku: "PRO-BS-LC-500G", barcode: null, attributeValueIds: [] },
        { sku: "PRO-BS-LC-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },

    // ── HỖ TRỢ GAN & ĐƯỜNG RUỘT ───────────────────────────────────────────
    {
      name: "Sorbitol + Methionine Hỗ trợ gan tụy",
      categoryId: GAN,
      brand: "VIETNAM AQUATIC",
      origin: "Việt Nam",
      baseSku: "SORB-MET",
      status: "ACTIVE",
      description: "<p>Thành phần: Sorbitol + L-Methionine. Liều: theo nhà sản xuất, dùng trong suốt liệu trình kháng sinh. Chỉ định: Vibriosis/AHPND.</p>",
      variants: [
        { sku: "SORB-MET-100G", barcode: null, attributeValueIds: [] },
        { sku: "SORB-MET-500G", barcode: null, attributeValueIds: [] },
        { sku: "SORB-MET-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Sorbitol + Choline + Methionine Phục hồi gan",
      categoryId: GAN,
      brand: "VIETNAM AQUATIC",
      origin: "Việt Nam",
      baseSku: "SCM-GAL",
      status: "ACTIVE",
      description: "<p>Thành phần: Sorbitol + Choline Chloride + L-Methionine. Liều: theo nhà sản xuất, dùng 10–14 ngày liên tục. Chỉ định: WFS/EHP.</p>",
      variants: [
        { sku: "SCM-GAL-100G", barcode: null, attributeValueIds: [] },
        { sku: "SCM-GAL-500G", barcode: null, attributeValueIds: [] },
        { sku: "SCM-GAL-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Men tiêu hóa Enzyme (Protease + Amylase)",
      categoryId: GAN,
      brand: "AQUA PRO",
      origin: "Việt Nam",
      baseSku: "ENZ-PA",
      status: "ACTIVE",
      description: "<p>Thành phần: Protease ≥ 50.000 U/g, Amylase ≥ 30.000 U/g. Liều: theo nhà sản xuất, trộn thức ăn. Chỉ định: Vibriosis/AHPND.</p>",
      variants: [
        { sku: "ENZ-PA-100G", barcode: null, attributeValueIds: [] },
        { sku: "ENZ-PA-500G", barcode: null, attributeValueIds: [] },
        { sku: "ENZ-PA-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Men tiêu hóa Enzyme (Protease + Lipase + Amylase)",
      categoryId: GAN,
      brand: "AQUA PRO",
      origin: "Việt Nam",
      baseSku: "ENZ-PLA",
      status: "ACTIVE",
      description: "<p>Thành phần: Protease + Lipase + Amylase. Liều: 0,3–0,5 g/kg thức ăn. Chỉ định: WFS/EHP — bù đắp chức năng gan tụy bị suy giảm do EHP.</p>",
      variants: [
        { sku: "ENZ-PLA-100G", barcode: null, attributeValueIds: [] },
        { sku: "ENZ-PLA-500G", barcode: null, attributeValueIds: [] },
        { sku: "ENZ-PLA-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },

    // ── VITAMIN & KHOÁNG ──────────────────────────────────────────────────
    {
      name: "Vitamin C Ascorbyl Phosphate Dạng bền",
      categoryId: VIT,
      brand: "VINA AQUA",
      origin: "Việt Nam",
      baseSku: "VIT-C-AP",
      status: "ACTIVE",
      description: "<p>Hoạt chất: Ascorbyl-2-Polyphosphate (Stay-C). Liều: 5–10 g/kg (WSSV/BG); 10 g/kg (Yellowhead hỗ trợ ngắn hạn). Ổn định nhiệt, không bị oxy hóa.</p>",
      variants: [
        { sku: "VIT-C-AP-100G", barcode: null, attributeValueIds: [] },
        { sku: "VIT-C-AP-500G", barcode: null, attributeValueIds: [] },
        { sku: "VIT-C-AP-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Vitamin B Complex + Vitamin E Thủy sản",
      categoryId: VIT,
      brand: "VINA AQUA",
      origin: "Việt Nam",
      baseSku: "VIT-BE",
      status: "ACTIVE",
      description: "<p>Thành phần: Vitamin B1, B2, B3, B5, B6, B12 + Vitamin E. Liều: theo nhà sản xuất. Chỉ định: WFS/EHP — phục hồi tế bào biểu mô gan tụy.</p>",
      variants: [
        { sku: "VIT-BE-100G", barcode: null, attributeValueIds: [] },
        { sku: "VIT-BE-500G", barcode: null, attributeValueIds: [] },
        { sku: "VIT-BE-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Khoáng tổng hợp Ca-Mg-K Hỗ trợ lột xác",
      categoryId: VIT,
      brand: "VINA AQUA",
      origin: "Việt Nam",
      baseSku: "MIN-CMK",
      status: "ACTIVE",
      description: "<p>Thành phần: Ca²⁺, Mg²⁺, K⁺ dạng hòa tan cao. Liều: theo nhà sản xuất. Chỉ định: BACTERIAL_GROUP/BG giai đoạn phục hồi, hỗ trợ lột xác và tái tạo mang.</p>",
      variants: [
        { sku: "MIN-CMK-500G", barcode: null, attributeValueIds: [] },
        { sku: "MIN-CMK-1KG",  barcode: null, attributeValueIds: [] },
        { sku: "MIN-CMK-5KG",  barcode: null, attributeValueIds: [] },
      ],
    },

    // ── MIỄN DỊCH ─────────────────────────────────────────────────────────
    {
      name: "Beta-Glucan β-1,3/1,6 Kích thích miễn dịch tôm",
      categoryId: MD,
      brand: "VIETNAM AQUATIC",
      origin: "Việt Nam",
      baseSku: "BETA-GLU",
      status: "ACTIVE",
      description: "<p>Hoạt chất: β-1,3/1,6-glucan từ nấm men Saccharomyces. Liều: 0,1–0,2 g/kg thức ăn, 7–10 ngày. Cơ chế: kích thích haemocyte chống virus WSSV/YHV.</p>",
      variants: [
        { sku: "BETA-GLU-100G", barcode: null, attributeValueIds: [] },
        { sku: "BETA-GLU-500G", barcode: null, attributeValueIds: [] },
        { sku: "BETA-GLU-1KG",  barcode: null, attributeValueIds: [] },
      ],
    },

    // ── HÓA CHẤT ──────────────────────────────────────────────────────────
    {
      name: "BKC Benzalkonium Chloride 50% Diệt khuẩn ao",
      categoryId: HC,
      brand: "CLEAN POND",
      origin: "Việt Nam",
      baseSku: "BKC-50",
      status: "ACTIVE",
      description: "<p>Hoạt chất: BKC 50%. Liều: 0,3–0,5 ppm, dùng 8–10 giờ sáng. KHÔNG dùng > 1 ppm. Chỉ định: Bệnh mang vi khuẩn (BG).</p>",
      variants: [
        { sku: "BKC-50-500ML", barcode: null, attributeValueIds: [] },
        { sku: "BKC-50-1L",    barcode: null, attributeValueIds: [] },
        { sku: "BKC-50-5L",    barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "EDTA Disodium Chelate kim loại nặng ao nuôi",
      categoryId: HC,
      brand: "CLEAN POND",
      origin: "Việt Nam",
      baseSku: "EDTA-DS",
      status: "ACTIVE",
      description: "<p>Hoạt chất: EDTA Disodium. Liều: 2–3 ppm, tắm tôm 1 giờ hoặc xuống ao. Chỉ định: WFS/EHP — chelate kim loại nặng và giảm độc tố Dinoflagellate.</p>",
      variants: [
        { sku: "EDTA-DS-500G", barcode: null, attributeValueIds: [] },
        { sku: "EDTA-DS-1KG",  barcode: null, attributeValueIds: [] },
        { sku: "EDTA-DS-5KG",  barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Zeolite Hấp thụ NH₃ Cải thiện chất lượng nước",
      categoryId: HC,
      brand: "CLEAN POND",
      origin: "Việt Nam",
      baseSku: "ZEOL-NAT",
      status: "ACTIVE",
      description: "<p>Thành phần: Clinoptilolite ≥ 80%. Liều: 10–15 kg/1.000 m³ khi NH₃ > 0,1 mg/L. Chỉ định: BACTERIAL_GROUP giai đoạn xử lý môi trường.</p>",
      variants: [
        { sku: "ZEOL-NAT-5KG",  barcode: null, attributeValueIds: [] },
        { sku: "ZEOL-NAT-10KG", barcode: null, attributeValueIds: [] },
        { sku: "ZEOL-NAT-25KG", barcode: null, attributeValueIds: [] },
      ],
    },

    // ── VÔI & CHLORINE ────────────────────────────────────────────────────
    {
      name: "Vôi CaO Calcium Oxide Xử lý ao nuôi",
      categoryId: VOI,
      brand: "CLEAN POND",
      origin: "Việt Nam",
      baseSku: "LIME-CAO",
      status: "ACTIVE",
      description: "<p>Thành phần: CaO ≥ 90%. Liều: 20 kg/1.000 m³ xử lý tôm chết. Cơ chế: nâng pH > 11 bất hoạt WSSV/YHV. KHÔNG dùng khi tôm còn sống.</p>",
      variants: [
        { sku: "LIME-CAO-5KG",  barcode: null, attributeValueIds: [] },
        { sku: "LIME-CAO-10KG", barcode: null, attributeValueIds: [] },
        { sku: "LIME-CAO-25KG", barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Vôi Nông nghiệp CaCO₃ Cải tạo đáy ao",
      categoryId: VOI,
      brand: "CLEAN POND",
      origin: "Việt Nam",
      baseSku: "LIME-CC",
      status: "ACTIVE",
      description: "<p>Thành phần: CaCO₃ ≥ 85%. Liều: 1.000–2.000 kg/ha bón sau phơi ao 15–30 ngày. Công dụng: diệt virus tồn lưu trong bùn, cải thiện đáy ao.</p>",
      variants: [
        { sku: "LIME-CC-10KG", barcode: null, attributeValueIds: [] },
        { sku: "LIME-CC-25KG", barcode: null, attributeValueIds: [] },
        { sku: "LIME-CC-50KG", barcode: null, attributeValueIds: [] },
      ],
    },
    {
      name: "Chlorine bột 70% Khử trùng nước thải ao",
      categoryId: VOI,
      brand: "CLEAN POND",
      origin: "Việt Nam",
      baseSku: "CLO-70",
      status: "ACTIVE",
      description: "<p>Hoạt chất: Ca(OCl)₂ 70%. Liều: 30 ppm giữ 48 giờ (nước thải); 100 ppm (sát trùng dụng cụ). Bắt buộc theo QCVN 02-19:2014 Điều 2.4 trước khi xả ra môi trường.</p>",
      variants: [
        { sku: "CLO-70-1KG",  barcode: null, attributeValueIds: [] },
        { sku: "CLO-70-5KG",  barcode: null, attributeValueIds: [] },
        { sku: "CLO-70-25KG", barcode: null, attributeValueIds: [] },
      ],
    },
  ];
}

async function seedProducts(catMap) {
  console.log("\n📦 Tạo Products...");
  const products = buildProducts(catMap);
  let ok = 0, fail = 0;

  for (const p of products) {
    if (!p.categoryId) {
      console.warn(`  ⚠️  "${p.name}": categoryId null — bỏ qua`);
      fail++;
      continue;
    }
    try {
      const res = await post("/products/json", { data: p });
      const id = res?.id ?? res?.data?.id ?? "?";
      console.log(`  ✅ "${p.name}" → id=${id}`);
      ok++;
    } catch (e) {
      console.error(`  ❌ "${p.name}": ${e.message}`);
      fail++;
    }
    await sleep(200);
  }
  return { ok, fail };
}

// ─── MAIN ────────────────────────────────────────────────────────────────────

(async () => {
  console.log(`\n🚀 Seed bắt đầu → ${BASE_URL}`);
  console.log(`   Token: ${TOKEN.slice(0, 20)}...`);

  try {
    const catMap = await seedCategories();

    const missing = Object.entries(catMap).filter(([, id]) => !id).map(([n]) => n);
    if (missing.length > 0) {
      console.warn(`\n⚠️  ${missing.length} category chưa có ID: ${missing.join(", ")}`);
      console.warn("   Các sản phẩm thuộc category này sẽ bị bỏ qua.");
    }

    const { ok, fail } = await seedProducts(catMap);

    console.log(`\n✅ Hoàn thành: ${ok} sản phẩm tạo thành công, ${fail} thất bại.`);
    if (fail > 0) {
      console.log("   → Kiểm tra lại token (hết hạn?) hoặc SKU trùng.");
    }
  } catch (e) {
    console.error("\n💥 Lỗi không mong đợi:", e.message);
    process.exit(1);
  }
})();
