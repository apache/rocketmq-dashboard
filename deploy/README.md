# 安全部署

Studio 不提供默认账户。首次启动前必须由操作者创建严格的 JSON 用户注册表；
缺失、空卷、权限不安全或格式错误都会失败关闭。完整安全约束见
[`../docs/security.md`](../docs/security.md)。

## 用户注册表

注册表使用 `v1` schema、`USER`/`ADMIN` 角色以及带 `{bcrypt}` 前缀的
cost-12 bcrypt hash。先在仓库外创建文件并限制权限：

```bash
export STUDIO_SECURITY_USER_FILE_LOCAL=/absolute/path/to/studio-users.json
chmod 600 "$STUDIO_SECURITY_USER_FILE_LOCAL"
```

不要把密码、hash 或注册表提交到 Git。示例字段和安全生成 hash 的方法见
[`../docs/security.md`](../docs/security.md)。

## 本机 Docker Compose

基础 Compose 不包含宿主机凭据路径。以下临时 server-image helper 从操作者
选择的文件读取标准输入，在私有命名卷内设置 `0600`，并通过同目录 rename
原子安装：

```bash
cd deploy
test -f "$STUDIO_SECURITY_USER_FILE_LOCAL"
test ! -L "$STUDIO_SECURITY_USER_FILE_LOCAL"

docker compose build rocketmq-server rocketmq-web
docker volume create rocketmq-studio_studio-users >/dev/null
docker run --rm -i \
  --entrypoint sh \
  -v rocketmq-studio_studio-users:/run/secrets \
  rocketmq-server:latest -c '
    set -eu
    umask 077
    destination=/run/secrets/studio-users.json
    temporary=/run/secrets/.studio-users.json.tmp.$$
    trap '\''rm -f -- "$temporary"'\'' EXIT HUP INT TERM
    cat >"$temporary"
    chmod 600 "$temporary"
    mv -f -- "$temporary" "$destination"
    trap - EXIT HUP INT TERM
  ' <"$STUDIO_SECURITY_USER_FILE_LOCAL"

docker compose up -d
```

Compose 将命名卷中的 `studio-users.json` 作为精确文件只读挂载到
`/run/secrets/studio-users.json`。后端仅暴露在 Compose 私有网络，Web 仅绑定
`127.0.0.1:6789`。启动后访问：

```text
http://127.0.0.1:6789
```

更新用户时，重新运行同一个临时 helper；不要在卷中原地编辑文件。停止服务可
使用 `docker compose down`。除非明确要删除所有 Studio 登录配置，否则不要
删除 `rocketmq-studio_studio-users` 卷。

## 远程 Podman 部署

本机需要 Docker、SSH 和 SCP；远程主机需要 Podman 与 curl，并配置免密 SSH。
复制模板并替换所有占位符：

```bash
cp deploy/.env.example deploy/.env
```

`server` 和 `all` 部署必须设置
`STUDIO_SECURITY_USER_FILE_LOCAL=/absolute/path/to/studio-users.json`。脚本会在
联网前解析精确文件，拒绝 symlink、非普通文件、错误所有者以及任何 group/other
权限。随后它会：

1. 本地构建并传输镜像；
2. 以 `0600` 暂存注册表且不打印内容；
3. 用刚加载的 server image 临时容器将文件原子写入私有 Podman 命名卷；
4. 只读挂载 `/run/secrets` 并设置
   `STUDIO_SECURITY_USER_FILE=/run/secrets/studio-users.json`；
5. 不发布后端端口，只把 Web 绑定到远程回环地址；以及
6. 在成功和错误路径都删除本地与远程暂存文件。

运行：

```bash
./deploy/deploy.sh all
./deploy/deploy.sh server
./deploy/deploy.sh web
```

脚本会从远程主机内部验证回环入口。操作者从工作站建立隧道：

```bash
ssh -L 6789:127.0.0.1:6789 deploy-user@studio.example.com
```

保持隧道运行，然后在工作站打开 `http://127.0.0.1:6789`。不要把远程回环
HTTP 端口改绑到公共接口。

## HTTPS

需要网络访问时，以
[`nginx/rocketmq-studio-tls.conf.example`](nginx/rocketmq-studio-tls.conf.example)
为模板部署受信任 TLS 终止器。证书固定挂载为
`/etc/nginx/tls/tls.crt`，私钥固定挂载为 `/etc/nginx/tls/tls.key`。模板的
80 端口只重定向，HSTS 只由 443 响应发送，访问日志不包含 query 或
`Authorization`，上游仍位于私有容器网络。

上线前替换示例域名，使用真实证书运行 `nginx -t`，并确认终止器覆盖可信
forwarding headers，外部客户端无法绕过终止器直接访问 Studio。
