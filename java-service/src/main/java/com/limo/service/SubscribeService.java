package com.limo.service;

import com.limo.common.BizException;
import com.limo.config.UserContext;
import com.limo.dto.SubscribeRequest;
import com.limo.dto.SubscribeResponse;
import com.limo.entity.Plan;
import com.limo.entity.User;
import com.limo.repository.PlanRepository;
import com.limo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 订阅购买：选套餐 → 赠送积分 + 切换套餐（更新 planCode，连带笔记上限）。
 * 用条件更新(updatePlan/addPoints)保证并发安全（进阶可换分布式锁）。
 */
@Service
@RequiredArgsConstructor
public class SubscribeService {

    private final PlanRepository planRepo;
    private final UserRepository userRepo;

    public SubscribeResponse subscribe(SubscribeRequest req) {
        Long uid = UserContext.get();
        Plan plan = planRepo.findByCode(req.getPlanCode())
                .orElseThrow(() -> new BizException(404, "套餐不存在"));

        userRepo.addPoints(uid, plan.getGrants());   // 套餐赠送积分
        userRepo.updatePlan(uid, plan.getCode());    // 切换套餐

        User u = userRepo.findById(uid).orElseThrow(() -> new BizException(404, "用户不存在"));
        return new SubscribeResponse(plan.getCode(), plan.getName(), u.getPoints(), plan.getNoteLimit());
    }
}
