package com.limo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订阅套餐表：月付 / 连续包月 / 年付 / 连续包年 → 价格 + 送积分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "plan")
public class Plan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 套餐码：free / monthly / monthly_auto / yearly / yearly_auto */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    /** 价格（元 / 月 或 元 / 年，视 period 而定） */
    private BigDecimal price;

    /** 购买该套餐赠送的积分 */
    @Column(nullable = false)
    private Integer grants;

    /** 笔记数量上限（配额：用户最多能存多少篇笔记，free 和付费不同） */
    @Column(nullable = false)
    private Integer noteLimit = 30;

    /** 周期：month / year */
    @Column(length = 10)
    private String period = "month";
}
