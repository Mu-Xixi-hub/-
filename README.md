# 异步登录示例：独立前端 + Spring Boot 后端 + H2

这是一个本地学习项目：浏览器使用 fetch 异步请求，Spring Security 验证数据库中的账号密码，使用 Session 隔离不同用户。数据库使用 H2 文件模式，不需要安装 MySQL，首次启动自动创建表和两个测试账号。

## 1. 环境

- JDK 17 或 JDK 21（推荐你已有的 JDK 21）
- Maven 3.6.3+，也可使用 IDEA 自带 Maven
- Node.js 18+，只用于启动前端静态服务器，无需 npm install

## 2. 启动后端

压缩包已附带编译好的 JAR。只体验运行时，不需要安装 Maven，在项目根目录执行：

```bash
cd backend
java -jar dist/async-login-1.0.0.jar
```

下面的 Maven / IDEA 方式用于源码开发，任选一种启动方式即可，不要同时启动两个后端。

终端进入解压后的项目目录：

```bash
cd backend
mvn spring-boot:run
```

或者在 IDEA 中打开 `backend/pom.xml`，等待 Maven 下载依赖，选择 JDK 17/21，然后运行 `LoginApplication.main()`。IDEA 的 Working directory 请设为 `backend`，保证 H2 数据文件路径一致。

后端监听 `http://localhost:8080`。它只提供接口，直接打开根地址不是登录页面。

## 3. 启动前端

打开第二个终端，从项目根目录执行：

```bash
cd frontend
node server.mjs
```

浏览器打开 **http://localhost:5500**。前后端是两个独立进程。

也可以在 frontend 目录使用 `python -m http.server 5500 --bind 127.0.0.1`（Linux 可用 python3），代替 Node 静态服务器。

请通过 HTTP 打开页面，不要双击 index.html；统一使用 localhost，不要混用 127.0.0.1。前端请求地址在 `frontend/app.js`，允许的跨域来源在 `SecurityConfig.java`。

## 4. 测试账号

| 用户名 | 密码 | 昵称 | 部门 |
| --- | --- | --- | --- |
| zhangsan | Demo123! | 张三 | 研发部 |
| lisi | Demo123! | 李四 | 产品部 |

验证多人使用：

1. 用 Chrome 普通窗口登录 zhangsan。
2. 用 Edge 或 Chrome 无痕窗口登录 lisi。
3. 两个窗口分别显示张三、李四的信息，刷新后仍保持各自登录状态。
4. 退出张三，再刷新李四页面，李四仍然登录。

同一个浏览器配置下的普通标签页共享 Cookie，所以会共享同一个登录身份，不能用两个普通标签页模拟两个独立用户。多个无痕窗口也可能共享无痕会话。

## 5. 核心流程

1. 前端 GET `/api/csrf`，获取 CSRF Token。
2. 前端 POST `/api/login`，发送表单用户名、密码和 CSRF 请求头。
3. Spring Security 从 H2 查询账号，使用 BCrypt 校验密码。
4. 登录成功后，Spring Security 更换 Session ID，并在该 Session 中保存用户身份；浏览器接收 HttpOnly 的 JSESSIONID Cookie。
5. 前端 GET `/api/me`，通过 `credentials: 'include'` 自动携带 Cookie。
6. 后端从 Principal 取得当前登录用户名并查库，只返回用户 ID、用户名、昵称、邮箱和部门，不返回密码哈希。
7. POST `/api/logout` 使当前 Session 失效；其他用户的 Session 不受影响。

这里的“异步”是前端 fetch 不阻塞页面，不需要 Redis 队列或 `@Async`。后端可并发处理多个请求，但每个请求的身份来自其会话。没有把当前用户放进 Controller 成员变量或全局静态变量。

## 6. 接口

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| GET | /api/csrf | 获取 token、headerName；无需登录 |
| POST | /api/login | application/x-www-form-urlencoded：username、password；需要 CSRF |
| GET | /api/me | 当前用户资料；未登录返回 401 |
| POST | /api/logout | 注销当前会话；需要 CSRF |

登录成功返回 `{"message":"登录成功"}`；用户名或密码错误返回 401。CSRF 校验失败返回 403。

`/api/me` 示例：

```json
{"id":1,"username":"zhangsan","nickname":"张三","email":"zhangsan@example.com","department":"研发部"}
```

## 7. 代码定位

- `frontend/index.html`：登录表单与个人资料页面。
- `frontend/app.js`：异步登录、Cookie 携带、错误提示和退出。
- `frontend/server.mjs`：无第三方依赖的前端静态服务器。
- `backend/.../SecurityConfig.java`：账号验证、BCrypt、登录/退出、CSRF、CORS 和演示账号初始化。
- `backend/.../UserController.java`：查询当前登录用户，避免信任前端传入的用户 ID。
- `backend/.../UserRepository.java`：参数化 SQL 查询。
- `backend/src/main/resources/schema.sql`：建表语句。
- `backend/src/main/resources/application.yml`：端口、H2 和 Session 配置。
- `backend/src/test/.../LoginIsolationTest.java`：用户隔离、退出隔离、错误密码、CSRF、登录时会话 ID 变更测试。

## 8. H2 和会话

H2 文件保存在后端工作目录的 `data/login-db.mv.db`，重启后账号数据仍在。演示账号只在不存在时创建，不会覆盖已有记录。数据库默认用户名 sa，密码 local-demo-only，可用环境变量 H2_PASSWORD 覆盖；已有数据库修改密码需要另行迁移。H2 Web 控制台默认关闭。

Session 保存在后端进程内存中：30 分钟未活动过期，重启后需要重新登录。单个后端进程即可服务多个用户；多实例部署才需要进一步考虑 Redis Session 等共享会话方案。

## 9. 测试和打包

```bash
cd backend
mvn test
mvn package
java -jar target/async-login-1.0.0.jar
```

前端仍需单独启动。8080 / 5500 被占用时，先停止冲突程序，或同步调整后端端口、前端请求地址、静态服务器端口及 CORS 来源。

这是只绑定本机回环地址的教学示例，不包含注册、找回密码、登录限流或公网部署配置。公开部署前应去除演示账号并配置 HTTPS、Secure Cookie 和登录限流。

Spring Boot 官方环境说明：https://docs.spring.io/spring-boot/3.5/system-requirements.html

## 10. 本次验证

- Java 17 编译并成功打包可执行 JAR。
- 两项 Spring Boot 集成测试通过，覆盖会话隔离、退出隔离、错误密码、CSRF 和登录会话 ID 变更。
- 对启动后的 JAR 进行真实 HTTP 联调：两个客户端并发登录、真实 CSRF Token、CORS 响应、各自 Cookie、多次个人信息查询、退出隔离均通过。
- 前端 JavaScript 语法检查和静态服务器 HTTP 检查通过。未进行浏览器视觉验收。
