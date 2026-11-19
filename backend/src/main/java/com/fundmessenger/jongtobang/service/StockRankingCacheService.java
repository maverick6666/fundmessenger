package com.fundmessenger.jongtobang.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.jongtobang.dto.StockResponse;
import com.fundmessenger.jongtobang.entity.Stock;
import com.fundmessenger.jongtobang.entity.StockRankingSnapshot;
import com.fundmessenger.jongtobang.repository.StockRankingSnapshotRepository;
import com.fundmessenger.jongtobang.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockRankingCacheService {

    private static final int SNAPSHOT_SIZE = 30;
    private static final long SNAPSHOT_STALE_MILLIS = 90_000;
    private static final String REDIS_KEY_PREFIX = "jongto:ranking:";
    private static final Duration REDIS_TTL = Duration.ofHours(6);

    private final StockPriceService stockPriceService;
    private final StockRepository stockRepository;
    private final StockRankingSnapshotRepository snapshotRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final ReentrantLock refreshLock = new ReentrantLock();
    private final Map<String, CachedSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<String> refreshingKeys = ConcurrentHashMap.newKeySet();

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        loadPersistedSnapshots();
        refreshAllSnapshots();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void scheduledRefresh() {
        refreshAllSnapshots();
    }

    public List<StockResponse> getSnapshot(String sort, String market, String marketGroup, int size) {
        int snapshotSize = Math.max(size, SNAPSHOT_SIZE);
        String cacheKey = key(sort, market, marketGroup);

        CachedSnapshot cached = snapshots.get(cacheKey);
        if (cached != null && !cached.items().isEmpty()) {
            if (isStale(cached)) {
                triggerRefreshAsync(sort, market, marketGroup);
            }
            List<StockResponse> items = hydrateSnapshot(topUpSnapshot(cached.items(), sort, market, marketGroup, snapshotSize));
            items = maybeBackfillOverseasQuotes(items, sort, market, marketGroup);
            return limit(items, size);
        }

        CachedSnapshot redisCached = loadRedisSnapshot(cacheKey);
        if (redisCached != null && !redisCached.items().isEmpty()) {
            snapshots.put(cacheKey, redisCached);
            if (isStale(redisCached)) {
                triggerRefreshAsync(sort, market, marketGroup);
            }
            List<StockResponse> items = hydrateSnapshot(topUpSnapshot(redisCached.items(), sort, market, marketGroup, snapshotSize));
            items = maybeBackfillOverseasQuotes(items, sort, market, marketGroup);
            return limit(items, size);
        }

        CachedSnapshot persisted = loadPersistedSnapshot(cacheKey);
        if (persisted != null && !persisted.items().isEmpty()) {
            snapshots.put(cacheKey, persisted);
            if (isStale(persisted)) {
                triggerRefreshAsync(sort, market, marketGroup);
            }
            List<StockResponse> items = hydrateSnapshot(topUpSnapshot(persisted.items(), sort, market, marketGroup, snapshotSize));
            items = maybeBackfillOverseasQuotes(items, sort, market, marketGroup);
            return limit(items, size);
        }

        triggerRefreshAsync(sort, market, marketGroup);
        List<StockResponse> fallback = loadFallbackSnapshot(sort, market, marketGroup, snapshotSize);
        if (!fallback.isEmpty()) {
            List<StockResponse> hydrated = hydrateSnapshot(fallback);
            hydrated = maybeBackfillOverseasQuotes(hydrated, sort, market, marketGroup);
            snapshots.put(cacheKey, new CachedSnapshot(hydrated, System.currentTimeMillis()));
            fallback = hydrated;
        }
        return limit(fallback, size);
    }

    private void refreshAllSnapshots() {
        if (!refreshLock.tryLock()) {
            log.debug("Stock ranking snapshot refresh already running");
            return;
        }
        try {
            refreshSort("gainers");
            refreshSort("losers");
            refreshSort("volume");
            refreshSort("marketCap");
        } finally {
            refreshLock.unlock();
        }
    }

    private void refreshSort(String sort) {
        refreshEntry(sort, null, "DOMESTIC");
        refreshEntry(sort, null, "OVERSEAS");

        refreshEntry(sort, "KOSPI", null);
        refreshEntry(sort, "KOSDAQ", null);
        refreshEntry(sort, "NASDAQ", null);
        refreshEntry(sort, "NYSE", null);
        refreshEntry(sort, "AMEX", null);
    }

    private void refreshEntry(String sort, String market, String marketGroup) {
        String cacheKey = key(sort, market, marketGroup);
        if (!refreshingKeys.add(cacheKey)) {
            return;
        }
        try {
            List<StockResponse> snapshot = computeSnapshot(sort, market, marketGroup, SNAPSHOT_SIZE);
            snapshot = topUpSnapshot(snapshot, sort, market, marketGroup, SNAPSHOT_SIZE);
            snapshot = hydrateSnapshot(snapshot);
            if (!snapshot.isEmpty()) {
                long now = System.currentTimeMillis();
                snapshots.put(cacheKey, new CachedSnapshot(snapshot, now));
                writeRedisSnapshot(cacheKey, snapshot, now);
                persistSnapshot(cacheKey, sort, market, marketGroup, snapshot, now);
            }
        } catch (Exception e) {
            log.warn("Stock ranking snapshot refresh failed: sort={}, market={}, marketGroup={}", sort, market, marketGroup, e);
        } finally {
            refreshingKeys.remove(cacheKey);
        }
    }

    private List<StockResponse> computeSnapshot(String sort, String market, String marketGroup, int size) {
        return switch (sort) {
            case "gainers", "losers" -> resolveChangeRateSnapshot(sort, market, marketGroup, size);
            case "volume" -> toResponses(resolveVolumeSnapshot(market, marketGroup, size));
            case "marketCap" -> toResponses(resolveMarketCapSnapshot(market, marketGroup, size));
            default -> List.of();
        };
    }

    private void triggerRefreshAsync(String sort, String market, String marketGroup) {
        CompletableFuture.runAsync(() -> refreshEntry(sort, market, marketGroup));
    }

    private List<StockResponse> resolveChangeRateSnapshot(String sort, String market, String marketGroup, int size) {
        boolean ascending = "losers".equals(sort);
        boolean hasMarket = market != null && !market.isBlank() && !"ALL".equalsIgnoreCase(market);

        if (hasMarket && ("KOSPI".equals(market) || "KOSDAQ".equals(market))) {
            return stockPriceService.getDomesticRanking(market, ascending, size);
        }
        if (hasMarket && ("NASDAQ".equals(market) || "NYSE".equals(market) || "AMEX".equals(market))) {
            return stockPriceService.getOverseasRanking(market, ascending, size);
        }
        if ("OVERSEAS".equalsIgnoreCase(marketGroup)) {
            return stockPriceService.getCombinedOverseasRanking(ascending, size);
        }
        return stockPriceService.getCombinedDomesticRanking(ascending, size);
    }

    private List<Stock> resolveVolumeSnapshot(String market, String marketGroup, int size) {
        boolean hasMarket = market != null && !market.isBlank() && !"ALL".equalsIgnoreCase(market);

        if (hasMarket && ("KOSPI".equals(market) || "KOSDAQ".equals(market))) {
            return stockPriceService.getDomesticVolumeRankingByMarket(market, size);
        }
        if (hasMarket && ("NASDAQ".equals(market) || "NYSE".equals(market) || "AMEX".equals(market))) {
            return stockPriceService.getOverseasVolumeRanking(market, size);
        }
        if ("OVERSEAS".equalsIgnoreCase(marketGroup)) {
            return stockPriceService.getCombinedOverseasVolumeRanking(size);
        }
        return stockPriceService.getCombinedDomesticVolumeRanking(size);
    }

    private List<Stock> resolveMarketCapSnapshot(String market, String marketGroup, int size) {
        boolean hasMarket = market != null && !market.isBlank() && !"ALL".equalsIgnoreCase(market);

        if (hasMarket && ("KOSPI".equals(market) || "KOSDAQ".equals(market))) {
            return stockPriceService.getDomesticMarketCapRankingByMarket(market, size);
        }
        if (hasMarket && ("NASDAQ".equals(market) || "NYSE".equals(market) || "AMEX".equals(market))) {
            return stockPriceService.getOverseasMarketCapRanking(market, size);
        }
        if ("OVERSEAS".equalsIgnoreCase(marketGroup)) {
            return stockPriceService.getCombinedOverseasMarketCapRanking(size);
        }
        return stockPriceService.getCombinedDomesticMarketCapRanking(size);
    }

    private List<StockResponse> loadFallbackSnapshot(String sort, String market, String marketGroup, int size) {
        return switch (sort) {
            case "gainers", "losers" -> loadFallbackChangeRateSnapshot(sort, market, marketGroup, size);
            case "volume" -> toResponses(loadFallbackVolumeSnapshot(market, marketGroup, size));
            case "marketCap" -> toResponses(loadFallbackMarketCapSnapshot(market, marketGroup, size));
            default -> List.of();
        };
    }

    private List<StockResponse> loadFallbackChangeRateSnapshot(String sort, String market, String marketGroup, int size) {
        PageRequest pageable = PageRequest.of(0, size);
        List<String> markets = resolveMarkets(market, marketGroup);
        boolean ascending = "losers".equals(sort);

        List<Stock> stocks = markets.size() == 1
                ? ascending
                ? stockRepository.findByChangeRateAscAndMarket(markets.get(0), pageable)
                : stockRepository.findByChangeRateDescAndMarket(markets.get(0), pageable)
                : ascending
                ? stockRepository.findByChangeRateAscAndMarkets(markets, pageable)
                : stockRepository.findByChangeRateDescAndMarkets(markets, pageable);

        return toResponses(ensureNotEmpty(stocks, markets, size));
    }

    private List<Stock> loadFallbackVolumeSnapshot(String market, String marketGroup, int size) {
        PageRequest pageable = PageRequest.of(0, size);
        List<String> markets = resolveMarkets(market, marketGroup);

        List<Stock> stocks = markets.size() == 1
                ? stockRepository.findByTradingVolumeDescAndMarket(markets.get(0), pageable)
                : stockRepository.findByTradingVolumeDescAndMarkets(markets, pageable);

        return ensureNotEmpty(stocks, markets, size);
    }

    private List<Stock> loadFallbackMarketCapSnapshot(String market, String marketGroup, int size) {
        PageRequest pageable = PageRequest.of(0, size);
        List<String> markets = resolveMarkets(market, marketGroup);

        List<Stock> stocks = markets.size() == 1
                ? stockRepository.findByMarketCapDescAndMarket(markets.get(0), pageable)
                : stockRepository.findByMarketCapDescAndMarkets(markets, pageable);

        return ensureNotEmpty(stocks, markets, size);
    }

    private List<Stock> ensureNotEmpty(List<Stock> stocks, List<String> markets, int size) {
        if (stocks.size() >= size) {
            return stocks;
        }

        Map<String, Stock> merged = new LinkedHashMap<>();
        for (Stock stock : stocks) {
            if (stock == null || stock.getStockCode() == null) continue;
            merged.putIfAbsent(stock.getStockCode(), stock);
        }

        PageRequest pageable = PageRequest.of(0, size);
        List<Stock> activeStocks = markets.size() == 1
                ? stockRepository.findActiveNonEtfByMarket(markets.get(0), pageable)
                : stockRepository.findActiveNonEtfByMarkets(markets, pageable);

        for (Stock stock : activeStocks) {
            if (stock == null || stock.getStockCode() == null) continue;
            merged.putIfAbsent(stock.getStockCode(), stock);
            if (merged.size() >= size) {
                break;
            }
        }

        return new ArrayList<>(merged.values()).subList(0, Math.min(size, merged.size()));
    }

    private List<String> resolveMarkets(String market, String marketGroup) {
        if (market != null && !market.isBlank() && !"ALL".equalsIgnoreCase(market)) {
            return List.of(market);
        }
        if ("OVERSEAS".equalsIgnoreCase(marketGroup)) {
            return List.of("NASDAQ", "NYSE", "AMEX");
        }
        return List.of("KOSPI", "KOSDAQ");
    }

    private List<StockResponse> topUpSnapshot(List<StockResponse> primary, String sort, String market, String marketGroup, int size) {
        if (primary.size() >= size) {
            return primary;
        }

        Map<String, StockResponse> merged = new LinkedHashMap<>();
        for (StockResponse item : primary) {
            if (item == null || item.getStockCode() == null) continue;
            merged.putIfAbsent(item.getStockCode(), item);
        }

        for (StockResponse item : loadFallbackSnapshot(sort, market, marketGroup, size)) {
            if (item == null || item.getStockCode() == null) continue;
            merged.putIfAbsent(item.getStockCode(), item);
            if (merged.size() >= size) {
                break;
            }
        }

        return new ArrayList<>(merged.values());
    }

    private void loadPersistedSnapshots() {
        int loaded = 0;
        for (StockRankingSnapshot snapshot : snapshotRepository.findAll()) {
            CachedSnapshot cached = deserializeSnapshot(snapshot);
            if (cached == null || cached.items().isEmpty()) {
                continue;
            }
            snapshots.put(snapshot.getCacheKey(), cached);
            loaded++;
        }
        if (loaded > 0) {
            log.info("Loaded {} persisted stock ranking snapshots", loaded);
        }
    }

    private CachedSnapshot loadPersistedSnapshot(String cacheKey) {
        return snapshotRepository.findByCacheKey(cacheKey)
                .map(this::deserializeSnapshot)
                .orElse(null);
    }

    private CachedSnapshot loadRedisSnapshot(String cacheKey) {
        try {
            String payload = redisTemplate.opsForValue().get(redisKey(cacheKey));
            if (payload == null || payload.isBlank()) {
                return null;
            }
            RedisSnapshotPayload snapshot = objectMapper.readValue(payload, RedisSnapshotPayload.class);
            if (snapshot.items() == null || snapshot.items().isEmpty()) {
                return null;
            }
            return new CachedSnapshot(snapshot.items(), snapshot.timestamp());
        } catch (Exception e) {
            log.debug("Failed to load redis stock ranking snapshot: key={}", cacheKey, e);
            return null;
        }
    }

    private void writeRedisSnapshot(String cacheKey, List<StockResponse> items, long timestamp) {
        try {
            String payload = objectMapper.writeValueAsString(new RedisSnapshotPayload(timestamp, items));
            redisTemplate.opsForValue().set(redisKey(cacheKey), payload, REDIS_TTL);
        } catch (Exception e) {
            log.debug("Failed to write redis stock ranking snapshot: key={}", cacheKey, e);
        }
    }

    private CachedSnapshot deserializeSnapshot(StockRankingSnapshot snapshot) {
        try {
            List<StockResponse> items = objectMapper.readValue(snapshot.getPayload(), new TypeReference<List<StockResponse>>() {});
            if (items == null || items.isEmpty()) {
                return null;
            }
            long timestamp = snapshot.getSnapshotAt()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            return new CachedSnapshot(items, timestamp);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize stock ranking snapshot: key={}", snapshot.getCacheKey(), e);
            return null;
        }
    }

    private void persistSnapshot(String cacheKey, String sort, String market, String marketGroup, List<StockResponse> items, long timestamp) {
        try {
            String payload = objectMapper.writeValueAsString(items);
            StockRankingSnapshot snapshot = snapshotRepository.findByCacheKey(cacheKey)
                    .orElseGet(() -> StockRankingSnapshot.builder()
                            .cacheKey(cacheKey)
                            .sortKey(sort)
                            .market(market)
                            .marketGroup(marketGroup)
                            .build());
            snapshot.setSortKey(sort);
            snapshot.setMarket(market);
            snapshot.setMarketGroup(marketGroup);
            snapshot.setPayload(payload);
            snapshot.setSnapshotAt(java.time.Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime());
            snapshotRepository.save(snapshot);
        } catch (JsonProcessingException e) {
            log.warn("Failed to persist stock ranking snapshot: key={}", cacheKey, e);
        }
    }

    private List<StockResponse> toResponses(List<Stock> stocks) {
        List<StockResponse> responses = new ArrayList<>();
        for (Stock stock : stocks) {
            if (stock == null) continue;
            responses.add(StockResponse.builder()
                    .id(stock.getId())
                    .stockCode(stock.getStockCode())
                    .stockName(stock.getStockName())
                    .market(stock.getMarket())
                    .currentPrice(stock.getCurrentPrice())
                    .changeRate(stock.getChangeRate())
                    .priceUpdatedAt(stock.getPriceUpdatedAt())
                    .newsCount(0)
                    .threadCount(0)
                    .build());
        }
        return responses;
    }

    private List<StockResponse> hydrateSnapshot(List<StockResponse> items) {
        List<Long> ids = items.stream()
                .map(StockResponse::getId)
                .filter(id -> id != null)
                .toList();
        if (ids.isEmpty()) {
            return items;
        }

        Map<Long, Stock> stocksById = new LinkedHashMap<>();
        for (Stock stock : stockRepository.findAllById(ids)) {
            stocksById.put(stock.getId(), stock);
        }

        List<StockResponse> hydrated = new ArrayList<>(items.size());
        for (StockResponse item : items) {
            if (item.getId() == null) {
                hydrated.add(item);
                continue;
            }
            Stock stock = stocksById.get(item.getId());
            if (stock == null) {
                hydrated.add(item);
                continue;
            }
            hydrated.add(item.toBuilder()
                    .currentPrice(stock.getCurrentPrice())
                    .changeRate(stock.getChangeRate())
                    .priceUpdatedAt(stock.getPriceUpdatedAt())
                    .build());
        }
        return hydrated;
    }

    private List<StockResponse> maybeBackfillOverseasQuotes(List<StockResponse> items, String sort, String market, String marketGroup) {
        if (!shouldBackfillOverseasQuotes(sort, market, marketGroup)) {
            return items;
        }

        List<Long> missingIds = items.stream()
                .filter(item -> item.getId() != null)
                .filter(item -> item.getCurrentPrice() == null || item.getChangeRate() == null)
                .map(StockResponse::getId)
                .toList();
        if (missingIds.isEmpty()) {
            return items;
        }

        stockPriceService.refreshOverseasQuotes(missingIds);
        return hydrateSnapshot(items);
    }

    private boolean shouldBackfillOverseasQuotes(String sort, String market, String marketGroup) {
        if (!"volume".equals(sort) && !"marketCap".equals(sort)) {
            return false;
        }
        if ("OVERSEAS".equalsIgnoreCase(marketGroup)) {
            return true;
        }
        return "NASDAQ".equals(market) || "NYSE".equals(market) || "AMEX".equals(market);
    }

    private List<StockResponse> limit(List<StockResponse> items, int size) {
        if (size >= items.size()) {
            return items;
        }
        return items.subList(0, size);
    }

    private boolean isStale(CachedSnapshot snapshot) {
        return System.currentTimeMillis() - snapshot.timestamp() >= SNAPSHOT_STALE_MILLIS;
    }

    private String redisKey(String cacheKey) {
        return REDIS_KEY_PREFIX + cacheKey;
    }

    private String key(String sort, String market, String marketGroup) {
        if (market != null && !market.isBlank() && !"ALL".equalsIgnoreCase(market)) {
            return sort + "::MARKET::" + market;
        }
        if ("OVERSEAS".equalsIgnoreCase(marketGroup)) {
            return sort + "::GROUP::OVERSEAS";
        }
        return sort + "::GROUP::DOMESTIC";
    }

    private record CachedSnapshot(List<StockResponse> items, long timestamp) {}

    private record RedisSnapshotPayload(long timestamp, List<StockResponse> items) {}
}
