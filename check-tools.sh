#!/bin/bash
echo "=== Checking installed tools ==="
check() {
  if command -v "$1" &> /dev/null; then
    echo "✅ $1 found: $($2 2>&1 | head -n 1)"
  else
    echo "❌ $1 NOT found"
  fi
}
check brew "brew --version"
check java "java -version"
check mvn "mvn -version"
check python3 "python3 --version"
check node "node -v"
check npm "npm -v"
check g++ "g++ --version"
check cmake "cmake --version"
check git "git --version"
check docker "docker --version"
docker compose version &> /dev/null && echo "✅ docker compose: $(docker compose version)" || echo "❌ docker compose NOT found"
echo "=== Done ==="
