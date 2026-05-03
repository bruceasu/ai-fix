# psql-go

轻量级 `psql` CLI 替代工具，用于 ECS Fargate 等无法安装完整 PostgreSQL 客户端的容器环境。

使用 Go 编写，编译为单一静态二进制文件（~7 MB），零运行时依赖。

## 功能

- 执行 SQL 命令（`-c` / `-f`）
- Tuples-only 输出（`-t`，无表头）
- 兼容 psql 常用参数子集
- 支持 `PGPASSFILE` / `~/.pgpass` 密码自动查找
- 支持 SSL 连接（默认 `sslmode=require`）
- 跨平台：Windows / Linux

## 支持的参数

| 参数 | 说明 |
|---|---|
| `-c "SQL"` | 执行 SQL 命令 |
| `-f <file>` | 从文件读取 SQL（`-` 表示 stdin） |
| `-t` | Tuples-only 输出（无表头/表尾） |
| `-q` | 静默模式（兼容，无操作） |
| `-v KEY=VAL` | 设置变量（兼容，始终启用 ON_ERROR_STOP） |
| `--no-psqlrc` | 跳过 psqlrc（兼容，无操作） |
| `-h, --host` | 数据库主机（默认 `PGHOST` 或 `localhost`） |
| `-p, --port` | 数据库端口（默认 `PGPORT` 或 `5432`） |
| `-d, --dbname` | 数据库名（默认 `PGDATABASE`） |
| `-U, --username` | 用户名（默认 `PGUSER`） |
| `--help, -?` | 显示帮助 |

## 环境变量

| 变量 | 说明 |
|---|---|
| `PGHOST` | 数据库主机 |
| `PGPORT` | 数据库端口 |
| `PGDATABASE` | 数据库名 |
| `PGUSER` | 数据库用户 |
| `PGPASSWORD` | 数据库密码 |
| `PGPASSFILE` | pgpass.conf 文件路径（默认 `~/.pgpass`） |
| `PGSSLMODE` | SSL 模式（默认 `require`） |

## 密码查找顺序

1. `PGPASSWORD` 环境变量
2. `PGPASSFILE` 指定的文件 → `~/.pgpass`（Linux）/ `%APPDATA%\postgresql\pgpass.conf`（Windows）

## 使用示例

```bash
# 通过环境变量连接
export PGHOST=db.example.com PGDATABASE=statistics PGUSER=reader PGPASSWORD=secret
psql-go -c "SELECT COUNT(*) FROM risk_manage.eod_position_record" -t

# 从文件执行 SQL
psql-go -f query.sql

# 完整命令行参数
psql-go -h db.example.com -p 5432 -d statistics -U reader -c "SELECT 1" -t

# 从 stdin 读取 SQL
echo "SELECT NOW()" | psql-go -f -

# 配合 pgpass.conf（无需密码参数）
export PGPASSFILE=/app/pgpass.conf
psql-go -h db.example.com -d statistics -U reader -c "SELECT 1" -t
```

## 构建

```bash
# Windows
build.bat

# PowerShell
.\build.ps1
```

编译产物在 `bin/` 目录：
- `bin/psql-go.exe` — Windows amd64
- `bin/psql-go` — Linux amd64

## ECS 部署

将 `bin/psql-go`（Linux 版本）放入容器镜像或挂载到任务定义中，配合环境变量或 `pgpass.conf` 使用。
