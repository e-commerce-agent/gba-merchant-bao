package com.gba.merchantbao.legacy.controller.user;

import com.gba.merchantbao.legacy.constant.JwtClaimsConstant;
import com.gba.merchantbao.legacy.dto.UserLoginDTO;
import com.gba.merchantbao.legacy.entity.User;
import com.gba.merchantbao.legacy.properties.JwtProperties;
import com.gba.merchantbao.legacy.result.Result;
import com.gba.merchantbao.legacy.service.UserService;
import com.gba.merchantbao.legacy.utils.JwtUtil;
import com.gba.merchantbao.legacy.vo.UserLoginVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/user")
@Tag(name = "C端用户相关接口")
@Slf4j
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    @PostMapping("/login")
    @Operation(summary ="微信登录")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("微信用户登录: {}", userLoginDTO.getCode());

        // 微信登录
        User user = userService.wxLogin(userLoginDTO);

        // 为微信用户生成 jwt 令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .token(token)
                .build();

        return Result.success(userLoginVO);
    }
}
