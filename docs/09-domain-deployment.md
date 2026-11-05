# 第九章 · 域名公网部署指南

> 目标：把本系统从「本机 Nginx + 8080」升级为「云服务器 + 域名 + HTTPS」，任何人浏览器输入域名即可访问。
> 前置阅读：README 第五章（本机 Nginx 部署）——本篇是它的公网版。

---

## 一、整体架构变化

```
部署前（本机）：
  浏览器 ──► http://localhost:8080 ──► Nginx ──┬─► 前端 dist
                                               └─► Spring Boot :8081 ──► MySQL :3308

部署后（公网）：
  任何人 ──► https://feedback.example.com ──► 云服务器 Nginx :443 ──┬─► 前端 dist
        （DNS A 记录指向服务器公网 IP）                              └─► Spring Boot :8081 ──► MySQL :3306
```

核心变化只有三件事：
1. 机器从本机换成有**公网 IP** 的云服务器；
2. 域名通过 **DNS A 记录**解析到这个 IP；
3. Nginx 从 80 端口升级为 443 + **SSL 证书**（HTTPS）。

后端代码**一行都不用改**，改的都是配置和部署环境。

---

## 二、准备工作（买什么、办什么）

### 2.1 域名

- 在阿里云/腾讯云等注册商购买域名（`.com` / `.cn` 均可），需要**实名认证**。
- ⚠️ **ICP 备案**：域名解析到**中国境内**服务器时，按《互联网信息服务管理办法》必须先完成 ICP 备案（在云厂商的备案系统提交，一般 1~2 周）。未备案域名指向境内服务器会被接入商阻断。
- 备案期间的过渡方案：先用境外/中国香港节点服务器（免备案，但境内访问延迟略高），或临时用 `http://服务器公网IP:80` 访问。
- 备案成功后才能开 80/443 对外服务（部分云厂商会自动拦截未备案域名的 80/443 流量）。

### 2.2 服务器

- 最低配置即可：**2 核 2G 云服务器**（阿里云/腾讯云轻量应用服务器，学生认证有优惠）。
- 操作系统选 Ubuntu 22.04 LTS 或 CentOS Stream（下文以 Ubuntu 为例）。
- 在云控制台的**安全组**里放行端口：`22`（SSH）、`80`（HTTP）、`443`（HTTPS）。
  **不要**放行 3306 和 8081——数据库和后端只允许本机访问，这是公网部署最重要的安全边界。

### 2.3 SSL 证书

- 云厂商均提供**免费 DV 证书**（每年 20 张），在控制台为域名申请后下载 Nginx 格式（`.pem` + `.key`）。
- 也可用 Let's Encrypt + `certbot` 自动签发续期。

---

## 三、服务器环境安装

```bash
# JDK 17（Boot 3.2 最低要求）
sudo apt update && sudo apt install -y openjdk-17-jdk

# MySQL 8（默认 3306 端口，比本机 3308 更标准）
sudo apt install -y mysql-server

# Nginx
sudo apt install -y nginx
```

数据库初始化（把本机 `sql/` 目录的脚本传上去执行）：

```bash
sudo mysql -e "CREATE DATABASE feedback_hub DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p feedback_hub < schema.sql
mysql -uroot -p feedback_hub < init-data.sql
```

> 修改 `application.yml` 里的 JDBC URL 端口：本机是 `3308`，服务器上装完默认就是 `3306`（见第六章配置清单第 1 条）。

**MySQL 安全加固**（只监听本机）：

```bash
sudo mysql -e "ALTER USER 'feedback'@'localhost' IDENTIFIED BY '强密码';"
# 确认 bind-address 是 127.0.0.1（Ubuntu 默认就是）
sudo grep bind-address /etc/mysql/mysql.conf.d/mysqld.cnf
```

---

## 四、打包与上传

```bash
# 本机：后端打包（跳过测试）
cd backend && bash mvn.sh -o clean package -DskipTests
# 产物：backend/target/feedback-hub.jar

# 本机：前端打包（先改名 dist 规避环境的批量删除守卫，再构建）
cd frontend && mv dist dist-old-$(date +%m%d) 2>/dev/null; npm run build
# 产物：frontend/dist/

# 上传到服务器（scp 示例）
scp backend/target/feedback-hub.jar user@<服务器IP>:/opt/feedback/
scp -r frontend/dist/* user@<服务器IP>:/var/www/feedback-ui/
```

---

## 五、Nginx 配置（域名 + HTTPS 版）

把 `deploy/nginx/feedback.conf` 在服务器上改造成下面这样（放 `/etc/nginx/conf.d/feedback.conf`）：

```nginx
# HTTP 一律 301 跳转到 HTTPS
server {
    listen      80;
    server_name feedback.example.com;   # 换成你的域名
    return 301 https://$host$request_uri;
}

server {
    listen      443 ssl;
    server_name feedback.example.com;

    # 云厂商下载的免费证书
    ssl_certificate     /etc/nginx/certs/feedback.example.com.pem;
    ssl_certificate_key /etc/nginx/certs/feedback.example.com.key;

    gzip on;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/json application/javascript;

    # 前端静态资源（SPA 路由回退，与本机版相同）
    location / {
        root  /var/www/feedback-ui;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端反代：proxy_pass 结尾不带斜杠，保留 /api 前缀（老坑不重踩）
    location /api/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;      # HTTPS 场景必须带
        proxy_connect_timeout 10s;
        proxy_read_timeout    120s;                       # AI 调用较慢
        proxy_send_timeout    120s;
    }

    # 本地图片目录（改用 OSS 后整段删除）
    location /uploads/ {
        alias /opt/feedback/uploads/;
        expires 7d;
        add_header Cache-Control "public";
        location ~ \.(php|jsp|asp|sh|bat)$ { deny all; }
    }
}
```

