# Teamc_Isow

> ⚠️ **開発用のテストデータ（`DEV_SEED_ENABLED`）について：このテストデータは開発専用です。公開するサーバーには絶対に投入しないでください。パスワードがREADMEに書かれているため、誰でもログインできるアカウントになります。**（詳しくは下記「開発用のテストデータ」）

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

- `http://localhost:5173/health` を開くと、バックエンドの `/api/health` を呼び出した結果が画面に表示されます（疎通確認用）。
- `http://localhost:5173/register` で新規会員登録、`http://localhost:5173/login` でログインができます。
- 管理画面（`http://localhost:5173/admin`）を使う場合は、下記「管理者の用意（管理画面）」を参照してください。

### テスト

```bash
# バックエンド
cd backend
./mvnw test

# フロントエンド（画面の部品のテスト。Vitest + Testing Library で、ブラウザの代わりに jsdom で描画する）
cd frontend
npm test
```

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
| `IMAGE_STORAGE` | 画像の保存先。`local` はサーバーのローカルフォルダ（開発用）。公開時はクラウドストレージ（R2 など）の実装を追加して切り替える | `local` |
| `IMAGE_LOCAL_DIR` | `local` の場合の保存先フォルダ（`backend/` からの相対パス。`backend/uploads/` は .gitignore 済み） | `./uploads` |
| `IMAGE_PUBLIC_BASE_URL` | `local` の場合に、ブラウザから画像を表示する URL の先頭部分（`/uploads/**` で配信） | `http://localhost:<SERVER_PORT>/uploads` |
| `DM_MAX_RECEIVED_ACTIVE_CONVERSATIONS` | 1人が受けられる「進行中」の相談の上限（自分から申し込んだ会話と、申請中の会話は数えない） | `3` |
| `ADMIN_EMAIL` | 管理者にするユーザーのメールアドレス。起動時に、このメールアドレスの登録済みユーザーを管理者にする（下記「管理者の用意（管理画面）」） | なし（空なら何もしない） |
| `DEV_SEED_ENABLED` | `true` のときだけ、起動時に開発用のテストデータを投入する（下記「開発用のテストデータ」）。**公開するサーバーでは絶対に `true` にしない** | なし（投入しない） |

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

## マスタデータ（選択肢）の追加

ファッションの種類・骨格タイプ・パーソナルカラー・公式タグの初期データは、バックエンドの起動時に自動で登録されます。

- 定義: `backend/src/main/java/com/teamc/isow/backend/seed/MasterSeedData.java`
- 登録処理: `backend/src/main/java/com/teamc/isow/backend/seed/MasterDataSeeder.java`

投入済みのものは `master_seed_history` テーブルに「種類:名前」（例: `fashion_category:きれいめ`）で記録され、再起動しても二重に登録されません。既存の行は更新しないため、DB 上で名前を直したり無効化（`is_active = false`）したりしても、起動のたびに元へ戻ることはありません。

### 選択肢を追加する手順

1. `MasterSeedData.java` の該当する一覧の**末尾**に1行追加する

   ```java
   new Item("フェミニン", 50),
   ```

   - `displayOrder`（2つ目の値）は画面での並び順です。小さいほど先に表示されます。初期データは 10 ずつ空けているので、既存の選択肢の間に入れたい場合は `15` のような値を使えます
2. バックエンドを再起動する
3. ログに `初期データを登録しました: fashion_category 1件` のように出れば完了

> **注意**
> - **一覧に追加済みの行の名前は書き換えない・消さないでください。** 名前が投入済みかどうかの判定キーになっているため、書き換えると「新しい選択肢」とみなされ、別の行として追加されてしまいます
> - 既に登録済みの選択肢の名前や並び順を変えたい場合は、`MasterSeedData.java` ではなく DB の行を直接更新してください（管理画面では、名前・並び順の変更はできません）
> - 選択肢をやめる場合も、行は削除せず無効にしてください（投稿・プロフィールから参照されているため）。管理画面の「マスタ管理」（`/admin/masters`）から無効化・有効に戻すことができます
> - 選択肢の追加は、管理画面の「マスタ管理」からもできます（DB に行が追加されるだけで、`MasterSeedData.java` には反映されません。チーム全員の初期データにしたいものは `MasterSeedData.java` に追加してください）
> - 一覧に追加したものと同じ名前の行が既に DB にある場合（手動で登録済みなど）は、行は追加せず履歴だけ記録します。タグは表記ゆれ（大文字小文字・全角半角など）も同じ名前とみなします

