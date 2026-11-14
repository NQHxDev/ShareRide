# ShareRide Testing & CI Strategy

## 1. Giới thiệu & Triết lý kiểm thử

Trong kiến trúc Microservices Monorepo như ShareRide, khi số lượng services tăng lên (10, 20, 30+ services), việc khởi tạo hạ tầng dùng chung (PostgreSQL, Valkey, Kafka...) trực tiếp trong file CI/CD workflow (`ci.yml` `services:`) là một **Anti-pattern** gây ra:

- Quá tải RAM/CPU trên GitHub Runner dẫn đến Out-Of-Memory (OOM).
- Xung đột port giữa các instance database của các service khác nhau.
- Coupling giữa các microservices (service A gãy do DB của service B không khởi động kịp).

Để giải quyết triệt để vấn đề này, ShareRide áp dụng **Chiến lược kiểm thử độc lập dựa trên Testcontainers**.

---

## 2. Kim tự tháp kiểm thử (Test Pyramid)

```Pyramid
        / \
       /   \
      / E2E \          (5% - Contract Testing / Staging smoke test)
     /-------\
    / Integr  \        (15% - Testcontainers: PostgreSQL 17 + Valkey)
   /-----------\
  /  Unit Test  \      (80% - Mockito / H2 In-Memory, cực nhanh)
 /---------------\
```

1. **Unit Tests (80%)**:
    - Sử dụng Mockito (`@ExtendWith(MockitoExtension.class)`).
    - Kiểm thử logic nghiệp vụ thuần túy, không bật Spring Context, chạy xong trong vài mili-giây.
2. **Integration Tests (15%)**:
    - Tên class kết thúc bằng `*IntegrationTest`.
    - Sử dụng **Testcontainers** để tự động khởi tạo PostgreSQL 17 và Valkey (`valkey/valkey-extension:latest`) trên Docker.
    - Khi test xong, container tự động hủy, port ngẫu nhiên được dọn dẹp sạch sẽ.
3. **E2E / Contract Tests (5%)**:
    - Kiểm thử luồng tích hợp giữa các microservices qua Contract Testing (Pact).

---

## 3. Kiến trúc Testcontainers trong ShareRide

### 3.1. Cấu hình Maven Dependencies

Các dependency của Testcontainers được quản lý tập trung ở `pom.xml` gốc và thừa hưởng từ Spring Boot:

```xml
<!-- Trong pom.xml của từng microservice -->
<dependency>
   <groupId>org.springframework.boot</groupId>
   <artifactId>spring-boot-testcontainers</artifactId>
   <scope>test</scope>
</dependency>
<dependency>
   <groupId>org.testcontainers</groupId>
   <artifactId>testcontainers-junit-jupiter</artifactId>
   <scope>test</scope>
</dependency>
<dependency>
   <groupId>org.testcontainers</groupId>
   <artifactId>testcontainers-postgresql</artifactId>
   <scope>test</scope>
</dependency>
```

### 3.2. Lớp cơ sở `AbstractIntegrationTest`

Mỗi service cung cấp một lớp test cơ sở (`AbstractIntegrationTest`), ví dụ tại `auth-service/src/test/java/com/shareride/auth/support/AbstractIntegrationTest.java`:

```java
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container @ServiceConnection
    protected static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Container
    protected static final GenericContainer<?> valkey =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey-extension:latest"));

    static {
        postgres.withDatabaseName("shareride_auth");
        postgres.withUsername("shareride_auth_usr");
        postgres.withPassword("auth_secret_password");

        valkey.withExposedPorts(6379);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (!valkey.isRunning()) {
            try {
                if (DockerClientFactory.instance().isDockerAvailable()) {
                    valkey.start();
                }
            } catch (Throwable ignored) {
                // Docker unavailable
            }
        }

        if (valkey.isRunning()) {
            registry.add("spring.data.redis.host", valkey::getHost);
            registry.add("spring.data.redis.port", valkey::getFirstMappedPort);
            registry.add("shareride.cache.shared.host", valkey::getHost);
            registry.add("shareride.cache.shared.port", valkey::getFirstMappedPort);
            registry.add("shareride.cache.enabled", () -> "true");
            registry.add("shareride.redis.shared.host", valkey::getHost);
            registry.add("shareride.redis.shared.port", valkey::getFirstMappedPort);
            registry.add("shareride.redis.enabled", () -> "true");
        }
    }
}
```

- `@ServiceConnection`: Spring Boot tự động bind JDBC URL, user, password vào DataSource.
- `@Testcontainers(disabledWithoutDocker = true)`: Nếu máy dev chưa bật Docker, test sẽ không bị crash mà bỏ qua hoặc fallback.

---

## 4. GitHub Actions CI Workflow

Sau khi chuyển sang Testcontainers, job `integration-test` trong file [.github/workflows/ci.yml](file:///Users/anhjkr/Developer/ShareRide/.github/workflows/ci.yml) trở nên cực kỳ tinh gọn:

```yaml
integration-test:
    name: Integration Tests
    needs: code-quality
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
        - name: Checkout repository
          uses: actions/checkout@v7

        - name: Setup Java and Maven Cache
          uses: ./.github/actions/setup-project

        - name: Run Integration Tests
          run: ./mvnw test -Dtest="*IntegrationTest" --batch-mode
```

👉 **Không cần khai báo bất kỳ `services:` nào.** Dù hệ thống có mở rộng lên 50 microservices, file CI vẫn giữ nguyên cấu trúc gọn gàng này.
