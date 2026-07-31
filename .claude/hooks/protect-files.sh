#!/bin/bash
# ──────────────────────────────────────────────────────────────────────
# File Protection Hook (PreToolUse)
#
# Blocks Claude from editing files that shouldn't change during
# normal development: production config, secrets, CI pipelines,
# and Claude's own guardrails.
#
# ADAPTING FOR YOUR PROJECT:
#   Add or remove patterns in PROTECTED_PATTERNS to match your project.
#   Common additions:
#     "Dockerfile"          — if you don't want Claude touching containers
#     "docker-compose"      — same for compose files
#     "Makefile"            — if your build config is off-limits
#     "terraform/"          — infrastructure-as-code
#     "k8s/"                — Kubernetes manifests
#     ".gitlab-ci"          — GitLab CI (instead of .github/workflows)
#     "Jenkinsfile"         — Jenkins pipelines
#     "package-lock.json"   — if you manage lock files manually
#
#   Pattern matching is substring-based (*pattern*), not glob.
#   A pattern of ".env" matches ".env", ".env.local", ".env.production".
#
#   Exit codes:
#     0 — allow the edit
#     2 — block the edit (Claude sees the error and tries another approach)
# ──────────────────────────────────────────────────────────────────────

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# ADAPT: Add patterns for files Claude should never edit in your project
PROTECTED_PATTERNS=(
  "application-prod"   # prod config (.yml / .properties)
  ".env"               # secrets
  ".pem" ".key" ".jks" # credentials & keystores
  ".github/workflows"  # CI pipelines
)

for pattern in "${PROTECTED_PATTERNS[@]}"; do
  if [[ "$FILE_PATH" == *"$pattern"* ]]; then
    echo "Blocked: $FILE_PATH matches '$pattern'" >&2
    exit 2
  fi
done
exit 0
