package com.example.webauthn.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebAuthn 凭证实体类 - 对应数据库表 tbl_webauthn_credential
 * <p>
 * 说明：
 * - 存储用户的 WebAuthn 认证器凭证信息
 * - 一个用户可以有多个凭证（多个设备）
 * - UserHandle 存储在每个凭证记录中（方案二：最小化方案）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnCredentialEntity {

    /**
     * 主键 ID
     */
    private Integer id;

    /**
     * 用户 ID - 关联 tbl_account.UserId
     */
    private Integer userId;

    /**
     * 用户句柄 - 32 字节随机值的 Base64 编码
     * 说明：用于在 WebAuthn 流程中唯一标识用户，不暴露用户名等敏感信息
     */
    private String userHandle;

    /**
     * 凭证 ID - Base64 编码
     * 说明：认证器生成的凭证唯一标识符
     */
    private String credentialId;

    /**
     * 公钥 - COSE 格式的 Base64 编码
     * 说明：用于验证用户签名的公钥
     */
    private String publicKeyCose;

    /**
     * 签名计数器
     * 说明：
     * - 每次认证成功后递增
     * - 用于防止凭证克隆攻击
     * - 如果计数器没有递增，说明凭证可能被克隆
     */
    private Long signatureCount;

    /**
     * 创建时间 - 凭证注册时间
     */
    private LocalDateTime createTime;

    /**
     * 最后使用时间 - 最后一次认证成功的时间
     */
    private LocalDateTime lastUsedTime;
}
