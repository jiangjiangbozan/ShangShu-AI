package com.ai.api.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 忘记密码请求 DTO
 *
 * @author add-user-auth
 */
@Data
public class ForgotPasswordRequestDTO {

  @NotBlank(message = "邮箱不能为空")
  @Email(message = "邮箱格式不合法")
  @Size(max = 128)
  private String email;
}
