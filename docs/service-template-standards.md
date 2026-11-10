# ShareRide Enterprise Microservice Template Standards

Tài liệu này đóng vai trò là **Hiến chương Kiến trúc chuẩn (Architecture Blueprint & Standards)** cho toàn bộ các microservices được phát triển trong monorepo **ShareRide**.

Mọi service (hiện tại và tương lai) đều **bắt buộc** phải tuân theo đúng 1 khuôn mẫu chuẩn duy nhất được định nghĩa tại module [`service-template`](../service-template). Mọi vi phạm về cấu trúc, phân tầng hay quy ước đặt tên sẽ bị chặn đứng tự động bởi bộ kiểm thử **ArchUnit** và **Spotless / Checkstyle** ngay tại bước CI.

---

## 1. Cấu trúc thư mục & Phân tầng chuẩn (Layered Architecture)

Mỗi microservice nằm tại thư mục gốc của monorepo và có cấu trúc package chuẩn: `com.shareride.<service_name>`.

```text
<service-name>/
├── pom.xml
├── docker-compose.yml                     # Dedicated PostgreSQL instance cho service
├── src/
│   ├── main/
│   │   ├── java/com/shareride/<service_name>/
│   │   │   ├── <Service>ServiceApplication.java   # Spring Boot Main Application Class
│   │   │   ├── client/                           # gRPC / REST Outgoing Clients
│   │   │   │   └── <Service>GrpcClient.java      # gRPC Client Wrapper chuẩn (Channel, Retry, Exception Mapper)
│   │   │   ├── config/                           # Cấu hình Spring (JPA, RabbitMQ, Redis, gRPC)
│   │   │   │   ├── JpaConfig.java                # @EntityScan & @EnableJpaRepositories
│   │   │   │   └── RabbitMqConfig.java           # Topology: Exchange, Resilient Queue, DLX, DLQ
│   │   │   ├── constant/                         # Hằng số kỹ thuật, mã lỗi, thông điệp
│   │   │   │   ├── <Service>Constants.java       # Endpoints, Redis Lock Keys, Routing Keys
│   │   │   │   ├── <Service>ResponseCode.java    # Mã phản hồi nghiệp vụ (BUSINESS CODES)
│   │   │   │   └── <Service>Message.java         # Thông điệp phản hồi chuẩn hóa
│   │   │   ├── consumer/                         # RabbitMQ Message Listeners
│   │   │   │   └── <Service>EventConsumer.java   # @RabbitListener với Idempotent Guard & DLQ
│   │   │   ├── controller/                       # REST API Controllers (Northbound)
│   │   │   │   └── <Service>Controller.java      # @RestController, @ApiVersion("v1"), BaseResponse
│   │   │   ├── domain/                           # JPA Entities & Enums (Domain Model)
│   │   │   │   ├── <Service>Item.java            # Kế thừa SoftDeletableEntity / BaseEntity (UUIDv7)
│   │   │   │   └── <Service>ItemStatus.java      # Enum trạng thái
│   │   │   ├── dto/                              # Data Transfer Objects
│   │   │   │   ├── Create<Service>Request.java   # Request DTO có @Valid validation
│   │   │   │   ├── Update<Service>Request.java
│   │   │   │   └── <Service>Response.java        # Response DTO
│   │   │   ├── publisher/                        # RabbitMQ Event Publishers
│   │   │   │   └── <Service>EventPublisher.java  # Hỗ trợ cả Outbox ACID và Direct Async Broadcast
│   │   │   ├── repository/                       # Spring Data JPA Repositories
│   │   │   │   └── <Service>Repository.java      # Interface kế thừa JpaRepository<Entity, UUID>
│   │   │   └── service/                          # Business Logic Layer (Chuẩn Enterprise)
│   │   │       ├── <Service>Service.java         # Interface nghiệp vụ
│   │   │       └── impl/
│   │   │           └── <Service>ServiceImpl.java # Class triển khai (@Service, @Transactional)
│   │   └── resources/
│   │       ├── application.yml                   # Cấu hình runtime (HikariCP, JPA, Flyway, Redis, RMQ)
│   │       └── db/migration/
│   │           └── V1__init_<service>_schema.sql # Flyway migration (bảng nghiệp vụ + outbox_messages)
│   └── test/
│       ├── java/com/shareride/<service_name>/
│       │   ├── <Service>ArchitectureTest.java    # ArchUnit tự động kiểm tra quy chuẩn kiến trúc
│       │   ├── <Service>ControllerTest.java      # WebMvcTest (Unit test Controller & Validation)
│       │   ├── <Service>ServiceTest.java         # Mockito Unit test Service logic
│       │   ├── <Service>MessagingTest.java       # Unit test RabbitMQ Publisher & Consumer
│       │   ├── <Service>GrpcClientTest.java      # Unit test gRPC Client wrapper
│       │   ├── <Service>PersistenceIntegrationTest.java # Testcontainers PostgreSQL + Valkey
│       │   └── support/
│       │       └── AbstractIntegrationTest.java  # Lớp cơ sở Testcontainers (@ServiceConnection)
│       └── resources/
│           ├── application.yml                   # H2 In-Memory config cho test nhanh
│           └── mockito-extensions/
│               └── org.mockito.plugins.MockMaker # mock-maker-subclass cho Java 21
```

