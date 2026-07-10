package com.zone.agri.service;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class ScraperTest {

    @Test
    public void testScrapers() {
        String taxCode = "3701091716";
        
        System.out.println("=== TESTING DOANHNGHIEP.BIZ ===");
        long start = System.currentTimeMillis();
        try {
            String url = "https://doanhnghiep.biz/" + taxCode;
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            System.out.println("DoanhNghiepBiz connect time: " + (System.currentTimeMillis() - start) + "ms");
            Element table = doc.selectFirst("table.company-table");
            if (table != null) {
                System.out.println("Table found!");
                System.out.println("Company Name: " + doc.selectFirst("th[itemprop=name]").text());
                System.out.println("Owner: " + doc.selectFirst("span[itemprop=Owner] a").text());
            } else {
                System.out.println("Table NOT found!");
            }
        } catch (Exception e) {
            System.out.println("Error scraping DoanhNghiepBiz: " + e.getMessage());
        }

        System.out.println("\n=== TESTING MASOTHUE.COM ===");
        start = System.currentTimeMillis();
        try {
            String url = "https://masothue.com/Search/?q=" + taxCode + "&type=auto";
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            System.out.println("MasoThue connect time: " + (System.currentTimeMillis() - start) + "ms");
            Element table = doc.selectFirst("table.table-taxinfo");
            if (table != null) {
                System.out.println("Table found!");
                System.out.println("Company Name: " + doc.selectFirst("th[itemprop=name]").text());
            } else {
                System.out.println("Table NOT found! Title: " + doc.title());
            }
        } catch (Exception e) {
            System.out.println("Error scraping MasoThue: " + e.getMessage());
        }
    }
}
