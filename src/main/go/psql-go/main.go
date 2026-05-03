// psql-go: lightweight psql replacement for ECS Fargate containers.
//
// Supports the subset of psql flags actually used in sync orchestration scripts:
//
//	-c "SQL"              Execute a SQL command
//	-f <file>             Read SQL from file ("-" for stdin; mutually exclusive with -c)
//	-t                    Tuples-only output (no headers/footers)
//	-q                    Quiet (accepted, no-op)
//	-v ON_ERROR_STOP=1    Error handling (accepted, always enabled)
//	--no-psqlrc           Accepted, no-op
//	-h / --host           Override PGHOST
//	-p / --port           Override PGPORT
//	-d / --dbname         Override PGDATABASE
//	-U / --username       Override PGUSER
//
// Connection: reads PG* environment variables (PGHOST, PGPORT, PGDATABASE, PGUSER, PGSSLMODE).
// Password:   PGPASSWORD → PGPASSFILE / ~/.pgpass
package main

import (
	"database/sql"
	"fmt"
	"io"
	"os"
	"strings"

	_ "github.com/lib/pq"
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

	// Parse arguments (psql-compatible subset)
	i := 0
	for i < len(args) {
		arg := args[i]
		switch {
		case arg == "--help" || arg == "-?":
			printHelp()
			return 0
		case arg == "-c" && i+1 < len(args):
			command = args[i+1]
			i += 2
		case arg == "-f" && i+1 < len(args):
			sqlFile = args[i+1]
			i += 2
		case arg == "-t":
			tuplesOnly = true
			i++
		case arg == "-q", arg == "--no-psqlrc":
			i++ // accepted, no-op
		case strings.HasPrefix(arg, "-v") && i+1 < len(args):
			// -v ON_ERROR_STOP=1 — accepted, always enabled
			i += 2
		case strings.HasPrefix(arg, "-v"):
			// -vON_ERROR_STOP=1 (no space)
			i++
		case (arg == "-h" || arg == "--host") && i+1 < len(args):
			host = args[i+1]
			i += 2
		case (arg == "-p" || arg == "--port") && i+1 < len(args):
			port = args[i+1]
			i += 2
		case (arg == "-d" || arg == "--dbname") && i+1 < len(args):
			dbname = args[i+1]
			i += 2
		case (arg == "-U" || arg == "--username") && i+1 < len(args):
			username = args[i+1]
			i += 2
		default:
			fmt.Fprintf(os.Stderr, "psql-go: unknown option: %s\n", arg)
			return 1
		}
	}

	if command != "" && sqlFile != "" {
		fmt.Fprintln(os.Stderr, "psql-go: -c and -f are mutually exclusive")
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
			fmt.Fprintf(os.Stderr, "psql-go: reading %s: %v\n", sqlFile, err)
			return 1
		}
		command = strings.TrimSpace(string(data))
	}
	if command == "" {
		fmt.Fprintln(os.Stderr, "psql-go: -c <command> or -f <file> is required")
		return 1
	}

	// Resolve connection parameters: CLI flags > env vars > defaults
	if host == "" {
		host = envOrDefault("PGHOST", "localhost")
	}
	if port == "" {
		port = envOrDefault("PGPORT", "5432")
	}
	if dbname == "" {
		dbname = os.Getenv("PGDATABASE")
	}
	if username == "" {
		username = os.Getenv("PGUSER")
	}
	if dbname == "" {
		fmt.Fprintln(os.Stderr, "psql-go: database name required (PGDATABASE or -d)")
		return 1
	}
	if username == "" {
		fmt.Fprintln(os.Stderr, "psql-go: username required (PGUSER or -U)")
		return 1
	}

	password := os.Getenv("PGPASSWORD")
	if password == "" {
		password = lookupPgpass(host, port, dbname, username)
	}

	sslmode := envOrDefault("PGSSLMODE", "require")

	dsn := fmt.Sprintf("host=%s port=%s dbname=%s user=%s password=%s sslmode=%s",
		host, port, dbname, username, password, sslmode)

	db, err := sql.Open("postgres", dsn)
	if err != nil {
		fmt.Fprintf(os.Stderr, "psql-go: connection error: %v\n", err)
		return 1
	}
	defer db.Close()

	rows, err := db.Query(command)
	if err != nil {
		fmt.Fprintf(os.Stderr, "psql-go: query error: %v\n", err)
		return 1
	}
	defer rows.Close()

	cols, err := rows.Columns()
	if err != nil {
		fmt.Fprintf(os.Stderr, "psql-go: columns error: %v\n", err)
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
			fmt.Fprintf(os.Stderr, "psql-go: scan error: %v\n", err)
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
			// psql -t: values separated by |, leading space before each field
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
		fmt.Fprintf(os.Stderr, "psql-go: rows error: %v\n", err)
		return 1
	}

	return 0
}

func printHelp() {
	help := `psql-go: lightweight psql replacement for ECS Fargate containers.

Usage:
  psql-go [OPTIONS]

Options:
  -c "SQL"              Execute a SQL command string
  -f <file>             Read SQL from file ("-" for stdin; mutually exclusive with -c)
  -t                    Tuples-only output (no column headers/footers)
  -q                    Quiet mode (accepted, no-op)
  -v KEY=VALUE          Set variable (e.g. -v ON_ERROR_STOP=1; accepted, always enabled)
  --no-psqlrc           Skip psqlrc (accepted, no-op)
  -h, --host HOST       Database server host (default: PGHOST or localhost)
  -p, --port PORT       Database server port (default: PGPORT or 5432)
  -d, --dbname DBNAME   Database name (default: PGDATABASE)
  -U, --username USER   Database user (default: PGUSER)
  --help, -?            Show this help message

Environment Variables:
  PGHOST                Database host
  PGPORT                Database port
  PGDATABASE            Database name
  PGUSER                Database user
  PGPASSWORD            Database password
  PGPASSFILE            Path to pgpass.conf (default: ~/.pgpass)
  PGSSLMODE             SSL mode (default: require)

Examples:
  # Query with environment variables
  export PGHOST=db.example.com PGDATABASE=mydb PGUSER=reader PGPASSWORD=secret
  psql-go -c "SELECT COUNT(*) FROM users" -t

  # Execute SQL from file
  psql-go -f query.sql

  # Full command-line options
  psql-go -h db.example.com -p 5432 -d mydb -U reader -c "SELECT 1" -t

  # Read SQL from stdin
  echo "SELECT NOW()" | psql-go -f -
`
	fmt.Print(help)
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