---

## 2. Các quy chuẩn kiến trúc cốt lõi

### 2.1. Tầng Service: Bắt buộc Interface + Implementation
* Interface đặt trực tiếp tại `com.shareride.<service_name>.service.<Service>Service`.
* Implementation đặt tại `com.shareride.<service_name>.service.impl.<Service>ServiceImpl`.
* Implementation được đánh dấu `@Service`, quản lý transaction bằng `@Transactional`, và ghi log nghiệp vụ với `@Slf4j`.

### 2.2. Tầng Controller & API Response
* Toàn bộ Controller đặt tại `com.shareride.<service_name>.controller`, có hậu tố `*Controller` và gắn `@RestController`.
* Bắt buộc có versioning rõ ràng bằng `@ApiVersion("v1")`.
* **Không được phép trả về raw entity hoặc raw DTO**. Mọi endpoint trả về đối tượng `ResponseEntity<BaseResponse<T>>`.
* Request DTO được kiểm tra hợp lệ bằng `@Valid` và các annotation của Jakarta Validation (`@NotBlank`, `@Size`, v.v.).
* **Tuyệt đối không được tiêm (inject) Repository vào Controller**. Mọi truy xuất dữ liệu bắt buộc đi qua Service.

### 2.3. Tầng Domain & Dữ liệu
* Toàn bộ Entity phải nằm trong package `domain` và kế thừa từ [`BaseEntity`](../common/src/main/java/com/shareride/common/persistence/BaseEntity.java) hoặc [`SoftDeletableEntity`](../common/src/main/java/com/shareride/common/persistence/SoftDeletableEntity.java).
* **Khóa chính**: Bắt buộc là `UUID` chuẩn **UUIDv7** (Time-ordered RFC 9562) được khởi tạo tự động ở `@PrePersist`.
* **Khóa lạc quan (Optimistic Locking)**: Được quản lý tự động qua cột `@Version Long version` trong `BaseEntity`.
* **Database Isolation**: Mỗi service sở hữu cơ sở dữ liệu PostgreSQL riêng biệt, quản lý schema bằng **Flyway** (`V1__init_...sql`).
* **Transactional Outbox**: Mỗi database của service có bảng `outbox_messages` để lưu trữ bản tin outbox trong cùng transaction ACID với entity.

---

## 3. Quy chuẩn Giao tiếp Liên Dịch vụ (Inter-service Communication)

### 3.1. Bất đồng bộ qua RabbitMQ (Event-Driven)
Module template cung cấp sẵn đầy đủ 2 thành phần:
1. **[`TemplateEventPublisher`](../service-template/src/main/java/com/shareride/template/publisher/TemplateEventPublisher.java)**:
   * **Transactional Outbox (`recordEvent`)**: Đảm bảo an toàn 100% dữ liệu (Dual-write guarantee) khi cần publish sự kiện gắn liền với thao tác DB.
   * **Direct Async Publishing (`publishAsync`)**: Xuất bản trực tiếp non-blocking qua `RabbitMqEventPublisher` kèm bọc trace context tự động vào [`ShareRideEvent<T>`](../common/src/main/java/com/shareride/common/communication/rabbitmq/event/ShareRideEvent.java).
