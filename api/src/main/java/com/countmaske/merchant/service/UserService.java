package com.countmaske.merchant.service;

import com.countmaske.merchant.dto.UserLoginDTO;
import com.countmaske.merchant.entity.User;

public interface UserService {
        /**
        * 微信登录
        * @param userLoginDTO
        * @return
        */
        User wxLogin(UserLoginDTO userLoginDTO);
}
