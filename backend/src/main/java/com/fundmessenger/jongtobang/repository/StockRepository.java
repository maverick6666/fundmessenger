package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.Stock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByStockCode(String stockCode);

    List<Stock> findByActiveTrueOrderByStockNameAsc();

    List<Stock> findByActiveTrueAndEtfFalseOrderByStockNameAsc();

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market = :market ORDER BY s.stockName ASC")
    List<Stock> findActiveNonEtfByMarket(@Param("market") String market, Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market IN :markets ORDER BY s.stockName ASC")
    List<Stock> findActiveNonEtfByMarkets(@Param("markets") List<String> markets, Pageable pageable);

    // ?? 二쇰ぉ??(而ㅽ뵆留???DESC) ??

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false " +
           "AND ((SELECT COUNT(c) FROM StockNewsCoupling c WHERE c.stock = s) + (SELECT COUNT(t) FROM StockThread t WHERE t.stock = s)) > 0 " +
           "ORDER BY ((SELECT COUNT(c) FROM StockNewsCoupling c WHERE c.stock = s) + (SELECT COUNT(t) FROM StockThread t WHERE t.stock = s)) DESC, s.stockName ASC")
    List<Stock> findByAttention(Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market = :market " +
           "AND ((SELECT COUNT(c) FROM StockNewsCoupling c WHERE c.stock = s) + (SELECT COUNT(t) FROM StockThread t WHERE t.stock = s)) > 0 " +
           "ORDER BY ((SELECT COUNT(c) FROM StockNewsCoupling c WHERE c.stock = s) + (SELECT COUNT(t) FROM StockThread t WHERE t.stock = s)) DESC, s.stockName ASC")
    List<Stock> findByAttentionAndMarket(@Param("market") String market, Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market IN :markets " +
           "AND ((SELECT COUNT(c) FROM StockNewsCoupling c WHERE c.stock = s) + (SELECT COUNT(t) FROM StockThread t WHERE t.stock = s)) > 0 " +
           "ORDER BY ((SELECT COUNT(c) FROM StockNewsCoupling c WHERE c.stock = s) + (SELECT COUNT(t) FROM StockThread t WHERE t.stock = s)) DESC, s.stockName ASC")
    List<Stock> findByAttentionAndMarkets(@Param("markets") List<String> markets, Pageable pageable);

    // ?? 蹂?숇쪧????

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.changeRate IS NOT NULL ORDER BY s.changeRate DESC, s.stockName ASC")
    List<Stock> findByChangeRateDesc(Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.changeRate IS NOT NULL ORDER BY s.changeRate ASC, s.stockName ASC")
    List<Stock> findByChangeRateAsc(Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market = :market AND s.changeRate IS NOT NULL ORDER BY s.changeRate DESC, s.stockName ASC")
    List<Stock> findByChangeRateDescAndMarket(@Param("market") String market, Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market = :market AND s.changeRate IS NOT NULL ORDER BY s.changeRate ASC, s.stockName ASC")
    List<Stock> findByChangeRateAscAndMarket(@Param("market") String market, Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market IN :markets AND s.changeRate IS NOT NULL ORDER BY s.changeRate DESC, s.stockName ASC")
    List<Stock> findByChangeRateDescAndMarkets(@Param("markets") List<String> markets, Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market IN :markets AND s.changeRate IS NOT NULL ORDER BY s.changeRate ASC, s.stockName ASC")
    List<Stock> findByChangeRateAscAndMarkets(@Param("markets") List<String> markets, Pageable pageable);

    // ?? 寃????

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND (LOWER(s.stockName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(s.stockCode) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY s.stockName ASC")
    List<Stock> searchActive(@Param("q") String q, Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market = :market AND (LOWER(s.stockName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(s.stockCode) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY s.stockName ASC")
    List<Stock> searchActiveByMarket(@Param("market") String market, @Param("q") String q, Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market IN :markets AND (LOWER(s.stockName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(s.stockCode) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY s.stockName ASC")
    List<Stock> searchActiveByMarkets(@Param("markets") List<String> markets, @Param("q") String q, Pageable pageable);

    // ?? ?쒓?珥앹븸????

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.marketCap IS NOT NULL ORDER BY s.marketCap DESC")
    List<Stock> findByMarketCapDesc(Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market = :market AND s.marketCap IS NOT NULL ORDER BY s.marketCap DESC")
    List<Stock> findByMarketCapDescAndMarket(@Param("market") String market, Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market IN :markets AND s.marketCap IS NOT NULL ORDER BY s.marketCap DESC")
    List<Stock> findByMarketCapDescAndMarkets(@Param("markets") List<String> markets, Pageable pageable);

    // ?? 嫄곕옒?됱닚 ??

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.tradingVolume IS NOT NULL ORDER BY s.tradingVolume DESC")
    List<Stock> findByTradingVolumeDesc(Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market = :market AND s.tradingVolume IS NOT NULL ORDER BY s.tradingVolume DESC")
    List<Stock> findByTradingVolumeDescAndMarket(@Param("market") String market, Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.market IN :markets AND s.tradingVolume IS NOT NULL ORDER BY s.tradingVolume DESC")
    List<Stock> findByTradingVolumeDescAndMarkets(@Param("markets") List<String> markets, Pageable pageable);

    // ?? 嫄곕옒??湲됱쬆 (?꾩씪 ?鍮? ??

    @Query("SELECT s FROM Stock s WHERE s.active = true AND s.etf = false AND s.tradingVolume IS NOT NULL AND s.prevTradingVolume IS NOT NULL AND s.prevTradingVolume > 0 ORDER BY (CAST(s.tradingVolume AS double) / s.prevTradingVolume) DESC")
    List<Stock> findByVolumeSurgeDesc(Pageable pageable);

    // ?? 湲고? ??

    List<Stock> findByMarket(String market);

    boolean existsByStockCode(String stockCode);
}