2. **[`TemplateEventConsumer`](../service-template/src/main/java/com/shareride/template/consumer/TemplateEventConsumer.java)**:
   * Tiêu thụ sự kiện với `@RabbitListener`.
   * **Idempotency Guard**: Sử dụng `IdempotentMessageService.isProcessed(eventId)` và `markAsProcessed(eventId)` lưu trên Redis/Valkey để chống xử lý trùng lặp.
   * **Dead-Letter Queue (DLQ)**: Khi xử lý thất bại, message tự động được RabbitMQ định tuyến về DLX/DLQ đã cấu hình trong `RabbitMqConfig`.

### 3.2. Đồng bộ qua gRPC Client
Module template cung cấp sẵn **[`TemplateGrpcClient`](../service-template/src/main/java/com/shareride/template/client/TemplateGrpcClient.java)**:
* Quản lý kết nối `Channel` tự động qua `GrpcChannelFactory` với đầy đủ interceptor (Client Tracing, Deadline Timeout, Logging).
* Tự động bắt lỗi `StatusRuntimeException` và chuyển đổi thành ngoại lệ chuẩn [`BaseException`](../common/src/main/java/com/shareride/common/exception/BaseException.java) của ShareRide qua `GrpcStatusExceptionMapper`.
* Cung cấp các phương thức tiện ích gọi sẵn:
  * `executeCall(targetServiceName, stubFunc)`: Gọi đồng bộ kèm xử lý ngoại lệ tự động.
  * `executeResilientCall(targetServiceName, operationName, stubFunc)`: Gọi có bọc Circuit Breaker, Bulkhead và Exponential Retry.
  * `executeAsyncCall(targetServiceName, stubFunc)`: Gọi bất đồng bộ trả về `CompletableFuture<R>`.

---

## 4. Tự động Cưỡng chế Kiến trúc với ArchUnit

Trong mỗi service, class `<Service>ArchitectureTest` chạy tự động khi thực hiện `mvn test` để kiểm soát các luật:

| Luật kiến trúc (ArchRule) | Hành vi bị từ chối (Build Failed) |
| :--- | :--- |
| `controllers_should_not_depend_on_repositories` | Controller inject hoặc gọi trực tiếp Repository |
| `controllers_naming_and_annotation` | Class trong `controller` không kết thúc bằng `Controller` hoặc thiếu `@RestController` |
| `service_interfaces_naming` | Interface trong `service` không kết thúc bằng `Service` |
| `service_implementations_naming_and_annotation` | Class trong `service.impl` không kết thúc bằng `ServiceImpl` hoặc thiếu `@Service` |
| `repository_naming_and_annotation` | Interface trong `repository` không kết thúc bằng `Repository` hoặc thiếu `@Repository` |
| `entities_must_extend_base_entity` | Entity trong `domain` không kế thừa `BaseEntity` hoặc `SoftDeletableEntity` |

---

## 5. Khởi tạo Service Mới Tự Động (CLI Scaffolding Tool)

Để khởi tạo một microservice mới chuẩn 100% chỉ trong **5 giây**, sử dụng lệnh `make new-service`:

```bash
# Cú pháp
make new-service NAME=<service-name> PORT=<http-port> [GRPC_PORT=<port>] [DB_PORT=<port>]

# Ví dụ tạo ride-service
make new-service NAME=ride-service PORT=8082

# Hoặc dùng trực tiếp script
./scripts/scaffold-service.sh billing-service 8084 9094 5434
```

Lệnh trên sẽ tự động:
1. Sao chép toàn bộ khung chuẩn từ `service-template`.
2. Tái cấu trúc package (`com.shareride.<name>`) và đổi tên toàn bộ file Java, Test, Migration SQL.
3. Thay thế hằng số, endpoint, port, tên database PostgreSQL.
4. Tự động thêm `<module><service-name></module>` vào root `pom.xml`.
5. Tự động cấu hình `docker-compose.yml` riêng biệt cho service với container `postgres-<name>` độc lập.
6. Chạy `mvn spotless:apply` để định dạng chuẩn Google Java Format.

