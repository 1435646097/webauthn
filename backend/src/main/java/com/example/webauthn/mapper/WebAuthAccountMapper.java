package com.example.webauthn.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户账户 Mapper 接口
 *
 * 说明：
 * - 查询 tbl_account 表的用户信息
 * - 用于 WebAuthn 流程中获取用户基本信息
 */
@Mapper
public interface WebAuthAccountMapper {

    /**
     * 根据登录账号查询用户 ID
     *
     * @param logonId 登录账号
     * @return 用户 ID
     */
    Integer selectUserIdByLogonId(@Param("logonId") String logonId);

    /**
     * 根据用户 ID 查询用户名（姓名）
     *
     * @param userId 用户 ID
     * @return 用户名（姓名）
     */
    String selectUserNameByUserId(@Param("userId") Integer userId);

    /**
     * 根据登录账号查询用户名（姓名）
     *
     * @param logonId 登录账号
     * @return 用户名（姓名）
     */
    String selectUserNameByLogonId(@Param("logonId") String logonId);

    /**
     * 检查用户是否存在
     *
     * @param logonId 登录账号
     * @return 存在返回 1，不存在返回 0
     */
    Integer existsByLogonId(@Param("logonId") String logonId);

    /**
     * 根据用户 ID 查询登录账号
     *
     * @param userId 用户 ID
     * @return 登录账号
     */
    String selectLogonIdByUserId(@Param("userId") Integer userId);
}