## 管理者の用意（管理画面）

管理画面（`http://localhost:5173/admin`）は管理者だけが開けます。管理者でないユーザーには「ページが見つかりません」と表示されます（詳しくは `docs/admin.md`）。管理者は画面から作れないため、各自の開発環境で次の手順で用意してください。

1. 通常の新規会員登録（`http://localhost:5173/register`）で、管理者にするアカウントを作る
2. ルートの `.env` の `ADMIN_EMAIL=` の後ろに、そのアカウントのメールアドレスを書く

   ```
   ADMIN_EMAIL=you@example.com
   ```

   - 自分のメールアドレスは `.env` にだけ書き、`.env.example` など、コミットするファイルには書かないでください
3. バックエンドを再起動する
4. ログに `ADMIN_EMAIL のユーザーを管理者にしました: id=…, username=…` と出れば完了。`http://localhost:5173/admin` を開くと管理画面が出ます

- `ADMIN_EMAIL` のユーザーがまだいない場合は、ログに `ADMIN_EMAIL のユーザーが見つからないため、管理者にしませんでした` と出ます。先に新規会員登録をしてから再起動してください
- `ADMIN_EMAIL` を書き換えたり空にしたりしても、前に管理者にしたユーザーは管理者のままです
- 管理者にしたことは、管理画面の「操作ログ」に「システム」の操作として残ります

> **`feature/admin` を取り込んだ後は、DB を作り直してください**
>
> 管理者の権限のため `users` テーブルに列（`role`）を足しました。既存の DB のままだと、既存のユーザーの `role` が空（NULL）のまま残ることがあるため、取り込んだ後に一度、次のコマンドで DB を作り直してください。
>
> ```bash
> docker compose down -v
> docker compose up -d
> ```
>
> - **ローカルの DB のデータ（ユーザー・投稿・DM など）はすべて消えます。** 作り直した後、新規会員登録からやり直し、上の手順で管理者を用意してください
> - 選択肢の初期データは、バックエンドの起動時に自動で登録し直されます
> - アップロードした写真のファイル（`backend/uploads/`）は消えません（DB から参照されなくなるだけです）

## 開発用のテストデータ

> ⚠️ **このテストデータは開発専用です。公開するサーバーには絶対に投入しないでください。パスワードがREADMEに書かれているため、誰でもログインできるアカウントになります。**

画面の確認用に、ユーザー・投稿・いいね・フォロー・相談（DM）・通知・検索履歴をまとめて作ります。既定では動きません。

### 使い方

1. ルートの `.env` に次の1行を書く（`true` 以外の値・空のときは何もしません）

   ```
   DEV_SEED_ENABLED=true
   ```

2. バックエンドを起動（再起動）する
3. ログに `開発用のテストデータを投入しました（ユーザー8人・投稿10件）` と出れば完了

- 投入済みなら、2回目以降の起動では何もしません（ログに `投入済みのため、何もしません` と出ます）。`DEV_SEED_ENABLED=true` のままでも重複しません
- 投入後は `DEV_SEED_ENABLED=` を空に戻しておくことをおすすめします
- 既存のデータを消したり書き換えたりはしません。追加だけです
- **データを消す機能はありません。** やり直したいときは、`docker compose down -v` で DB ごと作り直してから起動してください（ローカルのデータはすべて消えます）
- ログに `投入済みの印がないのに、投入用のユーザーがすでにいるため、何もしません` と出た場合は、前回の投入が途中で失敗したか、同じメールアドレス・ユーザー名のアカウントがすでにあります。`docker compose down -v` で DB を作り直してから起動してください
- 写真は起動時にその場で作ります（色と文字だけの画像）。`backend/uploads/` に保存されます（.gitignore 済み）

### アカウント

**パスワードは全員共通で `isho1234` です。** メールアドレスは `ユーザー名@example.com` です。

