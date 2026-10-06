package com.countmaske.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString(exclude = "password")
@Schema(description = "员工登录时传递的数据模型")
public class EmployeeLoginDTO implements Serializable {

    @Schema(description ="用户名")
    private String username;

    @Schema(description ="密码")
    private String password;

}
