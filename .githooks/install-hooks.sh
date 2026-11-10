#!/bin/bash
# ShareRide - Git Hooks Installer
# Configures Git to use version-controlled hooks from .githooks/

set -e

# Terminal colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}  ShareRide - Installing Git Hooks     ${NC}"

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

# Ensure hooks have executable permissions
chmod +x .githooks/pre-commit 2>/dev/null || true
chmod +x .githooks/pre-push 2>/dev/null || true
chmod +x .githooks/install-hooks.sh 2>/dev/null || true

# Point git core.hooksPath to .githooks
CURRENT_HOOKS_PATH="$(git config core.hooksPath 2>/dev/null || true)"
if [ "$CURRENT_HOOKS_PATH" = ".githooks" ]; then
   echo -e "${GREEN}✓ Git core.hooksPath is already configured to .githooks${NC}"
elif git config core.hooksPath .githooks 2>/dev/null; then
   echo -e "${GREEN}✓ Git core.hooksPath set to .githooks${NC}"
else
   echo -e "${YELLOW}! Unable to set core.hooksPath via git config. Trying fallback to .git/hooks...${NC}"
   mkdir -p .git/hooks 2>/dev/null || true
   ln -sf ../../.githooks/pre-commit .git/hooks/pre-commit 2>/dev/null || cp .githooks/pre-commit .git/hooks/pre-commit 2>/dev/null || true
   ln -sf ../../.githooks/pre-push .git/hooks/pre-push 2>/dev/null || cp .githooks/pre-push .git/hooks/pre-push 2>/dev/null || true
fi

echo -e "${GREEN}✓ All Git hooks configured successfully!${NC}"
echo -e "   - Pre-commit: Format check (Spotless) & Lint/Standards (Checkstyle) [Fast, No tests]"
echo -e "   - Pre-push: Branch protection, clean build, full test suite (Unit, ArchUnit, Integration) & artifact packaging"
