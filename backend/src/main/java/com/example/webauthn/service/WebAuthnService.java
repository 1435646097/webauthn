package com.example.webauthn.service;

import com.example.webauthn.model.*;
import com.example.webauthn.repository.DatabaseCredentialRepository;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebAuthn 服务类 - 处理无密码身份验证的核心业务逻辑
 *
 * 主要功能：
 * 1. 注册流程：生成注册挑战 -> 验证注册响应 -> 存储凭证
 * 2. 认证流程：生成认证挑战 -> 验证认证响应 -> 更新签名计数
 *
 * 依赖项：
 * - RelyingParty: Yubico WebAuthn 库的核心组件，需要配置 RP ID 和 RP Name
 * - DatabaseCredentialRepository: 数据库凭证存储仓库（持久化存储）
 */
@Service
public class WebAuthnService {

    // Yubico WebAuthn 库的依赖方（Relying Party）实例，负责生成和验证 WebAuthn 挑战
    private final RelyingParty relyingParty;

    // 数据库凭证存储仓库，用于管理用户账户和已注册的凭证
    private final DatabaseCredentialRepository credentialRepository;

    // 临时存储注册请求的挑战信息，key 为用户名，value 为注册选项（包含 challenge）
    private final Map<String, PublicKeyCredentialCreationOptions> registrationRequests = new ConcurrentHashMap<>();

    // 临时存储认证请求的挑战信息，key 为用户名或 challenge，value 为断言请求
    private final Map<String, AssertionRequest> assertionRequests = new ConcurrentHashMap<>();

    /**
     * 构造函数 - 通过依赖注入初始化服务
     *
     * @param relyingParty WebAuthn 依赖方实例（需在配置类中创建）
     * @param credentialRepository 数据库凭证存储仓库
     */
    public WebAuthnService(RelyingParty relyingParty, DatabaseCredentialRepository credentialRepository) {
        this.relyingParty = relyingParty;
        this.credentialRepository = credentialRepository;
    }

    /**
     * 开始注册流程 - 生成注册挑战（challenge）
     *
     * 流程说明：
     * 1. 验证用户名不为空
     * 2. 确保用户存在（不存在则创建）
     * 3. 配置认证器选择条件（平台认证器、常驻密钥、用户验证）
     * 4. 生成注册选项（包含 challenge、用户信息、RP 信息等）
     * 5. 缓存注册请求，用于后续验证
     *
     * @param request 注册开始请求，包含用户名、显示名称、是否需要常驻密钥
     * @return PublicKeyCredentialCreationOptions 返回给前端的注册选项（需序列化为 JSON）
     */
    public PublicKeyCredentialCreationOptions startRegistration(RegistrationStartRequest request) {
        Assert.hasText(request.username(), "用户名不能为空");
        // 确保用户存在，不存在则创建新用户
        UserAccount user = credentialRepository.ensureUser(request.username(), request.displayName());

        // 配置认证器选择条件
        AuthenticatorSelectionCriteria selectionCriteria = AuthenticatorSelectionCriteria.builder()
                .authenticatorAttachment(AuthenticatorAttachment.PLATFORM) // 平台认证器（如 Windows Hello、Touch ID）
                .residentKey(request.residentKey() ? ResidentKeyRequirement.REQUIRED : ResidentKeyRequirement.PREFERRED) // 常驻密钥（可发现凭证）
                .userVerification(UserVerificationRequirement.REQUIRED) // 要求用户验证（生物识别或 PIN）
                .build();

        // 生成注册选项（包含 challenge 等信息）
        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(UserIdentity.builder()
                                .name(user.getUsername())
                                .displayName(user.getDisplayName())
                                .id(user.getUserHandle()) // 用户句柄（随机生成的唯一标识）
                                .build())
                        .authenticatorSelection(selectionCriteria)
                        .build()
        );

