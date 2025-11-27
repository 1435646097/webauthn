package com.example.webauthn.mapper;

import com.example.webauthn.entity.WebAuthnCredentialEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WebAuthn 凭证 Mapper 接口
 *
 * 说明：
 * - 提供 WebAuthn 凭证的数据库操作方法
 * - 使用 MyBatis 进行数据持久化
 */
@Mapper
public interface WebAuthnCredentialMapper {

    /**
     * 根据用户 ID 查询用户句柄
     *
     * @param userId 用户 ID
     * @return 用户句柄（Base64 编码）
     */
    String selectUserHandleByUserId(@Param("userId") Integer userId);

    /**
     * 根据用户句柄查询用户 ID
     *
     * @param userHandle 用户句柄（Base64 编码）
     * @return 用户 ID
     */
    Integer selectUserIdByUserHandle(@Param("userHandle") String userHandle);

    /**
     * 插入新的凭证记录
     *
     * @param entity 凭证实体对象
     * @return 影响的行数
     */
    int insert(WebAuthnCredentialEntity entity);

    /**
     * 更新凭证的签名计数器和最后使用时间
     *
     * @param userId 用户 ID
     * @param credentialId 凭证 ID
     * @param signatureCount 新的签名计数值
     * @param lastUsedTime 最后使用时间
     * @return 影响的行数
     */
    int updateSignatureCount(@Param("userId") Integer userId,
                             @Param("credentialId") String credentialId,
                             @Param("signatureCount") Long signatureCount,
                             @Param("lastUsedTime") LocalDateTime lastUsedTime);

    /**
     * 根据用户 ID 查询该用户的所有凭证
     *
     * @param userId 用户 ID
     * @return 凭证列表
     */
    List<WebAuthnCredentialEntity> selectByUserId(@Param("userId") Integer userId);

    /**
     * 根据凭证 ID 查询凭证
     *
     * @param credentialId 凭证 ID
     * @return 凭证实体对象
     */
    WebAuthnCredentialEntity selectByCredentialId(@Param("credentialId") String credentialId);

    /**
     * 根据凭证 ID 和用户句柄查询凭证
     *
     * @param credentialId 凭证 ID
     * @param userHandle 用户句柄
     * @return 凭证实体对象
     */
    WebAuthnCredentialEntity selectByCredentialIdAndUserHandle(@Param("credentialId") String credentialId,
                                                                @Param("userHandle") String userHandle);

    /**
     * 根据凭证 ID 查询所有匹配的凭证（用于检测冲突）
     *
     * @param credentialId 凭证 ID
     * @return 凭证列表
     */
    List<WebAuthnCredentialEntity> selectAllByCredentialId(@Param("credentialId") String credentialId);
}
