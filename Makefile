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

.PHONY: install-hooks
install-hooks: ## Cài đặt git hooks
	@echo "$(YELLOW)Installing git hooks...$(NC)"
	@./.githooks/install-hooks.sh

.PHONY: verify-push
verify-push: ## Chạy thử nghiệm smart pre-push hook trên các thay đổi hiện tại
	@echo "$(YELLOW)Running pre-push verification...$(NC)"
	@./.githooks/pre-push < /dev/null

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
ci: ## Chạy TOÀN BỘ các case kiểm tra trong CI theo từng Job
	@echo ""
	@echo "$(BOLD)$(YELLOW)----------------------------------------------------------$(NC)"
	@echo "$(BOLD)$(YELLOW)       ShareRide - Local CI Verification Pipeline         $(NC)"
	@echo "$(BOLD)$(YELLOW)----------------------------------------------------------$(NC)"
	@echo ""
	@$(MAKE) ci-quality
	@echo ""
	@$(MAKE) ci-test
	@echo ""
	@$(MAKE) ci-package
	@echo ""
	@echo "$(BOLD)$(GREEN)----------------------------------------------------------$(NC)"
	@echo "$(BOLD)$(GREEN)            ✓ ALL CI JOBS PASSED 100%!                    $(NC)"
	@echo "$(BOLD)$(GREEN)----------------------------------------------------------$(NC)"
	@echo ""

.PHONY: dev
dev: ## Khởi chạy hệ thống môi trường phát triển với Process-Compose
	@PC_PORT_NUM=9999 process-compose up

.PHONY: graph-update
graph-update: ## Cập nhật knowledge graph của codebase bằng Graphify
	@echo "$(YELLOW)Updating knowledge graph with Graphify...$(NC)"
	@graphify update .
	@echo "$(GREEN)✓ Knowledge graph updated successfully!$(NC)"

.PHONY: graph-view
graph-view: ## Mở giao diện trực quan graph.html trên trình duyệt
	@open graphify-out/graph.html
