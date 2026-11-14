#!/bin/bash
# ==============================================================================
# Track Large Files (>1MB) with Git LFS
# ==============================================================================
# This script automatically finds and tracks files larger than 1MB with Git LFS
# Usage: .githooks/track-large-files.sh

set -e

YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}Scanning for files larger than 1MB...${NC}"

# Find all files > 1MB (excluding .git directory)
LARGE_FILES=$(find . -type f -size +1M ! -path "./.git/*" ! -path "*/target/*" ! -path "*/node_modules/*" 2>/dev/null || true)

if [ -z "$LARGE_FILES" ]; then
  echo -e "${GREEN}✓ No files larger than 1MB found.${NC}"
  exit 0
fi

echo -e "${YELLOW}Found files larger than 1MB:${NC}"
echo "$LARGE_FILES" | while read -r file; do
  size=$(ls -lh "$file" | awk '{print $5}')
  echo -e "  ${size}\t${file}"
done

echo ""
echo -e "${BLUE}Checking which files are already tracked by Git LFS...${NC}"

TRACKED_COUNT=0
UNTRACKED_COUNT=0

echo "$LARGE_FILES" | while read -r file; do
  if git check-attr filter -- "$file" | grep -q "filter: lfs"; then
    echo -e "${GREEN}✓ Already tracked:${NC} $file"
    TRACKED_COUNT=$((TRACKED_COUNT + 1))
  else
    echo -e "${YELLOW}⚠ Not tracked:${NC} $file"
    UNTRACKED_COUNT=$((UNTRACKED_COUNT + 1))
  fi
done

echo ""
echo -e "${BLUE}Track these untracked large files with Git LFS?${NC}"
echo -e "Run manually: ${GREEN}git lfs track \"path/to/file\"${NC}"

