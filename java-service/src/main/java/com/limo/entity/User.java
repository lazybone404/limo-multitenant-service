package com.limo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户表：账号 + 积分余额 + 当前套餐。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    /** BCrypt 加密后的密码（绝不明文） */
    @Column(nullable = false, length = 100)
    private String password;

    /** 积分余额 */
    @Column(nullable = false)
    private Integer points;

    /** 当前套餐 code：free / monthly / monthly_auto / yearly / yearly_auto */
    @Column(length = 20)
    private String planCode = "free";

    /** ACTIVE / BANNED */
    @Column(length = 20)
    private String status = "ACTIVE";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
