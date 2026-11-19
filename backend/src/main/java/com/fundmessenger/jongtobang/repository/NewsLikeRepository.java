package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.NewsLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsLikeRepository extends JpaRepository<NewsLike, Long> {

    Optional<NewsLike> findByCouplingIdAndMemberId(Long couplingId, Long memberId);

    boolean existsByCouplingIdAndMemberId(Long couplingId, Long memberId);
}
