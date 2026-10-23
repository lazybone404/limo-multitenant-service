package com.limo.config;

import com.limo.entity.Plan;
import com.limo.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 启动时初始化订阅套餐（幂等，已存在则跳过）。
 * free=30 篇 / monthly、monthly_auto=500 / yearly、yearly_auto=3000；积分按套餐送。
 */
@Component
@RequiredArgsConstructor
public class PlanSeeder implements CommandLineRunner {

    private final PlanRepository planRepo;

    @Override
    public void run(String... args) {
        seed("free", "免费版", BigDecimal.ZERO, 10, "month", 30);
        seed("monthly", "月度会员", new BigDecimal("19.9"), 500, "month", 500);
        seed("monthly_auto", "连续包月", new BigDecimal("19.9"), 500, "month", 500);
        seed("yearly", "年度会员", new BigDecimal("199"), 5000, "year", 3000);
        seed("yearly_auto", "连续包年", new BigDecimal("199"), 5000, "year", 3000);
    }

    private void seed(String code, String name, BigDecimal price, int grants, String period, int noteLimit) {
        if (planRepo.findByCode(code).isPresent()) return;
        planRepo.save(Plan.builder()
                .code(code).name(name).price(price).grants(grants)
                .period(period).noteLimit(noteLimit).build());
    }
}
