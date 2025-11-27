package com.example.webauthn.repository;

import com.example.webauthn.entity.WebAuthnCredentialEntity;
import com.example.webauthn.mapper.AccountMapper;
import com.example.webauthn.mapper.WebAuthnCredentialMapper;
import com.example.webauthn.model.UserAccount;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于数据库的凭证仓库 - 用于存储和管理 WebAuthn 用户账户和凭证
 * <p>
 * 功能说明：
 * 1. 实现 Yubico WebAuthn 库的 CredentialRepository 接口
 * 2. 使用 MySQL 数据库持久化存储用户和凭证
 * 3. 提供用户创建、凭证添加、凭证更新、凭证查询等功能
 * <p>
 * 数据表：
 * - tbl_account: 用户账户表（已存在）
 * - tbl_webauthn_credential: WebAuthn 凭证表（新增）
 * <p>
 * 字段映射：
 * - username: 使用 tbl_account.LogonId（登录账号）
 * - displayName: 使用 tbl_account.UserName（用户姓名）
 * - userId: 使用 tbl_account.UserId（用户主键）
 * - userHandle: 存储在 tbl_webauthn_credential.UserHandle（32 字节随机值）
 */
@Slf4j
@Repository
public class DatabaseCredentialRepository implements CredentialRepository {

    // 安全随机数生成器，用于生成用户句柄（User Handle）
    private final SecureRandom random = new SecureRandom();

    // WebAuthn 凭证 Mapper
    private final WebAuthnCredentialMapper credentialMapper;

    // 用户账户 Mapper
    private final AccountMapper accountMapper;

    public DatabaseCredentialRepository(WebAuthnCredentialMapper credentialMapper,
                                        AccountMapper accountMapper) {
        this.credentialMapper = credentialMapper;
        this.accountMapper = accountMapper;
    }

    /**
     * 确保用户存在 - 如果用户不存在则创建新用户
     * <p>
     * 说明：
     * - username 参数对应 tbl_account.LogonId（登录账号）
     * - displayName 参数对应 tbl_account.UserName（用户姓名）
     * - 如果用户在 tbl_account 中不存在，抛出异常（用户应该先在系统中注册）
     * - 如果用户在 tbl_webauthn_credential 中没有 UserHandle，则生成并存储
     *
     * @param username 登录账号（LogonId）
     * @param displayName 用户姓名（UserName）
     * @return UserAccount 用户账户对象
     */
    @Transactional
    public UserAccount ensureUser(String username, String displayName) {
        // 1. 从 tbl_account 查询用户 ID
        Integer userId = accountMapper.selectUserIdByLogonId(username);
        if (userId == null) {
            throw new IllegalArgumentException("用户不存在，请先在系统中注册用户：" + username);
        }

        // 2. 查询用户姓名（如果 displayName 为空，则从数据库获取）
        String actualDisplayName = displayName;
        if (actualDisplayName == null || actualDisplayName.isBlank()) {
            actualDisplayName = accountMapper.selectUserNameByUserId(userId);
        }

        // 3. 查询或生成 UserHandle
        String userHandleBase64 = credentialMapper.selectUserHandleByUserId(userId);
        ByteArray userHandle;

        if (userHandleBase64 == null) {
            // 用户首次使用 WebAuthn，生成新的 UserHandle
            userHandle = randomHandle();
            // 注意：UserHandle 会在添加凭证时一起存储，这里不需要单独插入
        } else {
            // 用户已有 UserHandle，从 Base64 解码
            userHandle = ByteArray.fromBase64(userHandleBase64);
        }
        log.info("userHandle={}", userHandle.getBase64());
        return new UserAccount(username, actualDisplayName, userHandle);
    }

    /**
     * 添加凭证 - 为指定用户添加新的 WebAuthn 凭证
     * <p>
     * 说明：
     * - 一个用户可以注册多个凭证（如多个设备）
     * - 将凭证信息存储到 tbl_webauthn_credential 表
     *
     * @param username 登录账号（LogonId）
     * @param credential 已注册的凭证对象（包含凭证 ID、公钥、签名计数等）
     */
    @Transactional
    public void addCredential(String username, RegisteredCredential credential) {
        // 1. 查询用户 ID
        Integer userId = accountMapper.selectUserIdByLogonId(username);
        if (userId == null) {
            throw new IllegalArgumentException("用户不存在：" + username);
        }

        // 2. 构建凭证实体对象
        WebAuthnCredentialEntity entity = WebAuthnCredentialEntity.builder()
                .userId(userId)
                .userHandle(credential.getUserHandle().getBase64()) // 存储 Base64 编码的 UserHandle
                .credentialId(credential.getCredentialId().getBase64()) // 存储 Base64 编码的凭证 ID
                .publicKeyCose(credential.getPublicKeyCose().getBase64()) // 存储 Base64 编码的公钥
                .signatureCount(credential.getSignatureCount())
                .createTime(LocalDateTime.now())
                .lastUsedTime(null)
                .build();

        // 3. 插入到数据库
        credentialMapper.insert(entity);
    }

