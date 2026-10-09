## 技術スタック

- バックエンド: Java 21 / Spring Boot 4.1.1 / Spring Data JPA / Spring Security
  （Spring Initializr が Spring Boot 3系の生成に対応しなくなったため 4.1.1 を採用）
- フロントエンド: React + TypeScript（Vite）
- データベース: PostgreSQL 16（docker-compose で起動）

## ディレクトリ構成

- backend/ : Spring Boot
- frontend/ : React
- docs/ : 機能ごとの仕様書
- design/ : 画面の完成例画像

## ルール

- 接続情報やキーは環境変数で管理し、コードに直接書かない
- 画像はDBに保存せず、保存先のURLのみを保持する
- テーブル設計は docs/ の仕様をもとに作成する
- 開発中は spring.jpa.hibernate.ddl-auto=update を使用する（本番では使用しない）
- エンティティを変更してDBの状態がおかしくなった場合は、
  docker compose down -v でDBを作り直す（ローカルのデータは消える）
- dangerouslySetInnerHTML は使用しない。
  認証トークンを localStorage に保存しているため、XSS が致命的になる
- アップロードされた画像は保存前にメタデータ（位置情報・撮影日時・端末情報など）を削除する。
  向きの情報はピクセルを回転させてから削除する。ICC プロファイルは色が変わるため残す
  （詳細は docs/post.md「写真のメタデータ（位置情報など）の削除」）
- 画像の保存処理を追加・変更する場合は、必ず ImageUploadService.prepare() を通すこと
- 管理者向けの API は必ず /api/admin/** に置く。権限のチェックは必ずサーバー側で行い、
  画面を隠すことを守りにしない（/api/admin/** 全体は SecurityConfig で管理者だけに許可し、
  サービスでも AdminAccess.requireAdmin() で確かめる。管理者でなければ存在しない URL と同じ 404 を返す）。
  データを変える管理操作は、同じトランザクションで AdminOperationLogger からログに残す
  （詳細は docs/admin.md「権限の仕組み」「管理操作のログ」）
