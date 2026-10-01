# Teamc_Isow

## 技術スタック

- バックエンド: Java 21 / Spring Boot 4.1.1 / Spring Data JPA / Spring Security
- フロントエンド: React + TypeScript（Vite）
- データベース: PostgreSQL 16（docker-compose で起動）

## 必要なツール・バージョン

| ツール | バージョン | 備考 |
| --- | --- | --- |
| Java (JDK) | 21 | Temurin 推奨 |
| Node.js | 24系 | 20以上であれば動作見込み |
| npm | 11系 | Node.js に同梱 |
| Docker / Docker Compose | Docker 28系 / Compose v2 | PostgreSQL をローカルで起動するために使用 |

Maven はインストール不要です（`backend/mvnw` に Maven Wrapper を同梱しています）。

## セットアップ

### 1. 環境変数の設定

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env
```

必要に応じて値を変更してください（ローカル開発では、`JWT_SECRET` 以外はデフォルト値のままで動作します）。

`JWT_SECRET` は空のままだとバックエンドが起動しません。以下のコマンドで生成した値を `.env` の `JWT_SECRET=` の後ろに設定してください（値は各自で生成し、共有・コミットしないでください）。

```bash
# Git Bash / macOS / Linux
openssl rand -base64 48
```

```powershell
# PowerShell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

> **ポートが競合する場合**
> `5432`（PostgreSQL）や `8080`（backend）を他のアプリ・コンテナが既に使用している場合、起動時にエラーになります。その場合は以下を変更してください（`.env.example` などコミット対象のファイルは変更不要です）。
> 1. `.env` の `DB_PORT` を空いているポート（例: `5433`）に変更
> 2. `.env` の `SERVER_PORT` を空いているポート（例: `8081`）に変更
> 3. `frontend/.env` の `VITE_API_BASE_URL` を変更後の backend ポートに合わせる（例: `http://localhost:8081`）
>
> 何が該当ポートを使用しているか分からない場合は `docker ps` や `netstat -ano | findstr <ポート番号>` で確認できます。

### 2. PostgreSQL の起動（Docker）

```bash
docker compose up -d
```

### 3. バックエンドの起動

```bash
cd backend
./mvnw spring-boot:run
```

- Windows (コマンドプロンプト/PowerShell) では `mvnw.cmd spring-boot:run` を使用してください。
- ルートの `.env` は起動時に自動で読み込まれます（`backend/` から起動してください）。
- 起動後、`http://localhost:8080/api/health` にアクセスすると `{"status":"ok"}` が返ります。

### 4. フロントエンドの起動

```bash
cd frontend
npm install
npm run dev
```

- `http://localhost:5173` を開くと、バックエンドの `/api/health` を呼び出した結果が画面に表示されます。

## 環境変数一覧

### ルート `.env`（docker-compose / backend）

| 変数名 | 説明 | デフォルト値 |
| --- | --- | --- |
| `DB_HOST` | PostgreSQL ホスト（backend用） | `localhost` |
| `DB_PORT` | PostgreSQL ポート | `5432` |
| `DB_NAME` | DB名 | `teamc_isow` |
| `DB_USER` | DBユーザー | `teamc` |
| `DB_PASSWORD` | DBパスワード | `teamc_password` |
| `SERVER_PORT` | backend の待受ポート | `8080` |
| `FRONTEND_ORIGIN` | CORS許可オリジン（frontend の URL） | `http://localhost:5173` |
| `JWT_SECRET` | JWT の署名用秘密鍵。32バイト以上のランダム値を Base64 で指定（生成方法は「1. 環境変数の設定」参照） | なし（必須） |

### `frontend/.env`

| 変数名 | 説明 | デフォルト値 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | backend APIのベースURL | `http://localhost:8080` |

## ディレクトリ構成

- `backend/` : Spring Boot（API）
- `frontend/` : React（画面）
- `docs/` : 機能ごとの仕様書
- `design/` : 画面の完成例画像
- `docker-compose.yml` : ローカル用 PostgreSQL

## 現段階でやっていないこと

- 認証・投稿・一覧・通知などの機能
- テーブル設計（エンティティ・マイグレーション）
