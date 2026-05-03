// mysql-go: lightweight mysql CLI replacement for ECS Fargate containers.
//
// Supports the subset of mysql flags used in sync orchestration scripts:
//
//	-e "SQL"              Execute a SQL command (alias for -c)
//	-c "SQL"              Execute a SQL command (psql-go compat)
//	-f <file>             Read SQL from file ("-" for stdin; mutually exclusive with -e/-c)
//	-t                    Tuples-only output (no headers/footers, tab-separated → pipe-separated)
//	-N                    Skip column names (alias for -t)
//	-q                    Quiet (accepted, no-op)
//	-B                    Batch mode (accepted, always enabled)
//	-h / --host           Override MYSQL_HOST
//	-P / --port           Override MYSQL_TCP_PORT
//	-D / --database       Override MYSQL_DATABASE (or positional arg)
//	-u / --user           Override MYSQL_USER (or USER)
//
// Connection: reads MYSQL_* environment variables.
// Password:   MYSQL_PWD → defaults-file (~/.my.cnf [client] section)
package main

import (
	"database/sql"
	"fmt"
	"io"
	"os"
	"strings"

	_ "github.com/go-sql-driver/mysql"
)

func main() {
	os.Exit(run(os.Args[1:]))
}

func run(args []string) int {
	var (
		command    string
		sqlFile    string
		tuplesOnly bool
		host       string
		port       string
		dbname     string
		username   string
	)

	// Parse arguments (mysql-compatible subset + psql-go compat flags)
	i := 0
	for i < len(args) {
		arg := args[i]
		switch {
		case arg == "--help" || arg == "-?":
			printHelp()
			return 0
		case (arg == "-e" || arg == "-c") && i+1 < len(args):
			command = args[i+1]
			i += 2
		case strings.HasPrefix(arg, "-e") && len(arg) > 2:
			// -e"SQL" (no space)
			command = arg[2:]
			i++
		case arg == "-f" && i+1 < len(args):
			sqlFile = args[i+1]
			i += 2
		case arg == "-t", arg == "-N":
			tuplesOnly = true
			i++
		case arg == "-q", arg == "-B", arg == "--batch":
			i++ // accepted, no-op
		case (arg == "-h" || arg == "--host") && i+1 < len(args):
			host = args[i+1]
			i += 2
		case (arg == "-P" || arg == "--port") && i+1 < len(args):
			port = args[i+1]
			i += 2
		case (arg == "-D" || arg == "--database") && i+1 < len(args):
			dbname = args[i+1]
			i += 2
		case (arg == "-u" || arg == "--user") && i+1 < len(args):
			username = args[i+1]
			i += 2
		case arg == "-p" && i+1 < len(args):
			// -p <password> — accept but prefer env var
			os.Setenv("MYSQL_PWD", args[i+1])
			i += 2
		case strings.HasPrefix(arg, "-p") && len(arg) > 2:
			// -pPASSWORD (no space) — mysql convention
			os.Setenv("MYSQL_PWD", arg[2:])
			i++
		default:
			// Positional arg: treat as database name if not set
			if !strings.HasPrefix(arg, "-") && dbname == "" {
				dbname = arg
				i++
			} else {
				fmt.Fprintf(os.Stderr, "mysql-go: unknown option: %s\n", arg)
				return 1
			}
		}
	}

	if command != "" && sqlFile != "" {
		fmt.Fprintln(os.Stderr, "mysql-go: -e/-c and -f are mutually exclusive")
		return 1
	}
	if sqlFile != "" {
		var data []byte
		var err error
		if sqlFile == "-" {
			data, err = io.ReadAll(os.Stdin)
		} else {
			data, err = os.ReadFile(sqlFile)
		}
		if err != nil {
			fmt.Fprintf(os.Stderr, "mysql-go: reading %s: %v\n", sqlFile, err)
			return 1
		}
		command = strings.TrimSpace(string(data))
	}
	if command == "" {
		fmt.Fprintln(os.Stderr, "mysql-go: -e <command> or -f <file> is required")
		return 1
	}

	// Resolve connection parameters: CLI flags > env vars > defaults
	if host == "" {
		host = envOrDefault("MYSQL_HOST", "localhost")
	}
	if port == "" {
		port = envOrDefault("MYSQL_TCP_PORT", "3306")
	}
	if dbname == "" {
		dbname = os.Getenv("MYSQL_DATABASE")
	}
	if username == "" {
		username = os.Getenv("MYSQL_USER")
		if username == "" {
			username = os.Getenv("USER")
		}
	}
	if dbname == "" {
		fmt.Fprintln(os.Stderr, "mysql-go: database name required (MYSQL_DATABASE or -D)")
		return 1
	}
	if username == "" {
		fmt.Fprintln(os.Stderr, "mysql-go: username required (MYSQL_USER or -u)")
		return 1
	}

	password := os.Getenv("MYSQL_PWD")
	if password == "" {
		password = lookupMyCnfPassword(host, port, username)
	}

	tls := envOrDefault("MYSQL_SSL_MODE", "false")

	// go-sql-driver/mysql DSN format: user:password@tcp(host:port)/dbname?params
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?tls=%s&charset=utf8mb4&parseTime=true",
		username, password, host, port, dbname, tls)

	db, err := sql.Open("mysql", dsn)
	if err != nil {
		fmt.Fprintf(os.Stderr, "mysql-go: connection error: %v\n", err)
		return 1
	}
	defer db.Close()

	// Detect if it's a SELECT/SHOW/DESCRIBE/EXPLAIN or a DML/DDL
	trimmed := strings.TrimSpace(command)
	upper := strings.ToUpper(trimmed)
	if strings.HasPrefix(upper, "SELECT") ||
		strings.HasPrefix(upper, "SHOW") ||
		strings.HasPrefix(upper, "DESCRIBE") ||
		strings.HasPrefix(upper, "DESC ") ||
		strings.HasPrefix(upper, "EXPLAIN") ||
		strings.HasPrefix(upper, "WITH") {
		return executeQuery(db, command, tuplesOnly)
	}
	return executeExec(db, command)
}

