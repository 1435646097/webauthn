package com.example.webauthn.model;

/**
 * 凭证信息响应 - 用于设备管理界面展示
 *
 * @param credentialId 凭证 ID（Base64 编码）
 * @param signatureCount 签名计数器
 */
public record CredentialInfoResponse(
        String credentialId,
        long signatureCount
) {
}
