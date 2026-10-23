package com.limo.repository;

import com.limo.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** 来源持久层 */
public interface SourceRepository extends JpaRepository<Source, Long> {
    /** 找同一用户、同一来源标识、同类型的来源（避免重复建） */
    Optional<Source> findFirstByUserIdAndOriginAndSourceType(Long userId, String origin, String sourceType);
}
