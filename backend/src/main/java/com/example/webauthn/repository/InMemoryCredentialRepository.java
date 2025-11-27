package com.example.webauthn.repository;

import com.example.webauthn.model.UserAccount;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存凭证仓库 - 用于存储和管理 WebAuthn 用户账户和凭证
 * <p>
 * 功能说明：
 * 1. 实现 Yubico WebAuthn 库的 CredentialRepository 接口
 * 2. 使用内存存储（ConcurrentHashMap）管理用户和凭证
 * 3. 提供用户创建、凭证添加、凭证更新、凭证查询等功能
 * <p>
 * 迁移建议：
 * - 生产环境应替换为数据库实现（如 JPA、MyBatis）
 * - 需要持久化存储用户账户和凭证信息
 * - 保持接口方法签名不变，只需修改内部实现
 * <p>
 * 数据结构：
 * - users: Map<用户名, 用户账户对象>
 * - credentials: Map<用户名, 凭证列表>
 */
@Repository
public class InMemoryCredentialRepository implements CredentialRepository {

    // 安全随机数生成器，用于生成用户句柄（User Handle）
    private final SecureRandom random = new SecureRandom();

    // 用户账户存储，key 为用户名，value 为用户账户对象（包含用户名、显示名称、用户句柄）
    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();

    // 凭证存储，key 为用户名，value 为该用户的凭证列表（一个用户可以有多个凭证）
    private final Map<String, List<RegisteredCredential>> credentials = new ConcurrentHashMap<>();

    /**
     * 确保用户存在 - 如果用户不存在则创建新用户
     *
     * @param username 用户名（唯一标识）
     * @param displayName 显示名称（用于 UI 展示）
     * @return UserAccount 用户账户对象
     */
    public UserAccount ensureUser(String username, String displayName) {
        return users.computeIfAbsent(username, name ->
                new UserAccount(name, displayName, randomHandle()));
    }

    /**
     * 添加凭证 - 为指定用户添加新的 WebAuthn 凭证
     * <p>
     * 说明：一个用户可以注册多个凭证（如多个设备）
     *
     * @param username 用户名
     * @param credential 已注册的凭证对象（包含凭证 ID、公钥、签名计数等）
     */
    public void addCredential(String username, RegisteredCredential credential) {
        credentials.computeIfAbsent(username, key -> new ArrayList<>()).add(credential);
    }

    /**
     * 更新凭证 - 更新指定凭证的签名计数器
     * <p>
     * 说明：
     * - 签名计数器用于防止凭证克隆攻击
     * - 每次认证成功后，签名计数器会递增
     * - 如果计数器没有递增，说明凭证可能被克隆
     *
     * @param username 用户名
     * @param credentialId 凭证 ID
     * @param signatureCount 新的签名计数值
     */
    public void updateCredential(String username, ByteArray credentialId, long signatureCount) {
        List<RegisteredCredential> registeredCredentials = credentials.get(username);
        if (registeredCredentials == null) {
            return;
        }
        // 遍历用户的所有凭证，找到匹配的凭证并更新签名计数
        for (int i = 0; i < registeredCredentials.size(); i++) {
            RegisteredCredential credential = registeredCredentials.get(i);
            if (credential.getCredentialId().equals(credentialId)) {
                // 重建凭证对象，只更新签名计数
                registeredCredentials.set(i, RegisteredCredential.builder()
                        .credentialId(credential.getCredentialId())
                        .userHandle(credential.getUserHandle())
                        .publicKeyCose(credential.getPublicKeyCose())
                        .signatureCount(signatureCount)
                        .build());
                break;
            }
        }
    }

    /**
     * 获取用户的凭证 ID 列表 - CredentialRepository 接口方法
     * <p>
     * 说明：
     * - 在认证流程中，返回该用户可以使用的凭证列表
     * - 前端会根据这个列表提示用户选择哪个凭证进行认证
     *
     * @param username 用户名
     * @return Set<PublicKeyCredentialDescriptor> 凭证描述符集合
     */
    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        List<RegisteredCredential> registeredCredentials = credentials.getOrDefault(username, Collections.emptyList());
        return registeredCredentials.stream()
                .map(cred -> PublicKeyCredentialDescriptor.builder()
                        .id(cred.getCredentialId())
                        .build())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 根据用户名获取用户句柄 - CredentialRepository 接口方法
     * <p>
     * 说明：
     * - 用户句柄是用户的唯一标识符（32 字节随机值）
     * - 用于在认证流程中关联用户和凭证
     *
     * @param username 用户名
     * @return Optional<ByteArray> 用户句柄（如果用户存在）
     */
    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return Optional.ofNullable(users.get(username)).map(UserAccount::getUserHandle);
    }

    /**
     * 根据用户句柄获取用户名 - CredentialRepository 接口方法
     * <p>
     * 说明：
     * - 在可发现凭证（Discoverable Credential）认证流程中使用
     * - 前端返回用户句柄，后端根据句柄查找用户名
     *
     * @param userHandle 用户句柄
     * @return Optional<String> 用户名（如果找到）
     */
    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return users.values().stream()
                .filter(user -> user.getUserHandle().equals(userHandle))
                .findFirst()
                .map(UserAccount::getUsername);
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
        return credentials.values().stream()
                .flatMap(List::stream)
                .filter(cred -> cred.getCredentialId().equals(credentialId)
                        && cred.getUserHandle().equals(userHandle))
                .findFirst();
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
        return credentials.values().stream()
                .flatMap(List::stream)
                .filter(cred -> cred.getCredentialId().equals(credentialId))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取用户的所有凭证 - 自定义方法
     * <p>
     * 说明：
     * - 用于管理界面展示用户已注册的所有凭证
     * - 可以查看用户有哪些设备已注册
     *
     * @param username 用户名
     * @return List<RegisteredCredential> 用户的凭证列表
     */
    public List<RegisteredCredential> getCredentials(String username) {
        return credentials.getOrDefault(username, Collections.emptyList());
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
