.PHONY: help install-hooks format check-format check-style compile test build clean

# Colors
YELLOW := \033[1;33m
GREEN := \033[0;32m
RED := \033[0;31m
NC := \033[0m

help: ## Hiển thị help
	@echo "$(GREEN)ShareRide - Available Commands$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""

install-hooks: ## Cài đặt git hooks
	@echo "$(YELLOW)Installing git hooks...$(NC)"
	@./.githooks/install-hooks.sh

format: ## Tự động format code với Spotless
	@echo "$(YELLOW)Formatting code...$(NC)"
	@mvn spotless:apply
	@echo "$(GREEN)✓ Code formatted successfully!$(NC)"

check-format: ## Kiểm tra code formatting
	@echo "$(YELLOW)Checking code formatting...$(NC)"
	@mvn spotless:check
	@echo "$(GREEN)✓ Formatting check passed!$(NC)"

check-style: ## Kiểm tra code style với Checkstyle
	@echo "$(YELLOW)Checking code style...$(NC)"
	@mvn checkstyle:check
	@echo "$(GREEN)✓ Style check passed!$(NC)"

compile: ## Compile toàn bộ project
	@echo "$(YELLOW)Compiling...$(NC)"
	@mvn compile -DskipTests
	@echo "$(GREEN)✓ Compilation successful!$(NC)"

test: ## Chạy tất cả tests
	@echo "$(YELLOW)Running tests...$(NC)"
	@mvn test
	@echo "$(GREEN)✓ All tests passed!$(NC)"

build: ## Build toàn bộ project
	@echo "$(YELLOW)Building project...$(NC)"
	@mvn clean install
	@echo "$(GREEN)✓ Build successful!$(NC)"

clean: ## Clean build artifacts
	@echo "$(YELLOW)Cleaning...$(NC)"
	@mvn clean
	@echo "$(GREEN)✓ Clean complete!$(NC)"

verify: check-format check-style compile test ## Chạy tất cả các kiểm tra
	@echo ""
	@echo "$(GREEN)✓ All verifications passed!$(NC)"

pre-commit-check: check-format check-style compile ## Giống như pre-commit hook
	@echo "$(YELLOW)Running pre-commit checks...$(NC)"
	@mvn test -q
	@echo "$(GREEN)✓ Pre-commit checks passed!$(NC)"

pre-push-check: clean build test ## Giống như pre-push hook
	@echo "$(GREEN)✓ Pre-push checks passed!$(NC)"
