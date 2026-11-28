package com.example.aiagent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginReq(
    /** 账号：邮箱或用户名 */
    val account: String,
    val password: String,
)

@Serializable
data class RegisterReq(
    val username: String,
    val email: String,
    val password: String,
)

@Serializable
data class LogoutReq(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class RefreshReq(
    val refreshToken: String,
)

/** 当前用户信息（GET /users/me） */
@Serializable
data class UserInfo(
    val email: String = "",
    val lastLoginAt: String? = null,
    val permissions: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    val status: Int = 0,
    val userId: Long = 0,
    val username: String = "",
)

/** 登录 / 刷新接口返回数据 */
@Serializable
data class LoginResp(
    val userId: Long = 0,
    val username: String = "",
    val email: String = "",
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Long = 0,
    val csrfToken: String? = null,
)