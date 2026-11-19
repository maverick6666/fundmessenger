package com.fundmessenger.club.repository;

import com.fundmessenger.club.entity.CarouselMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CarouselMemberRepository extends JpaRepository<CarouselMember, Long> {

    List<CarouselMember> findByClubIdOrderByDisplayOrderAsc(Long clubId);

    @Query("SELECT cm FROM CarouselMember cm " +
           "JOIN FETCH cm.clubMembership m " +
           "JOIN FETCH m.user " +
           "LEFT JOIN FETCH m.generations " +
           "WHERE cm.club.id = :clubId ORDER BY cm.displayOrder")
    List<CarouselMember> findByClubIdWithMembershipDetails(Long clubId);

    Optional<CarouselMember> findByClubIdAndClubMembershipId(Long clubId, Long membershipId);

    void deleteByClubIdAndClubMembershipId(Long clubId, Long membershipId);

    boolean existsByClubIdAndClubMembershipId(Long clubId, Long membershipId);
}