func executeQuery(db *sql.DB, command string, tuplesOnly bool) int {
	rows, err := db.Query(command)
	if err != nil {
		fmt.Fprintf(os.Stderr, "mysql-go: query error: %v\n", err)
		return 1
	}
	defer rows.Close()

	cols, err := rows.Columns()
	if err != nil {
		fmt.Fprintf(os.Stderr, "mysql-go: columns error: %v\n", err)
		return 1
	}

	if !tuplesOnly && len(cols) > 0 {
		fmt.Println(strings.Join(cols, "|"))
	}

	values := make([]sql.NullString, len(cols))
	ptrs := make([]any, len(cols))
	for j := range values {
		ptrs[j] = &values[j]
	}

	for rows.Next() {
		if err := rows.Scan(ptrs...); err != nil {
			fmt.Fprintf(os.Stderr, "mysql-go: scan error: %v\n", err)
			return 1
		}

		parts := make([]string, len(cols))
		for j, v := range values {
			if v.Valid {
				parts[j] = v.String
			} else {
				parts[j] = ""
			}
		}

		if tuplesOnly {
			// psql -t compatible: values separated by |, leading space
			formatted := make([]string, len(parts))
			for j, p := range parts {
				formatted[j] = " " + p
			}
			fmt.Println(strings.Join(formatted, "|"))
		} else {
			fmt.Println(strings.Join(parts, "|"))
		}
	}

	if err := rows.Err(); err != nil {
		fmt.Fprintf(os.Stderr, "mysql-go: rows error: %v\n", err)
		return 1
	}

	return 0
}

func executeExec(db *sql.DB, command string) int {
	result, err := db.Exec(command)
	if err != nil {
		fmt.Fprintf(os.Stderr, "mysql-go: exec error: %v\n", err)
		return 1
	}
	affected, _ := result.RowsAffected()
	if affected > 0 {
		fmt.Fprintf(os.Stderr, "Rows affected: %d\n", affected)
	}
	return 0
}

func printHelp() {
	help := `mysql-go: lightweight mysql CLI replacement for ECS Fargate containers.

Usage:
  mysql-go [OPTIONS] [database]

Options:
  -e "SQL"              Execute a SQL command string
  -c "SQL"              Execute a SQL command string (psql-go compatible alias)
  -f <file>             Read SQL from file ("-" for stdin; mutually exclusive with -e/-c)
  -t                    Tuples-only output (no column headers)
  -N                    Skip column names (alias for -t)
  -B, --batch           Batch mode (accepted, always enabled)
  -q                    Quiet mode (accepted, no-op)
  -h, --host HOST       Database server host (default: MYSQL_HOST or localhost)
  -P, --port PORT       Database server port (default: MYSQL_TCP_PORT or 3306)
  -D, --database DB     Database name (default: MYSQL_DATABASE)
  -u, --user USER       Database user (default: MYSQL_USER or USER)
  -p[PASSWORD]          Database password (prefer MYSQL_PWD env var)
  --help, -?            Show this help message

Environment Variables:
  MYSQL_HOST            Database host
  MYSQL_TCP_PORT        Database port
  MYSQL_DATABASE        Database name
  MYSQL_USER            Database user
  MYSQL_PWD             Database password
  MYSQL_SSL_MODE        TLS mode (default: false; set "true" or "skip-verify")
  MYSQL_DEFAULTS_FILE   Path to .my.cnf (default: ~/.my.cnf)

Password Lookup:
  1. MYSQL_PWD environment variable
  2. ~/.my.cnf [client] section (or MYSQL_DEFAULTS_FILE)

Examples:
  # Query with environment variables
  export MYSQL_HOST=mt4-db.example.com MYSQL_DATABASE=mt4_report
  export MYSQL_USER=reader MYSQL_PWD=secret
  mysql-go -e "SELECT COUNT(*) FROM t_trade_journal_rpt" -t

  # Execute SQL from file
  mysql-go -f query.sql

  # Full command-line options
  mysql-go -h mt4-db -P 3306 -D mt4_report -u reader -pSECRET -e "SELECT 1" -t

  # Database as positional argument
  mysql-go -h mt4-db -u reader mt4_report -e "SHOW TABLES"

  # Read SQL from stdin
  echo "SELECT NOW()" | mysql-go -f - -D mt4_report
`
	fmt.Print(help)
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
