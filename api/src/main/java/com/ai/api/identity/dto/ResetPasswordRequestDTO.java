package com.ai.api.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求 DTO
 *
 * @author add-user-auth
 */
@Data
public class ResetPasswordRequestDTO {

  @NotBlank(message = "token 不能为空")
  private String token;

  @NotBlank(message = "新密码不能为空")
  @Size(min = 8, max = 64, message = "密码长度须为 8-64 字符")
  @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码须包含字母与数字")
  private String newPassword;
}
