# ShareRide Common Messages - 日本語 (JP)
SUCCESS=成功しました
CREATED=正常に作成されました
ACCEPTED=リクエストを受理しました
NO_CONTENT=コンテンツがありません
BAD_REQUEST=不正なリクエストです
UNAUTHORIZED=認証されていないか、セッションの有効期限が切れています
PAYMENT_REQUIRED=お支払いが必要です
FORBIDDEN=このリソースへのアクセス権限がありません
NOT_FOUND=要求されたリソースが見つかりませんでした
METHOD_NOT_ALLOWED=サポートされていないHTTPメソッドです
CONFLICT=データの競合が発生したか、既に存在します
UNPROCESSABLE_ENTITY=処理不可能なエンティティです
RATE_LIMIT_EXCEEDED=リクエストレート制限を超過しました。しばらく待ってから再試行してください
TOO_MANY_REQUESTS=リクエストが多すぎます。しばらく待ってから再試行してください
REQUEST_TIMEOUT=リクエストがタイムアウトしました
VALIDATION_FAILED=入力データの検証に失敗しました
INTERNAL_SERVER_ERROR=システムエラーが発生しました。しばらくしてからもう一度お試しください
BAD_GATEWAY=不正なゲートウェイです
SERVICE_UNAVAILABLE=サービスは一時的に利用できません
GATEWAY_TIMEOUT=ゲートウェイがタイムアウトしました
CIRCUIT_BREAKER_OPEN=サーキットブレーカーが作動中です。しばらくしてから再試行してください

# Parameterized Templates
RESOURCE_NOT_FOUND=${resource!'リソース'}（ID: '${identifier!'不明'}'）が見つかりません
FIELD_REQUIRED=項目「${field}」は必須です
FIELD_INVALID=項目「${field}」の値が無効です: ${value!''}
UNSUPPORTED_API_VERSION=サポートされていないAPIバージョンです
API_VERSION_NOT_SUPPORTED=APIバージョン '${version!"不明"}' はサポートされていません。サポート対象: ${supported!""}
API_VERSION_REQUIRED=APIバージョンが指定されていません
API_VERSION_DEPRECATED_WARNING=APIバージョン '${version!"不明"}' は非推奨です。提供終了日: ${sunset!"不明"}
