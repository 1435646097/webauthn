package com.example.webauthn.controller;

import com.example.webauthn.model.CredentialInfoResponse;
import com.example.webauthn.service.WebAuthnService;
import com.yubico.webauthn.RegisteredCredential;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备管理 Controller - 提供 WebAuthn 设备管理 API
 * <p>
 * 功能：
 * 1. 检查用户是否已注册设备
 * 2. 查询用户的设备列表
 * 3. 删除指定设备
 * 4. 删除所有设备
 */
@RestController
@RequestMapping("/api/webauthn/devices")
@CrossOrigin(origins = "*")
public class DeviceManagementController {

    private final WebAuthnService webAuthnService;

    public DeviceManagementController(WebAuthnService webAuthnService) {
        this.webAuthnService = webAuthnService;
    }

    /**
     * 检查用户是否已注册过 WebAuthn 设备
     * <p>
     * 说明：
     * - 前端可以根据此接口决定显示"注册设备"还是"登录"按钮
     * - 返回是否已注册和设备数量
     *
     * @param username 登录账号（LogonId）
     * @return 检查结果
     */
    @GetMapping("/check/{username}")
    public ResponseEntity<Map<String, Object>> checkDeviceRegistered(@PathVariable String username) {
        boolean hasCredentials = webAuthnService.hasCredentials(username);
        int count = webAuthnService.getCredentialCount(username);

        Map<String, Object> response = new HashMap<>();
        response.put("hasCredentials", hasCredentials);
        response.put("count", count);
        response.put("message", hasCredentials ? "用户已注册 " + count + " 个设备" : "用户未注册任何设备");

        return ResponseEntity.ok(response);
    }

    /**
     * 获取用户的所有设备列表
     * <p>
     * 说明：
     * - 用于设备管理界面展示用户已注册的所有设备
     * - 返回凭证 ID、签名计数等信息
     *
     * @param username 登录账号（LogonId）
     * @return 设备列表
     */
    @GetMapping("/list/{username}")
    public ResponseEntity<List<CredentialInfoResponse>> listDevices(@PathVariable String username) {
        List<RegisteredCredential> credentials = webAuthnService.getCredentials(username);

        List<CredentialInfoResponse> response = credentials.stream()
                .map(cred -> new CredentialInfoResponse(
                        cred.getCredentialId().getBase64(),
                        cred.getSignatureCount()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * 删除指定设备
     * <p>
     * 说明：
     * - 用户可以删除不再使用的设备凭证
     * - 比如设备丢失、更换设备等情况
     *
     * @param username 登录账号（LogonId）
     * @param credentialId 凭证 ID（Base64 编码）
     * @return 删除结果
     */
    @DeleteMapping("/{username}/{credentialId}")
    public ResponseEntity<Map<String, Object>> deleteDevice(
            @PathVariable String username,
            @PathVariable String credentialId) {
        boolean success = webAuthnService.deleteCredential(username, credentialId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "设备删除成功" : "设备删除失败，设备不存在或已被删除");

        return ResponseEntity.ok(response);
    }

    /**
     * 删除用户的所有设备
     * <p>
     * 说明：
     * - 用于用户注销或重置 WebAuthn 设置
     * - 删除后用户需要重新注册设备
     *
     * @param username 登录账号（LogonId）
     * @return 删除结果
     */
    @DeleteMapping("/all/{username}")
    public ResponseEntity<Map<String, Object>> deleteAllDevices(@PathVariable String username) {
        int deletedCount = webAuthnService.deleteAllCredentials(username);

        Map<String, Object> response = new HashMap<>();
        response.put("success", deletedCount > 0);
        response.put("deletedCount", deletedCount);
        response.put("message", "成功删除 " + deletedCount + " 个设备");

        return ResponseEntity.ok(response);
    }
}
