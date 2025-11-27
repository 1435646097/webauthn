# Windows Hello WebAuthn 演示项目

这是一个基于 Spring Boot 3 后端和 Angular 17 前端的 WebAuthn 无密码认证演示项目，支持 Windows 11 的 Windows Hello（PIN 码、指纹、人脸识别）认证。

## 📁 项目结构

```
backend/   Spring Boot 3 WebAuthn REST API（后端服务）
frontend/  Angular 17 客户端应用（前端界面）
```

## ✨ 主要特性

- ✅ **无密码认证**：使用 Windows Hello 进行生物识别或 PIN 码认证
- ✅ **多设备支持**：一个用户可以注册多个设备（笔记本、台式机、手机等）
- ✅ **数据库持久化**：基于 MyBatis + MySQL 的凭证存储
- ✅ **设备管理**：查看、删除已注册的设备
- ✅ **安全性**：签名计数器防克隆攻击、挑战-响应机制
- ✅ **详细注释**：所有核心代码都有中文注释，方便理解和迁移

## 🔧 环境要求

### 必需条件
1. **操作系统**：Windows 11（已配置 Windows Hello）
2. **后端**：
   - Java 17+
   - Maven 3.9+
   - MySQL 5.7+ 或 8.0+
3. **前端**：
   - Node.js 18+（自带 npm）

### Windows Hello 配置
- 必须在 Windows 11 中配置 Windows Hello
- 支持的认证方式：
  - 🔢 PIN 码
  - 👆 指纹识别
  - 👤 人脸识别

## 🗄️ 数据库配置

### 1. 创建数据库表

假设你已有用户表 `tbl_account`，需要创建 WebAuthn 凭证表：

```sql
CREATE TABLE `tbl_webauthn_credential` (
  `Id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `UserId` int NOT NULL COMMENT '关联 tbl_account.UserId',
  `UserHandle` varchar(64) NOT NULL COMMENT '用户句柄（Base64 编码的 32 字节随机值）',
  `CredentialId` varchar(512) NOT NULL COMMENT '凭证 ID（Base64 编码）',
  `PublicKeyCose` text NOT NULL COMMENT '公钥（COSE 格式，Base64 编码）',
  `SignatureCount` bigint NOT NULL DEFAULT '0' COMMENT '签名计数器（防止克隆攻击）',
  `CreateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `LastUsedTime` datetime DEFAULT NULL COMMENT '最后使用时间',
  PRIMARY KEY (`Id`),
  UNIQUE KEY `uk_credential_id` (`CredentialId`(255)),
  KEY `idx_user_id` (`UserId`),
  CONSTRAINT `fk_webauthn_credential_account` FOREIGN KEY (`UserId`) REFERENCES `tbl_account` (`UserId`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WebAuthn 凭证表';
```

### 2. 配置数据库连接

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database_name?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

## 🚀 运行项目

### 启动后端服务

```powershell
cd backend
mvn spring-boot:run
```

后端服务运行在 `http://localhost:8080`

### 启动前端应用

```powershell
cd frontend
npm install
npm start
```

前端应用运行在 `http://localhost:4200`，通过 `proxy.conf.json` 代理 `/api` 请求到后端。

## 📖 WebAuthn 工作流程

### 1. 注册流程（添加新设备）

```
用户输入用户名
    ↓
前端调用 /api/webauthn/register/start
    ↓
后端生成注册挑战（challenge）
    ↓
前端调用 navigator.credentials.create()
    ↓
Windows Hello 弹出提示（PIN/指纹/人脸）
    ↓
用户完成验证，生成凭证
    ↓
前端调用 /api/webauthn/register/finish
    ↓
后端验证并存储凭证到数据库
    ↓
注册成功 ✅
```

### 2. 认证流程（使用已注册设备登录）

```
用户输入用户名
    ↓
前端调用 /api/webauthn/authenticate/start
    ↓
后端生成认证挑战（challenge）
    ↓
前端调用 navigator.credentials.get()
    ↓
Windows Hello 弹出提示（PIN/指纹/人脸）
    ↓
用户完成验证，生成签名
    ↓
前端调用 /api/webauthn/authenticate/finish
    ↓
后端验证签名并更新签名计数器
    ↓
认证成功 ✅
```

## 🔌 API 接口

### 注册相关
- `POST /api/webauthn/register/start` - 开始注册流程
- `POST /api/webauthn/register/finish` - 完成注册流程

### 认证相关
- `POST /api/webauthn/authenticate/start` - 开始认证流程
- `POST /api/webauthn/authenticate/finish` - 完成认证流程

### 设备管理
- `GET /api/webauthn/devices/check/{username}` - 检查用户是否已注册设备
- `GET /api/webauthn/devices/list/{username}` - 获取用户的所有设备列表
- `DELETE /api/webauthn/devices/{username}/{credentialId}` - 删除指定设备
- `DELETE /api/webauthn/devices/all/{username}` - 删除用户的所有设备

## 📂 核心文件说明

### 后端核心文件

| 文件路径 | 说明 |
|---------|------|
| `service/WebAuthnService.java` | WebAuthn 业务逻辑，处理注册和认证流程 |
| `repository/DatabaseCredentialRepository.java` | 数据库凭证仓库，实现凭证的增删改查 |
| `repository/InMemoryCredentialRepository.java` | 内存凭证仓库（已添加详细注释，可作为参考） |
| `mapper/WebAuthnCredentialMapper.java` | MyBatis Mapper 接口 |
| `mapper/AccountMapper.java` | 用户账户查询 Mapper |
| `entity/WebAuthnCredentialEntity.java` | 凭证实体类 |
| `controller/WebAuthnController.java` | WebAuthn REST API 控制器 |
| `controller/DeviceManagementController.java` | 设备管理 REST API 控制器 |
| `config/WebAuthnConfig.java` | WebAuthn 配置类 |

