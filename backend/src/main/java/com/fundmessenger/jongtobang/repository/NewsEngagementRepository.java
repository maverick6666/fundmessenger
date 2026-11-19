package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.NewsEngagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface NewsEngagementRepository extends JpaRepository<NewsEngagement, Long> {

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<NewsEngagement> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Modifying
    @Query("DELETE FROM NewsEngagement n WHERE n.createdAt BETWEEN :from AND :to")
    int deleteByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("SELECT MIN(n.createdAt) FROM NewsEngagement n")
    LocalDateTime findMinCreatedAt();

    @Query("SELECT MAX(n.createdAt) FROM NewsEngagement n")
    LocalDateTime findMaxCreatedAt();
}