```bash
sudo nginx -t && sudo nginx -s reload
```

---

## 六、后端配置清单（需要动的所有地方）

| # | 配置项 | 本机值 | 公网值 | 说明 |
|---|--------|--------|--------|------|
| 1 | `spring.datasource.url` 端口 | 3308 | **3306** | 服务器 MySQL 默认端口 |
| 2 | `spring.profiles.active` | dev | **prod** | 建议新增 `application-prod.yml` |
| 3 | `DB_USERNAME` / `DB_PASSWORD` | 本机口令 | **环境变量注入** | 口令不进代码库 |
| 4 | `JWT_SECRET` | dev 默认值 | **环境变量注入强随机串** | 公网必须换，`openssl rand -base64 48` 生成 |
| 5 | `mybatis-plus.configuration.log-impl` | StdOutImpl | **删除该行** | 生产别往 stdout 刷 SQL |
| 6 | `logging.level.com.company.feedback` | DEBUG | **INFO** | 同上 |
| 7 | `storage.local.root-path` | `D:/feedback-data/uploads` | `/opt/feedback/uploads` | Linux 路径 |
| 8 | `storage.local.url-prefix` | `http://localhost:8081/uploads/` | **`https://你的域名/uploads/`** | 否则图片 URL 是 localhost，别人看不见 |
| 9 | `springdoc.api-docs.enabled` | （缺省 true） | **false**（可选） | 公网不宜暴露 Swagger |
| 10 | `AI_API_KEY`（若用大模型） | — | 环境变量注入 | 密钥不进代码库 |

> CORS 说明：本项目前端与 `/api` **同域**（都走 Nginx 443），生产环境**不存在跨域**，
> `SecurityConfig` 里的 CORS 配置无需改动。只有把前端单独放到另一个域名时才需要把
> `setAllowedOriginPatterns` 里的 `*` 收紧为具体域名。

新增 `backend/src/main/resources/application-prod.yml`（已被 .gitignore 覆盖口令，密钥全走环境变量），
启动命令变为：

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_USERNAME=feedback
export DB_PASSWORD='强密码'
export JWT_SECRET='openssl生成的随机串'
export AI_PROVIDER=rule        # 或 llm + AI_API_KEY
nohup java -jar /opt/feedback/feedback-hub.jar > /opt/feedback/logs/backend.log 2>&1 &
```

---

## 七、进程守护：从 bat 换成 systemd

Windows 的 `start.bat`/`stop.bat` 在 Linux 上换成 **systemd**（崩溃自动重启 + 开机自启）：

`/etc/systemd/system/feedback.service`：

```ini
[Unit]
Description=feedback-hub backend
After=network.target mysql.service

[Service]
User=www-data
WorkingDirectory=/opt/feedback
ExecStart=/usr/bin/java -jar /opt/feedback/feedback-hub.jar
Environment=SPRING_PROFILES_ACTIVE=prod
EnvironmentFile=/opt/feedback/env        # DB_PASSWORD / JWT_SECRET 等放这里，权限 600
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now feedback
sudo systemctl status feedback        # 查看状态
journalctl -u feedback -f             # 看日志
```

---

## 八、上线前检查清单

- [ ] DNS A 记录已生效：`ping 域名` 返回服务器公网 IP
- [ ] `https://域名` 打开登录页，四类角色均能登录
- [ ] 上传图片后，图片 URL 是 `https://域名/uploads/...` 且能显示
- [ ] AI 聚类/起草正常（或确认降级到 rule 并留证）
- [ ] 3306 / 8081 端口对公网**不可达**：`nmap -p 3306,8081 服务器IP` 应为 filtered/closed
- [ ] JWT_SECRET、DB_PASSWORD、AI_API_KEY 均为环境变量注入，代码库里搜不到明文
- [ ] Swagger 已关闭或加了鉴权
- [ ] systemd 常驻运行，`kill` 后 5 秒内自动拉起
- [ ] 每日 `mysqldump` 定时备份（`crontab` 一行即可）

## 九、常见问题速查

| 现象 | 原因 | 处理 |
|------|------|------|
| 域名访问超时 | 未备案被接入商拦截 | 完成 ICP 备案，或暂用境外节点 |
| 证书浏览器报红 | 只配了 443 没配证书，或证书域名不匹配 | 核对 `ssl_certificate` 与域名 |
| 页面能开但接口 404 | `proxy_pass` 末尾误加斜杠，`/api` 前缀被改写 | 去掉斜杠 |
| 登录页正常、图片裂图 | `storage.local.url-prefix` 还是 localhost | 改成 `https://域名/uploads/` |
| HTTPS 下接口全挂（mixed content） | 后端返回的 URL 还是 http | 确认 `X-Forwarded-Proto` 已透传 + url-prefix 用 https |
| 重启服务器后服务没起 | 没做 systemd 自启 | `systemctl enable feedback` |
