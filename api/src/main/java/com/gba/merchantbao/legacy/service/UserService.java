package com.gba.merchantbao.legacy.service;

import com.gba.merchantbao.legacy.dto.UserLoginDTO;
import com.gba.merchantbao.legacy.entity.User;

public interface UserService {
        /**
        * 微信登录
        * @param userLoginDTO
        * @return
        */
        User wxLogin(UserLoginDTO userLoginDTO);
}
