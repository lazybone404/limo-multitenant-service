package com.limo.service;

import com.limo.common.BizException;
import com.limo.dto.AuthRequest;
import com.limo.dto.LoginResponse;
import com.limo.entity.Plan;
import com.limo.entity.User;
import com.limo.repository.PlanRepository;
import com.limo.repository.UserRepository;
import com.limo.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证业务：注册 / 登录。
 * 密码用 BCrypt 加密（不可逆）；登录签发 JWT（无状态）。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 注册：用户名唯一 → 密码加密 → 保存（送默认积分，见 free plan 配置） */
    public LoginResponse register(AuthRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BizException(409, "用户名已存在");
        }
        User user = User.builder()
                .username(req.getUsername())
                .password(encoder.encode(req.getPassword()))   // BCrypt 加密，不存明文
                .points(freePlanGrants())                     // 注册送 free 套餐积分
                .planCode("free")
                .status("ACTIVE")                               // @Builder 会忽略字段默认值，必须显式设
                .build();
        user = userRepository.save(user);
        return buildLogin(user);
    }

    /** 登录：校验密码 → 签 JWT */
    public LoginResponse login(AuthRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BizException(400, "用户不存在"));
        if (!encoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(400, "密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(403, "账号已停用");
        }
        return buildLogin(user);
    }

    private int freePlanGrants() {
        return planRepository.findByCode("free").map(Plan::getGrants).orElse(10);
    }

    private LoginResponse buildLogin(User user) {
        String token = jwtUtil.sign(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getPoints(), user.getPlanCode());
    }
}