    /**
     * 更新凭证 - 更新指定凭证的签名计数器
     * <p>
     * 说明：
     * - 签名计数器用于防止凭证克隆攻击
     * - 每次认证成功后，签名计数器会递增
     * - 如果计数器没有递增，说明凭证可能被克隆
     *
     * @param username 登录账号（LogonId）
     * @param credentialId 凭证 ID
     * @param signatureCount 新的签名计数值
     */
    @Transactional
    public void updateCredential(String username, ByteArray credentialId, long signatureCount) {
        // 1. 查询用户 ID
        Integer userId = accountMapper.selectUserIdByLogonId(username);
        if (userId == null) {
            return;
        }

        // 2. 更新签名计数器和最后使用时间
        credentialMapper.updateSignatureCount(
                userId,
                credentialId.getBase64(),
                signatureCount,
                LocalDateTime.now()
        );
    }

    /**
     * 获取用户的凭证 ID 列表 - CredentialRepository 接口方法
     * <p>
     * 说明：
     * - 在认证流程中，返回该用户可以使用的凭证列表
     * - 前端会根据这个列表提示用户选择哪个凭证进行认证
     *
     * @param username 登录账号（LogonId）
     * @return Set<PublicKeyCredentialDescriptor> 凭证描述符集合
     */
    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        // 1. 查询用户 ID
        Integer userId = accountMapper.selectUserIdByLogonId(username);
        if (userId == null) {
            log.info("[WebAuthn] 用户不存在: {}", username);
            return Collections.emptySet();
        }

        // 2. 查询用户的所有凭证
        List<WebAuthnCredentialEntity> credentials = credentialMapper.selectByUserId(userId);
        log.info("[WebAuthn] 用户 {} 的凭证数量: {}", username, credentials.size());

        // 3. 转换为 PublicKeyCredentialDescriptor
        Set<PublicKeyCredentialDescriptor> result = credentials.stream()
                .map(cred -> {
                    log.info("[WebAuthn] 凭证 ID: {}", cred.getCredentialId());
                    return PublicKeyCredentialDescriptor.builder()
                            .id(ByteArray.fromBase64(cred.getCredentialId()))
                            .build();
                })
                .collect(Collectors.toSet());

