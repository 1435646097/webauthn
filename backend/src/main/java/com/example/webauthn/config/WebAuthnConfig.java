package com.example.webauthn.config;

import com.example.webauthn.repository.DatabaseCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * WebAuthn 配置类
 *
 * 说明：
 * - 配置 RelyingParty（依赖方）实例
 * - 使用 DatabaseCredentialRepository 进行数据库持久化
 * - 配置 RP ID、RP Name 和允许的来源（Origins）
 */
@Configuration
@EnableConfigurationProperties(WebAuthnProperties.class)
public class WebAuthnConfig {

    /**
     * 创建 RelyingParty Bean
     *
     * 说明：
     * - RelyingParty 是 Yubico WebAuthn 库的核心组件
     * - 负责生成和验证 WebAuthn 挑战
     * - 使用 DatabaseCredentialRepository 替代内存实现
     *
     * @param properties WebAuthn 配置属性
     * @param repository 数据库凭证仓库
     * @return RelyingParty 实例
     */
    @Bean
    public RelyingParty relyingParty(WebAuthnProperties properties, DatabaseCredentialRepository repository) {
        Set<String> origins = new HashSet<>(properties.getOrigins());
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(properties.getRpId())
                        .name(properties.getRpName())
                        .build())
                .credentialRepository(repository)
                .origins(origins)
                .build();
    }
}
