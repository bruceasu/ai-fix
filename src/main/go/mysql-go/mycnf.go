package main

import (
	"bufio"
	"os"
	"path/filepath"
	"runtime"
	"strings"
)

// lookupMyCnfPassword searches for password in ~/.my.cnf [client] section.
// This is the standard MySQL option file for storing credentials in plaintext.
// For ECS deployment, prefer MYSQL_PWD env var or mount a .my.cnf file.
func lookupMyCnfPassword(host, port, username string) string {
	paths := myCnfPaths()
	for _, p := range paths {
		if pw := readMyCnfPassword(p); pw != "" {
			return pw
		}
	}
	return ""
}

// myCnfPaths returns candidate paths for MySQL option files, in priority order.
func myCnfPaths() []string {
	var paths []string

	// Explicit env override
	if f := os.Getenv("MYSQL_DEFAULTS_FILE"); f != "" {
		paths = append(paths, f)
	}

	if runtime.GOOS == "windows" {
		// Windows: %APPDATA%\MySQL\.mylogin.cnf, then %USERPROFILE%\.my.cnf
		if appdata := os.Getenv("APPDATA"); appdata != "" {
			paths = append(paths, filepath.Join(appdata, "MySQL", ".my.cnf"))
		}
		if home := os.Getenv("USERPROFILE"); home != "" {
			paths = append(paths, filepath.Join(home, ".my.cnf"))
		}
	} else {
		// Linux/macOS: ~/.my.cnf
		if home, err := os.UserHomeDir(); err == nil {
			paths = append(paths, filepath.Join(home, ".my.cnf"))
		}
	}

	return paths
}

// readMyCnfPassword reads the password from a .my.cnf file's [client] section.
// Format:
//
//	[client]
//	user=root
//	password=secret
//	host=localhost
func readMyCnfPassword(path string) string {
	f, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer f.Close()

	inClientSection := false
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())

		// Skip empty lines and comments
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, ";") {
			continue
		}

		// Section header
		if strings.HasPrefix(line, "[") {
			section := strings.TrimSpace(strings.Trim(line, "[]"))
			inClientSection = (section == "client")
			continue
		}

		if !inClientSection {
			continue
		}

		// Parse key=value (or key = value)
		key, value, ok := parseIniLine(line)
		if !ok {
			continue
		}

		if key == "password" {
			return value
		}
	}

	return ""
}

// parseIniLine parses a key=value line from a .my.cnf file.
// Handles optional quotes around values.
func parseIniLine(line string) (key, value string, ok bool) {
	idx := strings.IndexByte(line, '=')
	if idx < 0 {
		return "", "", false
	}
	key = strings.TrimSpace(line[:idx])
	value = strings.TrimSpace(line[idx+1:])

	// Remove surrounding quotes
	if len(value) >= 2 {
		if (value[0] == '"' && value[len(value)-1] == '"') ||
			(value[0] == '\'' && value[len(value)-1] == '\'') {
			value = value[1 : len(value)-1]
		}
	}

	return key, value, true
}
