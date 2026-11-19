package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.RawNews;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RawNewsRepository extends JpaRepository<RawNews, Long> {

    boolean existsByOriginalLink(String originalLink);

    List<RawNews> findByProcessedFalseOrderByPublishedAtDesc();

    List<RawNews> findByClusterId(Long clusterId);
}
