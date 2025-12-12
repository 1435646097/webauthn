# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 提供在此代码库中工作的指导。

## 项目概述

这是一个基于 Spring Boot 3 后端和 Angular 17 前端的 WebAuthn 无密码认证演示项目，专为 Windows 11 的 Windows Hello（PIN 码、指纹、人脸识别）认证设计。

## 构建和运行命令

### 后端 (Spring Boot)
```powershell
cd backend
mvn clean install          # 构建项目
mvn spring-boot:run        # 运行后端服务
mvn test                   # 运行测试
```

后端运行在 `http://localhost:8080`（如果配置了 SSL 则为 `https://localhost:8443`）

### 前端 (Angular)
```powershell
cd frontend
npm install                # 安装依赖
npm start                  # 运行开发服务器（已配置 SSL）
npm run build              # 生产环境构建
npm run lint               # 运行代码检查
npm test                   # 运行测试
```

前端运行在 `https://webauthn-demo.local:4211`（在 package.json 中配置）

## 架构设计

### 后端架构

后端采用分层架构，使用 MyBatis 进行数据库持久化：

**核心组件：**
- `WebAuthnService` - 注册和认证流程的核心业务逻辑。使用内存中的 ConcurrentHashMap 临时存储注册/认证过程中的挑战信息。
- `DatabaseCredentialRepository` - 实现 Yubico 的 `CredentialRepository` 接口。使用 MyBatis 映射器管理凭证持久化。为新用户生成 32 字节随机 userHandle。
- `RelyingParty` - Yubico WebAuthn 库组件，在 `WebAuthnConfig` 中配置。负责挑战生成和验证。

**数据流：**
1. 注册流程：Controller → Service（生成挑战）→ 存储到内存 → 客户端认证 → Service（验证响应）→ Repository（持久化凭证）
2. 认证流程：Controller → Service（生成挑战）→ 存储到内存 → 客户端签名 → Service（验证签名）→ Repository（更新签名计数器）

**数据库表结构：**
- `tbl_account` - 现有用户表（LogonId、UserName、UserId）
- `tbl_webauthn_credential` - WebAuthn 凭证表（UserHandle、CredentialId、PublicKeyCose、SignatureCount）
- 一个用户可以拥有多个凭证（多设备支持）
- 同一用户的所有凭证共享相同的 UserHandle

**关键实现细节：**
- UserHandle 是为每个用户生成一次的 32 字节随机值，在该用户的所有设备间共享
- CredentialId 唯一标识每个设备/认证器
- SignatureCount 防止凭证克隆攻击（每次使用必须递增）
- 挑战信息临时存储在 ConcurrentHashMap 中（生产环境应使用 Redis 并设置过期时间）

### 前端架构

Angular 17 单页应用，与 WebAuthn 浏览器 API 交互：

**核心组件：**
- `app.component.ts` - 主 UI 组件，处理注册和认证流程
- `webauthn.service.ts` - 封装后端 API 调用的服务
- `utils/credential.ts` - WebAuthn 数据的 Base64URL 和 ArrayBuffer 转换工具

**WebAuthn 流程：**
1. 用户输入用户名 → 调用 `/api/webauthn/register/start` 或 `/authenticate/start`
2. 从后端接收挑战 → 将 Base64URL 转换为 ArrayBuffer
3. 调用 `navigator.credentials.create()` 或 `get()` → Windows Hello 提示
4. 将响应 ArrayBuffer 转换为 Base64URL → 发送到 `/finish` 端点

## 配置说明

### 数据库配置

编辑 `backend/src/main/resources/application.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database
    username: your_username
    password: your_password
```

### WebAuthn 配置

编辑 `backend/src/main/resources/application.yml`：
```yaml
webauthn:
  rp-id: localhost                    # 必须与域名匹配
  rp-name: Your Application Name
  origins:
    - https://localhost:4200          # 允许的来源（除 localhost 外必须使用 HTTPS）
```

**重要提示：** WebAuthn 在生产环境中必须使用 HTTPS。只有 `localhost` 可以使用 HTTP。README 中包含本地开发生成自签名证书的详细说明。

### MyBatis 配置

- Mapper XML 文件：`backend/src/main/resources/mapper/*.xml`
- 实体类包：`com.example.webauthn.entity`
- 驼峰命名转换已禁用（`map-underscore-to-camel-case: false`）

## API 接口

### 注册相关
- `POST /api/webauthn/register/start` - 生成注册挑战
- `POST /api/webauthn/register/finish` - 验证并存储凭证

### 认证相关
- `POST /api/webauthn/authenticate/start` - 生成认证挑战
- `POST /api/webauthn/authenticate/finish` - 验证签名并更新计数器

### 设备管理
- `GET /api/webauthn/devices/check/{username}` - 检查用户是否已注册设备
- `GET /api/webauthn/devices/list/{username}` - 列出用户的所有设备
- `DELETE /api/webauthn/devices/{username}/{credentialId}` - 删除指定设备
- `DELETE /api/webauthn/devices/all/{username}` - 删除用户的所有设备

## 核心技术概念

### UserHandle vs UserId
- `UserId` - 数据库主键，来自 `tbl_account` 表（例如：100）
- `UserHandle` - WebAuthn 协议使用的 32 字节随机值（Base64 编码）
- UserHandle 为每个用户生成一次，在该用户的所有凭证间共享
- UserHandle 提供隐私保护（不向认证器暴露内部用户 ID）

### 签名计数器
- 每次认证后递增
- 存储在数据库中，每次认证时检查
- 如果计数器未递增，凭证可能被克隆（安全风险）
- 实现位于 `DatabaseCredentialRepository.updateSignatureCount()`

### 挑战存储
- 当前使用 `WebAuthnService` 中的内存 `ConcurrentHashMap`
- 生产环境应使用 Redis 并设置 TTL 以实现可扩展性和过期机制
- 挑战应在 5-10 分钟后过期

## 代码规范

- 所有核心后端代码都有详细的中文注释，解释 WebAuthn 概念
- 使用 Lombok 注解（`@Data`、`@Slf4j` 等）
- MyBatis XML 映射器使用显式结果映射（不使用自动映射）
- 前端使用 RxJS 可观察对象进行 HTTP 调用

## 迁移到其他项目

要将此 WebAuthn 实现集成到其他项目：

1. 将 `com.example.webauthn` 包复制到你的项目
2. 创建 `tbl_webauthn_credential` 表（SQL 在 README 中）
3. 修改 `WebAuthAccountMapper` 以匹配你的用户表结构
4. 更新 `application.yml` 中的数据库连接和 WebAuthn 配置
5. 调整 `rp-id` 和 `origins` 以匹配你的域名
6. 将控制器集成到你的认证系统中

代码设计为可移植的，每个步骤都有详细注释说明。
