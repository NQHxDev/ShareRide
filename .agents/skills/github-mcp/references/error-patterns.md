# ShareRide CI/CD & PR Error Pattern Catalog

This reference lists common failure signatures encountered in ShareRide CI/CD workflows, their root causes, and standard remediation actions.

---

## 1. Static Analysis Failures (`code-quality`)

### Pattern 1.1: Spotless Formatting Failure

- **Indicator in logs:**

    ```txt
    [ERROR] Failed to execute goal com.diffplug.spotless:spotless-maven-plugin:...:check
    [ERROR] The following files had format violations:
    [ERROR]   services/auth-service/src/main/java/.../AuthServiceImpl.java
    [ERROR] Run 'mvn spotless:apply' to fix these violations.
    ```

- **Root Cause:** Code was committed without running Google Java Format formatting.
- **Remediation:**

    ```bash
    mvn spotless:apply
    git add -u && git commit -m "style: apply spotless code formatting"
    ```

### Pattern 1.2: Checkstyle Rule Violation

- **Indicator in logs:**

    ```txt
    [ERROR] src/main/java/com/shareride/auth/.../AuthController.java:[15,1] (imports) CustomImportOrder
    [ERROR] src/main/java/com/shareride/auth/.../AuthService.java:[42,5] (whitespace) FileTabCharacter
    [ERROR] Checkstyle rule violations were found.
    ```

- **Root Cause:** Unused import, wildcards in imports (`.*`), tab characters, or naming conventions violating Checkstyle rules.
- **Remediation:**

    ```bash
    mvn checkstyle:check
    # Fix indicated lines manually, then verify:
    mvn checkstyle:check
    ```

---

## 2. Test Execution Failures (`test-modules` & `integration-test`)

### Pattern 2.1: ArchUnit Architecture Violation

- **Indicator in logs:**

    ```txt
    java.lang.AssertionError: Architecture Violation [Priority: MEDIUM] - Rule 'Controllers should not depend on repositories directly' was violated (1 times):
    Method <com.shareride.auth.controller.v1.AuthController.login(...)> calls method <com.shareride.auth.repository.UserRepository.findByEmail(...)>
    ```

- **Root Cause:** Controller bypasses the service layer and injects or invokes Repository directly. ShareRide strictly enforces `Controller -> Service -> Repository`.
- **Remediation:**
    1. Move logic into `*Service` interface and `*ServiceImpl`.
    2. Inject `*Service` into Controller.
    3. Re-run `mvn test -pl <module> -Dtest="*ArchitectureTest"`.

### Pattern 2.2: Mockito / Unit Test Assertion Failure

- **Indicator in logs:**

    ```txt
    [ERROR] Failures:
    [ERROR]   AuthServiceImplTest.testLogin_WithValidCredentials_ShouldReturnToken:88
    [ERROR]   org.opentest4j.AssertionFailedError: expected: <200> but was: <401>
    ```

- **Root Cause:** Business logic change, mock expectation mismatch, or broken test assertion.
- **Remediation:**

    ```bash
    ./mvnw test -pl <module> -Dtest="<TestClassName>#<testMethodName>"
    ```

### Pattern 2.3: Testcontainers / Database Connection Timeout

- **Indicator in logs:**

    ```txt
    org.testcontainers.containers.ContainerLaunchException: Timed out waiting for container port to open
    org.postgresql.util.PSQLException: Connection to localhost:... refused.
    ```

- **Root Cause:** Docker daemon unavailable, memory starvation in runner, or missing `@ServiceConnection` / `@Testcontainers(disabledWithoutDocker = true)`.
- **Remediation:**
    1. Ensure test class extends `AbstractIntegrationTest`.
    2. Check Docker availability on runner or local machine.
    3. Verify port mapping and environment variables in `@DynamicPropertySource`.

---

## 3. Codecov Coverage Regressions (`codecov/patch`)

### Pattern 3.1: Patch Coverage Dropped Below Threshold

- **Indicator in PR check status:**
    - Status: `codecov/patch — 68.20% of diff hit (target: 80.00%)`
    - Conclusion: `failure`
- **Indicator in PR Comments (Codecov Bot):**

    ```markdown
    ## [Codecov](https://app.codecov.io/gh/...) Report

    All modified and coverable lines must be covered by tests.

    > Patch Coverage: 68.20% (Target: 80.00%)
    > | File | Coverage | Uncovered Lines |
    > | auth-service/.../TokenProvider.java | 60.0% | 45-52, 60 |
    ```

- **Root Cause:** New or edited lines in the PR lack corresponding unit/integration test assertions.
- **Remediation:**
    1. Locate the exact uncovered line numbers from the Codecov comment or PR diff.
    2. Add unit tests in `<Module>Test` targeting edge cases / conditional branches (e.g. `if (token.isExpired())`).
    3. Verify coverage locally: `./mvnw test jacoco:report -pl <module>`.

---

## 4. CD Deployment Failures (`cd-staging.yml` / `cd-production.yml`)

### Pattern 4.1: Docker Hub Authentication / Push Failure

- **Indicator in logs:**

    ```txt
    Error: login attempt to https://index.docker.io/v1/ failed with status: 401 Unauthorized
    ERROR: denied: requested access to the resource is denied
    ```

- **Root Cause:** Missing or invalid `DOCKER_USERNAME` / `DOCKER_PASSWORD` repository or environment secrets.
- **Remediation:**
    - Check GitHub Repository Secrets -> `DOCKER_USERNAME`, `DOCKER_PASSWORD`.

### Pattern 4.2: SSH Connection / Host Key Verification Failure

- **Indicator in logs:**

    ```txt
    Host key verification failed.
    ssh: connect to host ... port 22: Connection timed out
    ::error::SSH_HOST or SSH_PRIVATE_KEY is not defined in Staging Environment Secrets.
    ```

- **Root Cause:** Staging server unreachable, IP firewall blocking GitHub Actions IP ranges, or missing SSH secrets.
- **Remediation:**
    - Verify Staging Environment secrets (`SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY`, `SSH_PORT`).

### Pattern 4.3: Docker Compose Remote Service Startup Failure

- **Indicator in logs:**

    ```txt
    service "auth-service" failed to start: container ... exited with code 1
    Caused by: org.postgresql.util.PSQLException: password authentication failed for user "postgres"
    ```

- **Root Cause:** Database password mismatch between `docker-compose.staging.yml` and `.env`, or multi-database initialization script did not execute.
- **Remediation:**
    - Inspect remote logs via SSH: `ssh <user>@<host> "cd ~/shareride-staging && docker compose -f docker-compose.staging.yml logs auth-service"`.