### 前端核心文件

| 文件路径 | 说明 |
|---------|------|
| `app.component.ts` | Angular 主组件，连接 WebAuthn API |
| `utils/credential.ts` | Base64URL 和 ArrayBuffer 转换工具 |

## 🔐 安全特性

1. **挑战-响应机制**：每次认证都使用随机生成的 challenge，防止重放攻击
2. **签名计数器**：检测凭证克隆，如果计数器没有递增则拒绝认证
3. **用户验证**：要求用户进行生物识别或 PIN 验证（`userVerification=required`）
4. **平台认证器**：只使用设备内置的认证器（`authenticatorAttachment=platform`）
5. **来源验证**：验证请求来源，防止钓鱼攻击

## 💡 使用场景

### 场景 1：用户首次使用
1. 用户在笔记本上访问系统
2. 系统检测到用户未注册设备
3. 用户点击"注册设备"按钮
4. 使用 Windows Hello 完成注册
5. 下次访问时可以直接登录

### 场景 2：用户添加新设备
1. 用户在台式机上访问系统
2. 系统检测到用户已在其他设备注册
3. 用户点击"添加新设备"按钮
4. 使用 Windows Hello 完成注册
5. 现在用户有 2 个设备可以登录

### 场景 3：设备管理
1. 用户进入设备管理页面
2. 查看已注册的所有设备
3. 删除丢失或不再使用的设备

## 🎯 数据库设计说明

### 字段映射关系

| WebAuthn 概念 | 数据库字段 | 说明 |
|--------------|-----------|------|
| username | `tbl_account.LogonId` | 登录账号 |
| displayName | `tbl_account.UserName` | 用户姓名 |
| userId | `tbl_account.UserId` | 用户主键 |
| userHandle | `tbl_webauthn_credential.UserHandle` | 32 字节随机值（Base64） |
| credentialId | `tbl_webauthn_credential.CredentialId` | 凭证 ID（Base64） |
| publicKey | `tbl_webauthn_credential.PublicKeyCose` | 公钥（COSE 格式） |
| signatureCount | `tbl_webauthn_credential.SignatureCount` | 签名计数器 |

### 数据示例

```sql
-- 用户 zhangsan 注册了 2 个设备
SELECT * FROM tbl_webauthn_credential WHERE UserId = 100;

+----+--------+-------------+--------------+----------------+---------------------+
| Id | UserId | UserHandle  | CredentialId | SignatureCount | CreateTime          |
+----+--------+-------------+--------------+----------------+---------------------+
| 1  | 100    | ABC123...   | CRED_001     | 15             | 2025-01-15 10:00:00 |
| 2  | 100    | ABC123...   | CRED_002     | 8              | 2025-01-18 09:00:00 |
+----+--------+-------------+--------------+----------------+---------------------+
```

**注意**：
- 同一用户的所有凭证，`UserHandle` 相同
- 每个设备有不同的 `CredentialId`
- `SignatureCount` 在每次认证后递增

## 🚀 生产环境部署建议

### 1. 安全加固
- ✅ 已实现数据库持久化（替代内存存储）
- ⚠️ 添加用户会话管理（JWT 或 Session）
- ⚠️ 实现 CSRF 防护
- ⚠️ 添加请求频率限制
- ⚠️ 启用 HTTPS（生产环境必需，只有 localhost 可以使用 HTTP）

### 2. 配置调整
- 修改 `application.yml` 中的 `webauthn.origins` 为实际域名
- 配置生产环境数据库连接
- 关闭 SQL 日志输出（`mybatis.configuration.log-impl`）

### 3. 性能优化
- 配置数据库连接池参数
- 添加 Redis 缓存挑战信息
- 实现挑战过期机制

### 4. 监控和日志
- 添加认证成功/失败日志
- 监控签名计数器异常（可能的克隆攻击）
- 记录设备注册和删除操作

## 🔄 迁移到其他项目

本项目的代码已经添加了详细的中文注释，方便迁移到其他项目：

### 迁移步骤
1. 复制 `backend/src/main/java/com/example/webauthn` 包到你的项目
2. 创建 `tbl_webauthn_credential` 表
3. 修改 `AccountMapper` 以适配你的用户表结构
4. 配置数据库连接
5. 调整 `WebAuthnConfig` 中的 RP ID 和 Origins
6. 集成到你的认证系统中

### 关键配置
```yaml
webauthn:
  rp-id: your-domain.com          # 你的域名
  rp-name: Your Application Name  # 你的应用名称
  origins:
    - https://your-domain.com     # 允许的来源
```

## 📚 技术栈

### 后端
- Spring Boot 3.3.3
- Java 17
- MyBatis 3.0.3
- MySQL 8.0
- Yubico WebAuthn Server 2.7.0

### 前端
- Angular 17
- TypeScript
- WebAuthn API

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

## 🙏 致谢

- [Yubico WebAuthn Server](https://github.com/Yubico/java-webauthn-server) - WebAuthn 服务端库
- [WebAuthn Guide](https://webauthn.guide/) - WebAuthn 协议指南

---

**注意**：本项目仅用于学习和演示目的，生产环境使用前请进行充分的安全评估和测试。
