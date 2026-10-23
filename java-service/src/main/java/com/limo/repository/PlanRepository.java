package com.limo.repository;

import com.limo.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** 订阅套餐持久层 */
public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByCode(String code);
}