| ユーザー名（メールアドレス） | 表示名 | 性別・年代 | 骨格・パーソナルカラー | 身長 | 画像 | 確かめられること |
| --- | --- | --- | --- | --- | --- | --- |
| `dev_aoi`（`dev_aoi@example.com`） | あおい | 女性・20代前半 | ストレート・イエベ春 | 158 | あり | **確認の中心。** 通知（既読・未読）・相談の全状態・検索履歴がそろう |
| `dev_ren`（`dev_ren@example.com`） | れん | 男性・20代後半 | ナチュラル・ブルベ冬 | 178 | あり | aoi と相談中（未読のメッセージあり）。**受けている相談が上限**（下記） |
| `dev_mio`（`dev_mio@example.com`） | みお | 女性・10代 | ウェーブ・ブルベ夏 | なし | なし | 投稿説明が長い投稿・aoi との終了した相談 |
| `dev_sora`（`dev_sora@example.com`） | そら | その他・30代前半 | ナチュラル・イエベ秋 | 165 | なし | aoi から申請が届いている |
| `dev_kenta`（`dev_kenta@example.com`） | けんた | 男性・40代 | ストレート・イエベ秋 | 170 | あり | aoi に相談を拒否された（24時間は再申請できない） |
| `dev_yui`（`dev_yui@example.com`） | ゆい | 女性・30代後半 | ウェーブ・イエベ春 | なし | あり | 写真10枚の投稿・aoi に申請中 |
| `dev_haru`（`dev_haru@example.com`） | はる | 男性・50代以上 | 未設定 | なし | なし | プロフィールが一部だけ設定済み |
| `dev_new`（`dev_new@example.com`） | （未設定） | 未設定 | 未設定 | なし | なし | 登録しただけの状態（投稿・フォローなし） |

> **`dev_ren` は、受けている相談が上限に達しているため、新しい相談を承認できません。これは上限の画面を確かめるための意図した状態です（バグではありません）。**
> ほかのユーザーで `dev_ren` のプロフィールを開くと「現在、新しい相談を受け付けていません。」と表示されます。

### 作られるデータ

- 投稿10件
  - 写真は1枚（縦長・横長・正方形）・2〜5枚・10枚（上限）があり、枚数の表示と ‹ › を確かめられます
  - 着用アイテム・参考情報が空のものと入っているもの（空の項目は行ごと表示されません）
  - 投稿説明が長いもの（`dev_mio` の「低身長でもバランスよく」。約1500文字）
  - 公式タグと手入力のタグ。`dev_ren` の「スニーカーが主役」には `＃Ｙ２Ｋ` と入力したタグが付き、本番と同じ処理で「Y2K」になります
- いいね14件・お気に入り5件・フォロー10件（`dev_aoi` と `dev_ren`・`dev_mio` は相互フォロー）
- 相談（DM）
  - 進行中：`dev_aoi` → `dev_ren`（5通。画像付き1通。最後の `dev_ren` のメッセージは `dev_aoi` が未読）、`dev_kenta` → `dev_ren`、`dev_yui` → `dev_ren`
  - 終了：`dev_mio` → `dev_aoi`（3通）
  - 申請中：`dev_yui` → `dev_aoi`（`dev_aoi` が受けた側）、`dev_aoi` → `dev_sora`（`dev_aoi` が送った側）
  - 拒否：`dev_kenta` → `dev_aoi`
- 通知：上の操作から、本番と同じ処理で作られます（いいね・フォロー・相談・メッセージ）。`dev_aoi` はフォローの通知だけ既読、ほかは未読です
- 検索履歴：`dev_aoi` に5件（古着・韓国ファッション・セットアップ・ワンピース・dev_ren）、`dev_ren` に1件
- 日時はすべて投入した時刻になります（一覧では「たった今」などと表示されます）。並び順は、同じ時刻なら後に作ったものが先になります

### 管理者として使う

この仕組みでは管理者を作りません。投入されたユーザーのメールアドレスを `.env` の `ADMIN_EMAIL` に書けば、新規会員登録なしでそのユーザーを管理者として使えます（上記「管理者の用意（管理画面）」）。

```
DEV_SEED_ENABLED=true
ADMIN_EMAIL=dev_aoi@example.com
```

- 投入の後に管理者の指定が行われるため、両方を書いておけば最初の起動から管理者になります

## 現段階でやっていないこと

- 投稿・一覧・通知などの機能（認証は `feature/auth` で実装中）
- マイグレーション（開発中は `spring.jpa.hibernate.ddl-auto=update` でテーブルを自動作成している）
  - 導入時に行うこと：`users.display_name`（表示名）は、列を追加する前に登録したユーザーが NULL のため、
    `UPDATE users SET display_name = username WHERE display_name IS NULL` で値を埋めてから NOT NULL にする
    （それまでは `User.getDisplayName()` が NULL の場合にユーザ名を返す）
