# Maven executable
MVN ?= mvn

# Colors & Formatting
YELLOW := \033[1;33m
GREEN  := \033[0;32m
RED    := \033[0;31m
BLUE   := \033[0;34m
CYAN   := \033[0;36m
BOLD   := \033[1m
NC     := \033[0m

.PHONY: help
help: ## Hiển thị danh sách các lệnh make khả dụng
	@echo "$(GREEN)$(BOLD)ShareRide - Available Commands$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""

.PHONY: new-service
new-service: ## Khởi tạo microservice mới chuẩn khuôn mẫu (vd: make new-service NAME=ride-service PORT=8082)
	@if [ -z "$(NAME)" ] || [ -z "$(PORT)" ]; then \
		echo "$(RED)Lỗi: Vui lòng cung cấp NAME và PORT.$(NC)"; \
		echo "$(YELLOW)Ví dụ: make new-service NAME=ride-service PORT=8082$(NC)"; \
		exit 1; \
	fi
	@./scripts/scaffold-service.sh $(NAME) $(PORT) $(GRPC_PORT) $(DB_PORT)

.PHONY: install-hooks
install-hooks: ## Cài đặt git hooks
	@echo "$(YELLOW)Installing git hooks...$(NC)"
	@./.githooks/install-hooks.sh

.PHONY: format
format: ## Tự động format code với Spotless
	@echo "$(YELLOW)Formatting code with Spotless...$(NC)"
	@$(MVN) spotless:apply
	@echo "$(GREEN)✓ Code formatted successfully!$(NC)"

.PHONY: check-format
check-format: ## Kiểm tra code formatting (Spotless)
	@echo "$(YELLOW)Checking code formatting...$(NC)"
	@$(MVN) spotless:check
	@echo "$(GREEN)✓ Formatting check passed!$(NC)"

.PHONY: check-style
check-style: ## Kiểm tra code style (Checkstyle)
	@echo "$(YELLOW)Checking code style...$(NC)"
	@$(MVN) checkstyle:check
	@echo "$(GREEN)✓ Style check passed!$(NC)"

.PHONY: compile
compile: ## Compile toàn bộ project (skip tests)
	@echo "$(YELLOW)Compiling project...$(NC)"
	@$(MVN) compile -DskipTests
	@echo "$(GREEN)✓ Compilation successful!$(NC)"

.PHONY: test
test: ## Chạy tất cả unit & integration tests
	@echo "$(YELLOW)Running tests...$(NC)"
	@$(MVN) test
	@echo "$(GREEN)✓ All tests passed!$(NC)"

.PHONY: build
build: ## Build và install toàn bộ project vào local repo
	@echo "$(YELLOW)Building project...$(NC)"
	@$(MVN) clean install
	@echo "$(GREEN)✓ Build successful!$(NC)"

.PHONY: clean
clean: ## Xoá các artifacts build (target/)
	@echo "$(YELLOW)Cleaning build artifacts...$(NC)"
	@$(MVN) clean
	@echo "$(GREEN)✓ Clean complete!$(NC)"

.PHONY: ci-quality
ci-quality: ## [CI Job 1] Kiểm tra Code Quality (Spotless & Checkstyle)
	@echo "$(CYAN)▶ [CI Job 1/3] Running Code Quality checks (Spotless & Checkstyle)...$(NC)"
	@$(MVN) spotless:check checkstyle:check --batch-mode || { \
		echo ""; \
		echo "$(RED)✗ Code quality check failed!$(NC)"; \
		echo "$(YELLOW)-> Gợi ý: Chạy 'make format' để tự động sửa lỗi định dạng mã nguồn.$(NC)"; \
		exit 1; \
	}
	@echo "$(GREEN)✓ [CI Job 1/3] Code Quality passed!$(NC)"

.PHONY: ci-test
ci-test: ## [CI Job 2] Chạy toàn bộ Tests & JaCoCo Coverage Report
	@echo "$(CYAN)▶ [CI Job 2/3] Running Tests & Coverage (common, auth-service + JaCoCo)...$(NC)"
	@$(MVN) test jacoco:report --batch-mode || { \
		echo ""; \
		echo "$(RED)✗ Tests or JaCoCo report failed!$(NC)"; \
		echo "$(YELLOW)-> Gợi ý: Kiểm tra file báo cáo chi tiết trong <module>/target/surefire-reports/$(NC)"; \
		exit 1; \
	}
	@echo "$(GREEN)✓ [CI Job 2/3] All Tests & JaCoCo Coverage passed!$(NC)"

.PHONY: ci-package
ci-package: ## [CI Job 3] Package JAR và kiểm tra Artifact Output
	@echo "$(CYAN)▶ [CI Job 3/3] Packaging project & verifying artifacts...$(NC)"
	@$(MVN) clean package -DskipTests --batch-mode || { \
		echo ""; \
		echo "$(RED)✗ Packaging failed!$(NC)"; \
		exit 1; \
	}
	@if [ -z "$$(ls -1 auth-service/target/auth-service-*.jar 2>/dev/null)" ]; then \
		echo ""; \
		echo "$(RED)✗ Artifact verification failed: auth-service-*.jar not found!$(NC)"; \
		exit 1; \
	fi
	@echo "$(GREEN)✓ [CI Job 3/3] Package & Artifact verification passed ($$(ls -1 auth-service/target/auth-service-*.jar | head -n 1))!$(NC)"

.PHONY: ci
ci: ## Chạy TOÀN BỘ các case kiểm tra trong CI theo từng Job (mô phỏng GitHub Actions)
	@echo ""
	@echo "$(BOLD)$(YELLOW)==========================================================$(NC)"
	@echo "$(BOLD)$(YELLOW)       ShareRide - Local CI Verification Pipeline         $(NC)"
	@echo "$(BOLD)$(YELLOW)==========================================================$(NC)"
	@echo ""
	@$(MAKE) ci-quality
	@echo ""
	@$(MAKE) ci-test
	@echo ""
	@$(MAKE) ci-package
	@echo ""
	@echo "$(BOLD)$(GREEN)==========================================================$(NC)"
	@echo "$(BOLD)$(GREEN) ✓ ALL CI JOBS PASSED 100%! Bạn có thể yên tâm push code!  $(NC)"
	@echo "$(BOLD)$(GREEN)==========================================================$(NC)"
	@echo ""

.PHONY: ci-fast
ci-fast: ## Chạy toàn bộ kiểm tra CI trong 1 phiên Maven duy nhất (siêu tốc, ~30s)
	@echo ""
	@echo "$(BOLD)$(YELLOW)==========================================================$(NC)"
	@echo "$(BOLD)$(YELLOW)     ShareRide - Fast CI Verification (Single Maven Run)  $(NC)"
	@echo "$(BOLD)$(YELLOW)==========================================================$(NC)"
	@echo ""
	@$(MVN) clean verify spotless:check checkstyle:check jacoco:report --batch-mode || { \
		echo ""; \
		echo "$(RED)✗ CI verification failed!$(NC)"; \
		echo "$(YELLOW)-> Gợi ý: Chạy 'make format' nếu lỗi format hoặc chạy 'make ci' để debug từng job.$(NC)"; \
		exit 1; \
	}
	@if [ -z "$$(ls -1 auth-service/target/auth-service-*.jar 2>/dev/null)" ]; then \
		echo ""; \
		echo "$(RED)✗ Artifact verification failed: auth-service-*.jar not found!$(NC)"; \
		exit 1; \
	fi
	@echo ""
	@echo "$(BOLD)$(GREEN)==========================================================$(NC)"
	@echo "$(BOLD)$(GREEN) ✓ ALL CI CHECKS PASSED 100%! Artifacts verified!          $(NC)"
	@echo "$(BOLD)$(GREEN)==========================================================$(NC)"
	@echo ""

.PHONY: coverage-report
coverage-report: ## Hiển thị đường dẫn các báo cáo JaCoCo HTML coverage
	@echo "$(CYAN)Báo cáo JaCoCo HTML Coverage:$(NC)"
	@echo "  - Common:       file://$(CURDIR)/common/target/site/jacoco/index.html"
	@echo "  - Auth Service: file://$(CURDIR)/auth-service/target/site/jacoco/index.html"

.PHONY: verify
verify: ci ## Chạy toàn bộ kiểm tra (alias cho make ci)

.PHONY: pre-commit-check
pre-commit-check: check-format check-style compile ## Kiểm tra nhanh tương đương pre-commit hook
	@echo "$(YELLOW)Running pre-commit checks...$(NC)"
	@$(MVN) test -q
	@echo "$(GREEN)✓ Pre-commit checks passed!$(NC)"

.PHONY: pre-push-check
pre-push-check: ci-fast ## Kiểm tra toàn diện tương đương pre-push hook (sử dụng ci-fast)
	@echo "$(GREEN)✓ Pre-push checks passed!$(NC)"
