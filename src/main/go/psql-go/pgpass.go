package main

import (
	"bufio"
	"os"
	"path/filepath"
	"runtime"
	"strings"
)

// pgpassEntry represents one line from a pgpass.conf file.
type pgpassEntry struct {
	hostname string
	port     string
	database string
	username string
	password string
}

// lookupPgpass searches for a matching password in pgpass.conf.
// Search order: PGPASSFILE env var → default path (~/.pgpass on Linux, %APPDATA%\postgresql\pgpass.conf on Windows).
func lookupPgpass(host, port, database, user string) string {
	path := os.Getenv("PGPASSFILE")
	if path == "" {
		path = defaultPgpassPath()
	}
	if path == "" {
		return ""
	}

	f, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}

		entry, ok := parsePgpassLine(line)
		if !ok {
			continue
		}

		if matchField(entry.hostname, host) &&
			matchField(entry.port, port) &&
			matchField(entry.database, database) &&
			matchField(entry.username, user) {
			return entry.password
		}
	}
	return ""
}

// defaultPgpassPath returns the OS-specific default pgpass file path.
func defaultPgpassPath() string {
	if runtime.GOOS == "windows" {
		appdata := os.Getenv("APPDATA")
		if appdata != "" {
			return filepath.Join(appdata, "postgresql", "pgpass.conf")
		}
		return ""
	}
	home, err := os.UserHomeDir()
	if err != nil {
		return ""
	}
	return filepath.Join(home, ".pgpass")
}

// parsePgpassLine parses a single pgpass.conf line.
// Format: hostname:port:database:username:password
// Supports \: (literal colon) and \\ (literal backslash) escapes.
func parsePgpassLine(line string) (pgpassEntry, bool) {
	fields := splitPgpassFields(line)
	if len(fields) != 5 {
		return pgpassEntry{}, false
	}
	return pgpassEntry{
		hostname: fields[0],
		port:     fields[1],
		database: fields[2],
		username: fields[3],
		password: fields[4],
	}, true
}

// splitPgpassFields splits a pgpass line by unescaped colons.
func splitPgpassFields(line string) []string {
	var fields []string
	var current strings.Builder
	i := 0
	for i < len(line) {
		ch := line[i]
		if ch == '\\' && i+1 < len(line) {
			next := line[i+1]
			if next == ':' || next == '\\' {
				current.WriteByte(next)
				i += 2
				continue
			}
		}
		if ch == ':' {
			fields = append(fields, current.String())
			current.Reset()
			i++
			continue
		}
		current.WriteByte(ch)
		i++
	}
	fields = append(fields, current.String())
	return fields
}

// matchField checks if a pgpass field matches the target value.
// Wildcard "*" matches anything.
func matchField(pattern, value string) bool {
	return pattern == "*" || pattern == value
}
