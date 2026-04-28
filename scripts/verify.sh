#!/usr/bin/env bash
set -euo pipefail

echo "[verify] format"
# add formatter command here

echo "[verify] lint"
# add lint command here

echo "[verify] typecheck"
# add typecheck command here

echo "[verify] unit tests"
# add unit test command here

echo "[verify] integration tests"
# add integration test command here

echo "[verify] build"
# add build command here

# for java projects, you might want to run maven or gradle commands
#!/usr/bin/env bash
set -euo pipefail

echo "[verify] test"
mvn test

echo "[verify] verify"
mvn verify


# for python projects, you might want to run pytest or flake8 commands
#!/usr/bin/env bash
set -euo pipefail

echo "[verify] ruff"
ruff check .

echo "[verify] format-check"
ruff format --check .

echo "[verify] tests"
pytest

for go projects, you might want to run gofmt, go vet, go test, and go build commands
#!/usr/bin/env bash
set -euo pipefail

echo "[verify] fmt"
gofmt -l .

echo "[verify] vet"
go vet ./...

echo "[verify] test"
go test ./...

echo "[verify] build"
go build ./...