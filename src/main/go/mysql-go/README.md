# mysql-go

轻量级 `mysql` CLI 替代工具，用于 ECS Fargate 等无法安装完整 MySQL 客户端的容器环境。

使用 Go 编写，编译为单一静态二进制文件（~5 MB），零运行时依赖。

## 功能

- 执行 SQL 命令（`-e` / `-c` / `-f`）
- Tuples-only 输出（`-t` / `-N`，无表头）
- 自动区分 SELECT/SHOW（Query）与 DML/DDL（Exec）
- 兼容 mysql 常用参数 + psql-go 兼容别名（`-c`）
- 支持 `~/.my.cnf` [client] section 密码读取
- 跨平台：Windows / Linux

## 支持的参数

| 参数 | 说明 |
|---|---|
| `-e "SQL"` | 执行 SQL 命令 |
| `-c "SQL"` | 执行 SQL 命令（psql-go 兼容别名） |
| `-f <file>` | 从文件读取 SQL（`-` 表示 stdin） |
| `-t` | Tuples-only 输出（无表头） |
| `-N` | 跳过列名（`-t` 别名） |
| `-B, --batch` | 批处理模式（兼容，始终启用） |
| `-q` | 静默模式（兼容，无操作） |
| `-h, --host` | 数据库主机（默认 `MYSQL_HOST` 或 `localhost`） |
| `-P, --port` | 数据库端口（默认 `MYSQL_TCP_PORT` 或 `3306`） |
| `-D, --database` | 数据库名（默认 `MYSQL_DATABASE`） |
| `-u, --user` | 用户名（默认 `MYSQL_USER` 或 `USER`） |
| `-p[PASSWORD]` | 数据库密码（推荐用 `MYSQL_PWD` 环境变量） |
| `--help, -?` | 显示帮助 |

## 环境变量

| 变量 | 说明 |
|---|---|
| `MYSQL_HOST` | 数据库主机 |
| `MYSQL_TCP_PORT` | 数据库端口 |
| `MYSQL_DATABASE` | 数据库名 |
| `MYSQL_USER` | 数据库用户 |
| `MYSQL_PWD` | 数据库密码 |
| `MYSQL_SSL_MODE` | TLS 模式（默认 `false`；可设 `true` 或 `skip-verify`） |
| `MYSQL_DEFAULTS_FILE` | .my.cnf 文件路径（默认 `~/.my.cnf`） |

## 密码查找顺序

1. `MYSQL_PWD` 环境变量
2. `MYSQL_DEFAULTS_FILE` 指定的文件 → `~/.my.cnf`（Linux）/ `%APPDATA%\MySQL\.my.cnf`（Windows）

`.my.cnf` 格式示例（参见 `.my.cnf.example`）：
```ini
[client]
host=localhost
port=3306
user=root
password=secret
```

## 与 psql-go 对照

| 功能 | psql-go | mysql-go |
|---|---|---|
| 执行 SQL | `-c "SQL"` | `-e "SQL"` / `-c "SQL"` |
| 从文件读取 | `-f <file>` | `-f <file>` |
| 仅输出数据 | `-t` | `-t` / `-N` |
| 主机 | `-h` / `PGHOST` | `-h` / `MYSQL_HOST` |
| 端口 | `-p` / `PGPORT` | `-P` / `MYSQL_TCP_PORT` |
| 数据库 | `-d` / `PGDATABASE` | `-D` / `MYSQL_DATABASE` |
| 用户 | `-U` / `PGUSER` | `-u` / `MYSQL_USER` |
| 密码 | `PGPASSWORD` / pgpass.conf | `MYSQL_PWD` / `.my.cnf` |
| SSL | `PGSSLMODE` | `MYSQL_SSL_MODE` |
| SELECT vs DML | 统一 Query | 自动区分 Query/Exec |

## 使用示例

```bash
# 通过环境变量连接（ECS 推荐）
export MYSQL_HOST=mt4-db.example.com MYSQL_DATABASE=mt4_report
export MYSQL_USER=reader MYSQL_PWD=secret
mysql-go -e "SELECT COUNT(*) FROM TX_EOD_OPEN_POSITION_MT4 WHERE EVALUATION_DT='2026-04-14'" -t

# 从文件执行 SQL
mysql-go -f query.sql

# 完整命令行参数
mysql-go -h mt4-db -P 3306 -D mt4_report -u reader -pSECRET -e "SELECT 1" -t

# 数据库名作为位置参数
mysql-go -h mt4-db -u reader mt4_report -e "SHOW TABLES"

# 从 stdin 读取 SQL
echo "SELECT NOW()" | mysql-go -f - -D mt4_report

# 配合 .my.cnf（无需密码参数）
mysql-go -D mt4_report -e "SELECT 1" -t
```

## 构建

```bash
# Windows
build.bat

# PowerShell
.\build.ps1
```

编译产物在 `bin/` 目录：
- `bin/mysql-go.exe` — Windows amd64（~5.2 MB）
- `bin/mysql-go` — Linux amd64（~5.0 MB）

## 文件结构

| 文件 | 说明 |
|---|---|
| `main.go` | 主程序，参数解析 + SQL 执行 |
| `mycnf.go` | `~/.my.cnf` [client] section 密码读取 |
| `go.mod` | Go module（`go-sql-driver/mysql v1.9.2`）|
| `build.bat` / `build.ps1` | 构建脚本（Win + Linux 交叉编译）|
| `.my.cnf.example` | MySQL 配置文件示例 |

## ECS 部署

将 `bin/mysql-go`（Linux 版本）放入容器镜像或挂载到任务定义中，通过环境变量或 `.my.cnf` 配置连接信息。典型用法是在编排脚本中查询 MT4 MySQL 源库。
