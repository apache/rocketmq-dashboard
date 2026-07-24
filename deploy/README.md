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

需要 Docker Compose **2.35 或更高版本**（本文流程已在 2.40.2 验证）；
`deploy/docker-compose.yml` 使用了命名卷的 `volume.subpath` 精确文件挂载。
基础 Compose 不包含宿主机凭据路径。

还需要 Python 3。`deploy.sh snapshot-registry` 会从受信任目录链逐级以
no-follow 方式打开目录，再从持有的父目录 descriptor 打开注册表；它通过
`fstat` 检查普通文件、当前 UID 所有权和权限，并从持续持有的 descriptor
复制到 `0600` 快照。因此构建期间替换原路径或 symlink 不会改变后续输入。

以下流程把快照放进权限为 `0700` 的临时目录，且目标文件在命令执行前必须
不存在。随后临时 server-image helper 从快照读取标准输入，在私有命名卷内
设置 `0600`，并通过同目录 rename 原子安装：

```bash
cd deploy
registry_snapshot_dir=$(mktemp -d "${TMPDIR:-/tmp}/rocketmq-studio-registry.XXXXXX")
chmod 700 "$registry_snapshot_dir"
trap 'rm -rf -- "$registry_snapshot_dir"' EXIT HUP INT TERM
registry_snapshot="$registry_snapshot_dir/studio-users.json"
./deploy.sh snapshot-registry \
  "$STUDIO_SECURITY_USER_FILE_LOCAL" \
  "$registry_snapshot"

docker compose build rocketmq-server rocketmq-web
docker volume create rocketmq-studio_studio-users >/dev/null
docker run --rm -i \
  --entrypoint sh \
  -v rocketmq-studio_studio-users:/run/secrets \
  rocketmq-server:latest -c '
    set -eu
    umask 077
    destination=/run/secrets/studio-users.json
    temporary=$(mktemp /run/secrets/.studio-users.json.tmp.XXXXXX)
    trap '\''rm -f -- "$temporary"'\'' EXIT HUP INT TERM
    cat >"$temporary"
    chmod 600 "$temporary"
    mv -f -- "$temporary" "$destination"
    trap - EXIT HUP INT TERM
  ' <"$registry_snapshot"

docker compose up -d

rm -- "$registry_snapshot"
rmdir -- "$registry_snapshot_dir"
trap - EXIT HUP INT TERM
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

本机需要 Docker、Python 3、SSH 和 SCP；远程主机需要 Podman 与 curl，并
配置免密 SSH。复制模板并替换所有占位符：

```bash
cp deploy/.env.example deploy/.env
```

`server` 和 `all` 部署必须设置
`STUDIO_SECURITY_USER_FILE_LOCAL=/absolute/path/to/studio-users.json`。脚本会在
构建或联网前从 no-follow descriptor 创建不可变 `0600` 快照，并拒绝
symlink、非普通文件、错误所有者、任何 group/other 文件权限，以及所有者不是
root/当前 UID 或 group/world 可写的目录祖先。随后它会：

1. 为远程账户获取全局独占锁，串行保护固定容器名、网络和凭据卷；
2. 使用每次运行独有的镜像 tag、远程 tarball 和注册表暂存名；
3. 本地构建并传输镜像，以 `0600` 暂存快照且不打印内容；
4. 用该次运行刚加载的 server image 和卷内 `mktemp` 将文件原子写入私有卷；
5. 只读挂载 `/run/secrets` 并设置
   `STUDIO_SECURITY_USER_FILE=/run/secrets/studio-users.json`；
6. 不发布后端端口，只把 Web 绑定到远程回环地址；
7. 确认预期容器正在运行，并从 `rocketmq-server` 容器内限时轮询
   `http://127.0.0.1:8888/actuator/health/readiness`，直到 HTTP 成功且顶层
   `status` 为 `UP`；`web`/`all` 随后还验证静态首页；以及
8. 在成功和错误路径只删除本次运行的本地/远程暂存文件、本地一次性镜像 tag
   和所持锁；成功替换容器后尽力移除旧远程镜像，启动前失败则移除本次加载但
   未被容器使用的精确 tag，不强制删除仍被任何容器引用的镜像。

运行：

```bash
./deploy/deploy.sh all
./deploy/deploy.sh server
./deploy/deploy.sh web
```

同一远程账户即使使用不同 `REMOTE_PATH` 也共享
`~/.rocketmq-studio-deploy.lock`，因为 rootless Podman 的固定容器名和卷属于
账户级资源。锁冲突时不要删除锁或重试并发部署。只有在与其他操作者/自动化
确认没有部署仍在运行后，才可登录同一账户，读取 `owner` 中的非敏感运行 ID，
再精确移除 owner 文件并以 `rmdir` 删除空锁目录：

```bash
lock="$HOME/.rocketmq-studio-deploy.lock"
test -d "$lock"
if test -f "$lock/owner"; then cat "$lock/owner"; else echo "owner missing"; fi
# 先在部署系统外确认该运行已终止；不要仅按锁的年龄判断。
if test -f "$lock/owner"; then rm -- "$lock/owner"; fi
rmdir -- "$lock"
```

不要使用递归删除，也不要在 owner 对应的部署仍可能运行时恢复。脚本会从远程
主机内部验证 readiness 和回环入口。操作者从工作站建立隧道：

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

该配置同时提供静态页面，所以 TLS 终止器必须包含已构建的
`/usr/share/nginx/html` 静态资源。可直接复用 `rocketmq-web:latest` 镜像；
它包含与当前版本匹配的 Web assets。终止器还必须加入 Compose 私有网络
`rocketmq-studio_default`，这样 `rocketmq-server:8888` 只能通过私网解析，
无需发布后端端口。

替换配置中的 `studio.example.com`，准备真实证书后，可先测试配置，再启动
独立终止器（以下命令在 `deploy/` 目录运行）：

```bash
docker run --rm \
  --network rocketmq-studio_default \
  -v "$PWD/nginx/rocketmq-studio-tls.conf.example:/etc/nginx/conf.d/default.conf:ro" \
  -v /absolute/path/to/tls.crt:/etc/nginx/tls/tls.crt:ro \
  -v /absolute/path/to/tls.key:/etc/nginx/tls/tls.key:ro \
  --entrypoint nginx \
  rocketmq-web:latest -t

docker run -d \
  --name rocketmq-studio-tls \
  --restart unless-stopped \
  --network rocketmq-studio_default \
  -p 80:80 \
  -p 443:443 \
  -v "$PWD/nginx/rocketmq-studio-tls.conf.example:/etc/nginx/conf.d/default.conf:ro" \
  -v /absolute/path/to/tls.crt:/etc/nginx/tls/tls.crt:ro \
  -v /absolute/path/to/tls.key:/etc/nginx/tls/tls.key:ro \
  --entrypoint nginx \
  rocketmq-web:latest -g 'daemon off;'
```

只允许公网到达终止器的 80/443（80 仅重定向），不要公开 6789 或 8888。
确认终止器覆盖可信 forwarding headers，外部客户端不能加入私有网络或绕过
终止器直接访问 Studio。若使用不同 Compose project name，请把示例网络名
替换为 `docker compose config` 输出的同一私有网络。
