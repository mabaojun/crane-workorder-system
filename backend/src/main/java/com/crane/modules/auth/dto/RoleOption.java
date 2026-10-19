package com.crane.modules.auth.dto;

/**
 * 可选角色项：一人多角色时供小程序登录后选择本次使用的身份。
 */
public record RoleOption(String code, String name) {
}
