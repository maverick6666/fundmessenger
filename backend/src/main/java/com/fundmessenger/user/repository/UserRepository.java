package com.fundmessenger.user.repository;

import com.fundmessenger.user.entity.SocialProvider;
import com.fundmessenger.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findBySocialProviderAndSocialProviderId(
            SocialProvider socialProvider, String socialProviderId);

    /** MAU: 지정 시간 이후에 활동한 사용자 수 */
    long countByLastActiveAtAfter(LocalDateTime since);
}
