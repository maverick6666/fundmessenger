package com.fundmessenger.jongtobang.service;

import com.fundmessenger.jongtobang.entity.Stock;
import com.fundmessenger.jongtobang.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * KIS 종목 마스터 파일 다운로드 → Stock 테이블 동기화
 * 인증 불필요 — 공개 URL에서 zip 다운로드 후 파싱
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockMasterService {

    private final StockRepository stockRepository;

    private static final String BASE_DOWNLOAD_URL = "https://new.real.download.dws.co.kr/common/master/";
    private static final Charset CP949 = Charset.forName("CP949");
    private final ReentrantLock syncLock = new ReentrantLock();

    /**
     * 매일 06:30 자동 동기화 (KIS 마스터 파일 06:00 업데이트 후)
     */
    @Scheduled(cron = "0 30 6 * * *")
    public void scheduledSync() {
        if (!syncLock.tryLock()) {
            log.warn("이전 종목 마스터 동기화 진행 중, 스킵");
            return;
        }
        try {
            log.info("=== 종목 마스터 스케줄 동기화 시작 ===");
            syncAllStocks();
        } finally {
            syncLock.unlock();
        }
    }

    /**
     * 전종목 동기화: KOSPI + KOSDAQ + NASDAQ + NYSE + AMEX
     * 반환: 신규 등록 수
     */
    @Transactional
    public Map<String, Integer> syncAllStocks() {
        Map<String, Integer> result = new LinkedHashMap<>();

        result.put("KOSPI", syncDomesticMarket("kospi_code.mst.zip", "KOSPI"));
        result.put("KOSDAQ", syncDomesticMarket("kosdaq_code.mst.zip", "KOSDAQ"));
        result.put("NASDAQ", syncOverseasMarket("nas", "NASDAQ"));
        result.put("NYSE", syncOverseasMarket("nys", "NYSE"));
        result.put("AMEX", syncOverseasMarket("ams", "AMEX"));

        int total = result.values().stream().mapToInt(i -> i).sum();
        long totalActive = stockRepository.findByActiveTrueOrderByStockNameAsc().size();
        result.put("total_new", total);
        result.put("total_active", (int) totalActive);

        log.info("=== 전종목 동기화 완료: {} ===", result);
        return result;
    }

    // ── 국내 종목 (KOSPI/KOSDAQ) ──

    private int syncDomesticMarket(String zipFileName, String market) {
        try {
            byte[] zipBytes = downloadFile(BASE_DOWNLOAD_URL + zipFileName);
            byte[] mstBytes = unzipFirstEntry(zipBytes);
            List<StockInfo> stocks = parseDomesticMst(mstBytes);
            return upsertStocks(stocks, market);
        } catch (Exception e) {
            log.error("국내 종목 마스터 동기화 실패: {} — {}", market, e.getMessage());
            return 0;
        }
    }

    /**
     * 국내 MST 파일 파싱
     * 형식: 각 행의 앞부분 = [단축코드(9)][표준코드(12)][한글명(가변)] + 뒤 228바이트 = 부가정보
     * 단축코드에서 앞 6자리가 실제 종목코드 (예: "005930   " → "005930")
     */
    private List<StockInfo> parseDomesticMst(byte[] mstBytes) {
        List<StockInfo> result = new ArrayList<>();
        String content = new String(mstBytes, CP949);
        String[] lines = content.split("\n");

        for (String line : lines) {
            if (line.length() < 240) continue;
            try {
                // 뒤 228바이트 = 부가정보
                String mainPart = line.substring(0, line.length() - 228);
                String code = mainPart.substring(0, 9).trim();    // 단축코드
                String name = mainPart.substring(21).trim();       // 한글명 (21~ 끝)

                // 부가정보에서 거래정지/관리종목 등 확인
                String extra = line.substring(line.length() - 228);
                char suspended = extra.charAt(34);   // 거래정지
                char managed = extra.charAt(36);     // 관리종목

                // 6자리 종목코드 추출
                if (code.length() >= 6) {
                    code = code.substring(0, 6);
                }

                // 우선주/스팩/ETF/ELW 등 필터
                if (name.isEmpty() || code.isEmpty()) continue;
                // 거래정지/관리종목 제외
                if (suspended == '1' || managed == '1') continue;

                result.add(new StockInfo(code, name));
            } catch (Exception e) {
                // 파싱 실패한 행 스킵
            }
        }
        log.info("국내 마스터 파싱: {}건", result.size());
        return result;
    }

    // ── 해외 종목 (NASDAQ/NYSE/AMEX) ──

    private int syncOverseasMarket(String marketCode, String market) {
        try {
            byte[] zipBytes = downloadFile(BASE_DOWNLOAD_URL + marketCode + "mst.cod.zip");
            byte[] codBytes = unzipFirstEntry(zipBytes);
            List<StockInfo> stocks = parseOverseasCod(codBytes, market);
            return upsertStocks(stocks, market);
        } catch (Exception e) {
            log.error("해외 종목 마스터 동기화 실패: {} — {}", market, e.getMessage());
            return 0;
        }
    }

    /**
     * 해외 COD 파일 파싱
     * TSV 형식, 컬럼: 국가코드 \t 거래소ID \t 거래소코드 \t 거래소명 \t 심볼 \t 리얼타임심볼 \t 한글명 \t 영문명 \t 증권유형 ...
     * 증권유형: 1=Index, 2=Stock, 3=ETP(ETF), 4=Warrant
     */
    private List<StockInfo> parseOverseasCod(byte[] codBytes, String market) {
        List<StockInfo> result = new ArrayList<>();
        String content = new String(codBytes, CP949);
        String[] lines = content.split("\n");

        for (String line : lines) {
            if (line.isBlank()) continue;
            String[] cols = line.split("\t");
            if (cols.length < 9) continue;

            try {
                String symbol = cols[4].trim();       // 심볼
                String koName = cols[6].trim();        // 한글명
                String enName = cols[7].trim();        // 영문명
                String secType = cols[8].trim();       // 증권유형

                // Stock(2)만 — Index, ETF, Warrant 제외
                if (!"2".equals(secType)) continue;
                if (symbol.isEmpty()) continue;

                String name = koName.isEmpty() ? enName : koName;
                if (name.isEmpty()) name = symbol;

                result.add(new StockInfo(symbol, name));
            } catch (Exception e) {
                // 파싱 실패한 행 스킵
            }
        }
        log.info("해외 마스터 파싱 ({}): {}건", market, result.size());
        return result;
    }

    // ── 공통: DB upsert ──

    @Transactional
    protected int upsertStocks(List<StockInfo> stocks, String market) {
        int newCount = 0;
        Set<String> masterCodes = new HashSet<>();

        for (StockInfo si : stocks) {
            masterCodes.add(si.code);
            boolean isEtf = detectEtf(si.name, market);
            Optional<Stock> existing = stockRepository.findByStockCode(si.code);
            if (existing.isPresent()) {
                Stock s = existing.get();
                if (!s.getStockName().equals(si.name)) {
                    s.setStockName(si.name);
                }
                s.setEtf(isEtf);
                if (!s.isActive()) {
                    s.setActive(true);
                    log.info("종목 재활성화: {} ({})", si.name, si.code);
                }
                stockRepository.save(s);
            } else {
                Stock s = Stock.builder()
                        .stockCode(si.code)
                        .stockName(si.name)
                        .market(market)
                        .etf(isEtf)
                        .active(true)
                        .build();
                stockRepository.save(s);
                newCount++;
            }
        }

        // 마스터에 없는 기존 종목 → 비활성화 (상장폐지)
        List<Stock> existingInMarket = stockRepository.findByMarket(market);
        for (Stock s : existingInMarket) {
            if (!masterCodes.contains(s.getStockCode()) && s.isActive()) {
                s.setActive(false);
                stockRepository.save(s);
                log.info("종목 비활성화 (상장폐지): {} ({})", s.getStockName(), s.getStockCode());
            }
        }

        log.info("{} 동기화: 신규 {}건, 마스터 {}건", market, newCount, stocks.size());
        return newCount;
    }

    /** 기존 DB의 ETF 플래그 일괄 업데이트 (페이지 단위 처리로 메모리 절약) */
    @Transactional
    public int markEtfFlags() {
        int count = 0;
        int pageNum = 0;
        final int batchSize = 500;
        org.springframework.data.domain.Page<Stock> page;
        do {
            page = stockRepository.findAll(org.springframework.data.domain.PageRequest.of(pageNum++, batchSize));
            for (Stock s : page.getContent()) {
                boolean isEtf = detectEtf(s.getStockName(), s.getMarket());
                if (s.isEtf() != isEtf) {
                    s.setEtf(isEtf);
                    count++;
                }
            }
        } while (page.hasNext());
        log.info("ETF 플래그 업데이트: {}건 변경", count);
        return count;
    }

    /** 국내 ETF/ETN 판별 — 이름 기반 패턴 매칭 */
    private static final List<String> ETF_PREFIXES = List.of(
            "KODEX", "TIGER", "KBSTAR", "ARIRANG", "HANARO", "SOL ", "ACE ",
            "KOSEF", "SMART", "TIMEFOLIO", "FOCUS", "WOORI", "BNK",
            "PLUS ", "RISE ", "파워 ", "마이다스"
    );
    private static final List<String> ETF_KEYWORDS = List.of(
            "ETF", "ETN", "액티브", "인버스", "레버리지", "S&P", "채권", "혼합",
            "머니마켓", "단기", "국고채", "회사채", "배당", "밸류업"
    );

    private boolean detectEtf(String name, String market) {
        // 해외 종목은 이미 secType=2(Stock) 필터로 ETF 제외됨
        if (!"KOSPI".equals(market) && !"KOSDAQ".equals(market)) return false;
        String upper = name.toUpperCase();
        for (String prefix : ETF_PREFIXES) {
            if (upper.startsWith(prefix.toUpperCase())) return true;
        }
        for (String kw : ETF_KEYWORDS) {
            if (name.contains(kw)) return true;
        }
        // 숫자+영문으로 시작하는 패턴 (1Q, 2X 등 — 대부분 ETF)
        if (name.length() >= 2 && Character.isDigit(name.charAt(0)) && Character.isLetter(name.charAt(1))) {
            return true;
        }
        return false;
    }

    // ── 유틸 ──

    private byte[] downloadFile(String url) throws IOException {
        log.info("마스터 파일 다운로드: {}", url);
        try (InputStream is = URI.create(url).toURL().openStream()) {
            return is.readAllBytes();
        }
    }

    private byte[] unzipFirstEntry(byte[] zipBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) throw new IOException("빈 zip 파일");
            log.debug("zip 엔트리: {}", entry.getName());
            return zis.readAllBytes();
        }
    }

    private record StockInfo(String code, String name) {}
}