        return result;
    }

    /**
     * 根据用户名获取用户句柄 - CredentialRepository 接口方法
     * <p>
     * 说明：
     * - 用户句柄是用户的唯一标识符（32 字节随机值）
     * - 用于在认证流程中关联用户和凭证
     *
     * @param username 登录账号（LogonId）
     * @return Optional<ByteArray> 用户句柄（如果用户存在）
     */
    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        // 1. 查询用户 ID
        Integer userId = accountMapper.selectUserIdByLogonId(username);
        if (userId == null) {
            return Optional.empty();
        }

        // 2. 查询 UserHandle
        String userHandleBase64 = credentialMapper.selectUserHandleByUserId(userId);
        if (userHandleBase64 == null) {
            return Optional.empty();
        }

        // 3. 从 Base64 解码
        return Optional.of(ByteArray.fromBase64(userHandleBase64));
    }

    /**
     * 根据用户句柄获取用户名 - CredentialRepository 接口方法
     * <p>
     * 说明：
     * - 在可发现凭证（Discoverable Credential）认证流程中使用
     * - 前端返回用户句柄，后端根据句柄查找用户名
     *
     * @param userHandle 用户句柄
     * @return Optional<String> 登录账号（LogonId）（如果找到）
     */
    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        // 1. 根据 UserHandle 查询用户 ID
        Integer userId = credentialMapper.selectUserIdByUserHandle(userHandle.getBase64());
        if (userId == null) {
            return Optional.empty();
        }

        // 2. 根据用户 ID 查询 LogonId
        String logonId = accountMapper.selectLogonIdByUserId(userId);
        return Optional.ofNullable(logonId);
    }

    /**
     * 查找凭证 - 根据凭证 ID 和用户句柄查找凭证（CredentialRepository 接口方法）
     * <p>
     * 说明：
     * - 在认证流程中，验证前端返回的凭证是否存在
     * - 同时验证凭证 ID 和用户句柄，确保凭证属于该用户
     *
     * @param credentialId 凭证 ID
     * @param userHandle 用户句柄
     * @return Optional<RegisteredCredential> 已注册的凭证（如果找到）
     */
    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        // 1. 查询凭证
        WebAuthnCredentialEntity entity = credentialMapper.selectByCredentialIdAndUserHandle(
                credentialId.getBase64(),
                userHandle.getBase64()
        );

        if (entity == null) {
            return Optional.empty();
        }

        // 2. 转换为 RegisteredCredential
        RegisteredCredential credential = RegisteredCredential.builder()
                .credentialId(ByteArray.fromBase64(entity.getCredentialId()))
                .userHandle(ByteArray.fromBase64(entity.getUserHandle()))
                .publicKeyCose(ByteArray.fromBase64(entity.getPublicKeyCose()))
                .signatureCount(entity.getSignatureCount())
                .build();

        return Optional.of(credential);
    }

    /**
     * 查找所有匹配的凭证 - CredentialRepository 接口方法
     * <p>
     * 说明：
     * - 返回所有匹配该凭证 ID 的凭证（理论上应该只有一个）
     * - 用于检测凭证 ID 冲突
     *
     * @param credentialId 凭证 ID
     * @return Set<RegisteredCredential> 匹配的凭证集合
     */
    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        // 1. 查询所有匹配的凭证
        List<WebAuthnCredentialEntity> entities = credentialMapper.selectAllByCredentialId(credentialId.getBase64());

        // 2. 转换为 RegisteredCredential
        return entities.stream()
                .map(entity -> RegisteredCredential.builder()
                        .credentialId(ByteArray.fromBase64(entity.getCredentialId()))
                        .userHandle(ByteArray.fromBase64(entity.getUserHandle()))
                        .publicKeyCose(ByteArray.fromBase64(entity.getPublicKeyCose()))
                        .signatureCount(entity.getSignatureCount())
                        .build())
                .collect(Collectors.toSet());
    }

    /**
     * 获取用户的所有凭证 - 自定义方法
     * <p>
     * 说明：
     * - 用于管理界面展示用户已注册的所有凭证
     * - 可以查看用户有哪些设备已注册
     *
     * @param username 登录账号（LogonId）
     * @return List<RegisteredCredential> 用户的凭证列表
     */
    public List<RegisteredCredential> getCredentials(String username) {
        // 1. 查询用户 ID
        Integer userId = accountMapper.selectUserIdByLogonId(username);
        if (userId == null) {
            return Collections.emptyList();
        }

        // 2. 查询用户的所有凭证
        List<WebAuthnCredentialEntity> entities = credentialMapper.selectByUserId(userId);

        // 3. 转换为 RegisteredCredential
        return entities.stream()
                .map(entity -> RegisteredCredential.builder()
                        .credentialId(ByteArray.fromBase64(entity.getCredentialId()))
                        .userHandle(ByteArray.fromBase64(entity.getUserHandle()))
                        .publicKeyCose(ByteArray.fromBase64(entity.getPublicKeyCose()))
                        .signatureCount(entity.getSignatureCount())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 检查用户是否已注册过 WebAuthn 凭证
     * <p>
     * 说明：
     * - 用于判断用户是否已经注册过设备
     * - 可以在前端决定显示"注册"还是"登录"按钮
     *
     * @param username 登录账号（LogonId）
     * @return 已注册返回 true，未注册返回 false
     */
    public boolean hasCredentials(String username) {
        // 1. 查询用户 ID
        Integer userId = accountMapper.selectUserIdByLogonId(username);
        if (userId == null) {
            return false;
        }

        // 2. 检查是否有凭证
        return credentialMapper.hasCredentials(userId);
    }

    /**
     * 获取用户的凭证数量
     *
     * @param username 登录账号（LogonId）
     * @return 凭证数量
     */
    public int getCredentialCount(String username) {
        // 1. 查询用户 ID
        Integer userId = accountMapper.selectUserIdByLogonId(username);
        if (userId == null) {
            return 0;
        }

        // 2. 统计凭证数量
        return credentialMapper.countByUserId(userId);
    }

    /**
     * 删除凭证 - 用户可以删除不再使用的设备凭证
     * <p>
     * 说明：
     * - 用于设备管理功能
     * - 用户可以删除丢失或不再使用的设备
     *
     * @param username 登录账号（LogonId）
     * @param credentialId 凭证 ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteCredential(String username, ByteArray credentialId) {
        // 1. 查询用户 ID
        Integer userId = accountMapper.selectUserIdByLogonId(username);
        if (userId == null) {
            return false;
        }

        // 2. 删除凭证
        int rows = credentialMapper.deleteCredential(userId, credentialId.getBase64());
        return rows > 0;
    }

    /**
     * 删除用户的所有凭证
     * <p>
     * 说明：
     * - 用于用户注销或重置 WebAuthn 设置
     * - 删除后用户需要重新注册设备
     *
     * @param username 登录账号（LogonId）
     * @return 删除的凭证数量
     */
    @Transactional
    public int deleteAllCredentials(String username) {
        // 1. 查询用户 ID
        Integer userId = accountMapper.selectUserIdByLogonId(username);
        if (userId == null) {
            return 0;
        }

        // 2. 删除所有凭证
        return credentialMapper.deleteAllByUserId(userId);
    }

    /**
     * 生成随机用户句柄 - 私有辅助方法
     * <p>
     * 说明：
     * - 用户句柄是 32 字节的随机值
     * - 用于唯一标识用户，不暴露用户名等敏感信息
     * - 使用 SecureRandom 确保随机性和安全性
     *
     * @return ByteArray 32 字节的随机用户句柄
     */
    private ByteArray randomHandle() {
        byte[] buffer = new byte[32];
        random.nextBytes(buffer);
        return new ByteArray(buffer);
    }
}
