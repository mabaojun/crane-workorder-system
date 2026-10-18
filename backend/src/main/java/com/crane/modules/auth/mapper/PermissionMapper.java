package com.crane.modules.auth.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色权限查询：按角色 id 联查 sys_role_permission 与 sys_permission 取权限码集合。
 */
public interface PermissionMapper {

    /** 查询角色拥有的权限码，无授权记录时返回空列表 */
    @Select("SELECT p.code FROM sys_role_permission rp "
            + "JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0 "
            + "WHERE rp.role_id = #{roleId} AND rp.deleted = 0 "
            + "ORDER BY p.id")
    List<String> selectPermissionCodes(@Param("roleId") Long roleId);
}