        // 缓存注册请求，用于验证前端返回的响应
        registrationRequests.put(user.getUsername(), options);
        return options;
    }

    /**
     * 完成注册流程 - 验证前端返回的凭证并存储
     *
     * 流程说明：
     * 1. 从缓存中获取并移除之前生成的注册选项（包含 challenge）
     * 2. 验证前端返回的凭证响应（签名、来源等）
     * 3. 提取公钥和凭证 ID
     * 4. 将凭证存储到仓库中
     *
     * @param request 注册完成请求，包含用户名和前端生成的凭证
     * @return RegistrationFinishResponse 注册结果
     * @throws RegistrationFailedException 注册验证失败时抛出
     */
    public RegistrationFinishResponse finishRegistration(RegistrationFinishRequest request) throws RegistrationFailedException {
        // 获取之前缓存的注册选项（包含 challenge），用于验证响应
        PublicKeyCredentialCreationOptions creationOptions = registrationRequests.remove(request.username());
        if (creationOptions == null) {
            throw new IllegalStateException("注册挑战已过期或不存在");
        }
        // 验证前端返回的凭证（验证签名、来源、challenge 等）
        RegistrationResult result = relyingParty.finishRegistration(
                FinishRegistrationOptions.builder()
                        .request(creationOptions)
                        .response(request.credential())
                        .build());

        // 确保用户存在
        UserAccount user = credentialRepository.ensureUser(request.username(), request.username());
        // 构建已注册凭证对象
        RegisteredCredential credential = RegisteredCredential.builder()
                .credentialId(result.getKeyId().getId()) // 凭证 ID（唯一标识）
                .userHandle(user.getUserHandle()) // 用户句柄
                .publicKeyCose(result.getPublicKeyCose()) // 公钥（COSE 格式）
                .signatureCount(result.getSignatureCount()) // 签名计数器（防止克隆攻击）
                .build();
        // 将凭证存储到仓库
        credentialRepository.addCredential(request.username(), credential);

        return new RegistrationFinishResponse(true, "注册成功");
    }

    /**
     * 开始认证流程 - 生成认证挑战（challenge）
     *
     * 流程说明：
     * 1. 生成断言请求（包含 challenge 和允许的凭证列表）
     * 2. 缓存断言请求，用于后续验证
     * 3. 支持两种模式：
     *    - 指定用户名：只允许该用户的凭证
     *    - 不指定用户名：允许任何已注册的凭证（可发现凭证）
     *
     * @param request 认证开始请求，可选用户名
     * @return PublicKeyCredentialRequestOptions 返回给前端的认证选项（需序列化为 JSON）
     */
    public PublicKeyCredentialRequestOptions startAuthentication(AuthenticationStartRequest request) {
        // 生成断言请求（认证挑战）
        AssertionRequest assertionRequest = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                        .username(Optional.ofNullable(request.username())) // 可选用户名
                        .userVerification(UserVerificationRequirement.REQUIRED) // 要求用户验证
                        .build());
        // 缓存断言请求，key 为用户名或 challenge
        if (request.username() != null && !request.username().isBlank()) {
            // 指定用户名时，使用用户名作为 key
            assertionRequests.put(request.username(), assertionRequest);
        } else {
            // 未指定用户名时（可发现凭证），使用 challenge 作为 key
            assertionRequests.put(assertionRequest.getPublicKeyCredentialRequestOptions().getChallenge().getBase64Url(), assertionRequest);
        }
        return assertionRequest.getPublicKeyCredentialRequestOptions();
    }

    /**
     * 完成认证流程 - 验证前端返回的断言并更新签名计数
     *
     * 流程说明：
     * 1. 从缓存中获取并移除之前生成的断言请求（包含 challenge）
     * 2. 验证前端返回的断言响应（签名、来源、challenge 等）
     * 3. 验证签名计数器（防止凭证被克隆）
     * 4. 更新存储的签名计数器
     *
     * @param request 认证完成请求，包含用户名和前端生成的断言
     * @return AuthenticationFinishResponse 认证结果，包含是否成功、用户名、签名计数
     * @throws AssertionFailedException 认证验证失败时抛出
     */
    public AuthenticationFinishResponse finishAuthentication(AuthenticationFinishRequest request) throws AssertionFailedException {
        // 获取之前缓存的断言请求（包含 challenge），用于验证响应
        AssertionRequest assertionRequest = Optional.ofNullable(assertionRequests.remove(request.username()))
                .orElseThrow(() -> new IllegalStateException("认证挑战已过期或不存在"));

        // 验证前端返回的断言（验证签名、来源、challenge、签名计数器等）
        AssertionResult result = relyingParty.finishAssertion(
                FinishAssertionOptions.builder()
                        .request(assertionRequest)
                        .response(request.credential())
                        .build());

        // 更新凭证的签名计数器（用于防止克隆攻击）
        credentialRepository.updateCredential(result.getUsername(), request.credential().getId(), result.getSignatureCount());

        return new AuthenticationFinishResponse(result.isSuccess(), result.getUsername(), result.getSignatureCount());
    }

    /**
     * 检查用户是否已注册过 WebAuthn 凭证
     *
     * 说明：
     * - 用于判断用户是否已经注册过设备
     * - 前端可以根据此结果决定显示"注册设备"还是"登录"按钮
     *
     * @param username 登录账号（LogonId）
     * @return 已注册返回 true，未注册返回 false
     */
    public boolean hasCredentials(String username) {
        return credentialRepository.hasCredentials(username);
    }

    /**
     * 获取用户的凭证数量
     *
     * @param username 登录账号（LogonId）
     * @return 凭证数量
     */
    public int getCredentialCount(String username) {
        return credentialRepository.getCredentialCount(username);
    }

    /**
     * 获取用户的所有凭证列表
     *
     * 说明：
     * - 用于设备管理界面展示用户已注册的所有设备
     * - 返回凭证 ID、注册时间、最后使用时间等信息
     *
     * @param username 登录账号（LogonId）
     * @return 凭证列表
     */
    public java.util.List<com.yubico.webauthn.RegisteredCredential> getCredentials(String username) {
        return credentialRepository.getCredentials(username);
    }

    /**
     * 删除指定凭证
     *
     * 说明：
     * - 用户可以删除不再使用的设备凭证
     * - 比如设备丢失、更换设备等情况
     *
     * @param username 登录账号（LogonId）
     * @param credentialId 凭证 ID（Base64 编码）
     * @return 是否删除成功
     */
    public boolean deleteCredential(String username, String credentialId) {
        return credentialRepository.deleteCredential(username, com.yubico.webauthn.data.ByteArray.fromBase64(credentialId));
    }

    /**
     * 删除用户的所有凭证
     *
     * 说明：
     * - 用于用户注销或重置 WebAuthn 设置
     * - 删除后用户需要重新注册设备
     *
     * @param username 登录账号（LogonId）
     * @return 删除的凭证数量
     */
    public int deleteAllCredentials(String username) {
        return credentialRepository.deleteAllCredentials(username);
    }
}
