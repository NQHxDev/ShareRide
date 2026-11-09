# ShareRide Common Messages - 简体中文 (CN)
SUCCESS=操作成功
CREATED=创建成功
ACCEPTED=请求已接受
NO_CONTENT=无内容返回
BAD_REQUEST=无效的请求
UNAUTHORIZED=未授权或会话已过期
PAYMENT_REQUIRED=需要付款才能继续
FORBIDDEN=无权访问该资源
NOT_FOUND=请求的资源不存在
METHOD_NOT_ALLOWED=不支持该HTTP请求方法
CONFLICT=数据冲突或已存在
UNPROCESSABLE_ENTITY=无法处理的实体
RATE_LIMIT_EXCEEDED=已超出请求速率限制，请稍后再试
TOO_MANY_REQUESTS=请求过多，请稍后再试
REQUEST_TIMEOUT=请求超时
VALIDATION_FAILED=输入数据验证失败
INTERNAL_SERVER_ERROR=系统遇到异常，请稍后重试
BAD_GATEWAY=网关错误
SERVICE_UNAVAILABLE=服务暂时不可用
GATEWAY_TIMEOUT=网关超时
CIRCUIT_BREAKER_OPEN=熔断器已开启，请稍后再试

# Parameterized Templates
RESOURCE_NOT_FOUND=未找到${resource!'资源'}，标识符：'${identifier!'未知'}'
FIELD_REQUIRED=字段'${field}'为必填项
FIELD_INVALID=字段'${field}'的值无效：${value!''}
UNSUPPORTED_API_VERSION=不支持的API版本
API_VERSION_NOT_SUPPORTED=不支持的API版本 '${version!"未知"}'。支持的版本: ${supported!""}
API_VERSION_REQUIRED=缺少必需的API版本
API_VERSION_DEPRECATED_WARNING=API版本 '${version!"未知"}' 已弃用，将于 ${sunset!"未知"} 停止支持
