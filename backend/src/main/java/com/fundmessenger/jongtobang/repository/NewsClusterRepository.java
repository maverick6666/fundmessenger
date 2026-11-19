package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.NewsCluster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsClusterRepository extends JpaRepository<NewsCluster, Long> {

    List<NewsCluster> findByValidTrue();
}
