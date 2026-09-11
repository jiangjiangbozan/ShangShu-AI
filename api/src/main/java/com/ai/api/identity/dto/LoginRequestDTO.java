package com.ai.api.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求 DTO
 *
 * <p>依据 spec.md "用户登录"：支持邮箱或用户名 + 密码。
 *
 * @author add-user-auth
 */
@Data
public class LoginRequestDTO {

  @NotBlank(message = "账号不能为空")
  @Size(max = 128, message = "账号长度不能超过 128 字符")
  private String account;

  @NotBlank(message = "密码不能为空")
  @Size(max = 64, message = "密码长度不能超过 64 字符")
  private String password;
}
