package com.ai.api.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求 DTO
 *
 * <p>依据 spec.md "用户注册"：用户名（3-32 字母数字下划线）、邮箱（RFC 5322）、密码（≥8 位含字母+数字）。
 *
 * @author add-user-auth
 */
@Data
public class RegisterRequestDTO {

  @NotBlank(message = "用户名不能为空")
  @Size(min = 3, max = 32, message = "用户名长度须为 3-32 字符")
  @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母、数字、下划线")
  private String username;

  @NotBlank(message = "邮箱不能为空")
  @Email(message = "邮箱格式不合法")
  @Size(max = 128, message = "邮箱长度不能超过 128 字符")
  private String email;

  @NotBlank(message = "密码不能为空")
  @Size(min = 8, max = 64, message = "密码长度须为 8-64 字符")
  @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码须包含字母与数字")
  private String password;
}
