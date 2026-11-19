package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.StockRankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRankingSnapshotRepository extends JpaRepository<StockRankingSnapshot, Long> {

    Optional<StockRankingSnapshot> findByCacheKey(String cacheKey);
}
