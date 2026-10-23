package com.limo.repository;

import com.limo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 用户持久层。关键：积分扣减用【条件更新】防超扣/负积分（并发安全），
 * 也留了加积分/改套餐的方法（订阅购买时用）——这是"分布式锁"的进阶口子。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    /** 扣积分：只有余额 >= cost 才扣（原子，防并发超扣），返回更新行数（0=余额不足） */
    @Modifying
    @Transactional
    @Query("update User u set u.points = u.points - :cost where u.id = :id and u.points >= :cost")
    int deductPoints(@Param("id") Long id, @Param("cost") int cost);

    /** 加积分：订阅购买赠送时用 */
    @Modifying
    @Transactional
    @Query("update User u set u.points = u.points + :add where u.id = :id")
    int addPoints(@Param("id") Long id, @Param("add") int add);

    /** 切换套餐：订阅购买时用 */
    @Modifying
    @Transactional
    @Query("update User u set u.planCode = :code where u.id = :id")
    int updatePlan(@Param("id") Long id, @Param("code") String code);
}
