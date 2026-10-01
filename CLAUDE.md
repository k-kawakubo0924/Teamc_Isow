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
