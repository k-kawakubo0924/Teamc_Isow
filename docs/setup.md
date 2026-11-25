# 環境構築の手順（開発環境）

リポジトリを clone してから、自分の PC で画面を開き、テストを流すまでの手順。何も知らない状態から、この文書だけで進められることを目指す。

## 0. この文書について

- 対象：自分の PC で動かす開発環境。**公開（デプロイ）は対象外**（公開するときにやることは `deploy.md`。今は公開しない）
- 環境変数の一つひとつの意味は README「環境変数一覧」、テストデータのアカウントは README「開発用のテストデータ」を見る

### 手順を確かめた記録

この手順を、実際にどの PC でなぞって確かめたかを書く。**別の PC で確かめたら、1行足すこと**（「誰の PC でも同じように動く」は、この表の行が増えることでしか示せない）。

| 誰が | どの OS で | いつ | 確かめた範囲 | 結果 |
| --- | --- | --- | --- | --- |
| Claude Code（チームのメンバーの PC で） | Windows 11 Pro（10.0.26200）、Git Bash と PowerShell | 2026-10-29〜30 | 各手順の部品（版の固定・起動・画面で写真が出る・テスト）。**clone から通しては確かめていない**（すでに環境がある PC で確かめた） | 動いた |

- まだ Windows のこの PC だけ。**macOS・Linux では確かめていない**（コマンドは書いてあるが、動くことは確かめていない）
- いちばん確かなのは、チームのほかの人が、何も知らない状態でこの手順をなぞり、詰まったところを直すこと

### 前提：PC のタイムゾーンは日本時間

- このアプリは、日時を **PC（サーバー）の時計の時間帯で、日本時間として** 保存する（`admin.md`「時刻の扱い」）。日本の PC では何もしなくてよい
- **日本時間以外に設定した PC では、保存・表示される日時が9時間ずれる**（エラーにはならず、静かにずれる。利用停止の期限・相談の自動終了などの判定もずれる）
- 確かめ方：Windows は `tzutil /g` が `Tokyo Standard Time`、macOS・Linux は `date +%Z` が `JST`

### 版の揺れは、手順書の注意ではなく設定で止めている

注意書きは読まれないことがある。直せば読まなくても詰まらない。そのため、版が合わないと詰まるものは、できるだけ設定で固定し、合わなければはっきり止まるようにした（下記「8. 付録」）。

## 1. 必要なもの

| ツール | 必要な版 | 確かめた版 | 確かめるコマンド | 合わないとき |
| --- | --- | --- | --- | --- |
| Git | 特になし | 2.x | `git --version` | — |
| JDK（Java） | **21 以上**（Temurin 21 推奨） | Temurin 21.0.5、Oracle JDK 23 | `java -version` | 21 より古いと、ビルドの最初に止まる（`pom.xml` の enforcer。メッセージの先頭は英語で「JDK 21 or later is required」） |
| Node.js | **22.22.2 以上の 22系、24.15.0 以上の 24系、または 26 以上** | 24.16.0（`frontend/.nvmrc`） | `node -v` | `npm ci` が `EBADENGINE` で止まる（必要な版と今の版が表示される）。**20 では動かない** |
| npm | Node.js に付いてくるもの（11系） | 11.13.0 | `npm -v` | — |
| Docker Desktop（Docker・Docker Compose） | Docker 28系・Compose v2 | Docker 28.5.1・Compose v2.40.0 | `docker version`・`docker compose version` | — |

- Maven は入れなくてよい（`backend/mvnw` が、決まった版（3.9.16）を初回に自動でダウンロードする）
- **初回は、Maven とライブラリのダウンロードで数分かかり、インターネットにつながっている必要がある**
- Node.js の版を切り替える道具を使う場合：nvm（macOS・Linux）は `frontend/` で `nvm use` とすれば `.nvmrc` を読む。nvm-windows は `.nvmrc` を読まないため、`nvm install 24.16.0` → `nvm use 24.16.0` と版を指定する
- JDK が複数入っている場合、`mvnw` は `JAVA_HOME` の JDK（なければ PATH の `java`）を使う

## 2. 取得

```bash
git clone -b develop https://github.com/k-kawakubo0924/Teamc_Isow.git
cd Teamc_Isow
```

- **`-b develop` を付ける**。GitHub の既定のブランチ（`main`）は、開発の最新（`develop`）より古い

## 3. 設定

### 3-1. `.env` を2つ作る

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env
```

- PowerShell では `Copy-Item .env.example .env`・`Copy-Item frontend/.env.example frontend/.env`
- `.env` はコミットしない（`.gitignore` 済み）

### 3-2. `JWT_SECRET` を作る（必須）

空のままだとバックエンドが起動しない。次のどちらかで作った値を、`.env` の `JWT_SECRET=` の後ろに書く。値は各自で作り、共有・コミットしない。

```bash
# Git Bash / macOS / Linux
openssl rand -base64 48
```

```powershell
# PowerShell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

### 3-3. テストデータを入れる（最初はおすすめ）

`.env` の `DEV_SEED_ENABLED=` を `DEV_SEED_ENABLED=true` にすると、起動したときに、ユーザー・投稿（写真つき）・DM などが作られる（README「開発用のテストデータ」）。画面がちゃんと動いているかを、すぐに確かめられる。

### 3-4. ポートを決める

既定のポートは次のとおり。ほかのプロジェクト・アプリが使っていると、起動のときにぶつかる。

| 使うもの | 既定 | ぶつかったときに変える場所 |
| --- | --- | --- |
| PostgreSQL | 5432 | `.env` の `DB_PORT` |
| バックエンド | 8080 | `.env` の `SERVER_PORT` と、**`frontend/.env` の `VITE_API_BASE_URL` の両方**（例：`8081` にしたら `http://localhost:8081`） |
| 画面（Vite） | 5173 | **変えない**。使っているものを止めて空ける（下記） |
| メール受信（Mailpit） | 1025（受け取り）・8025（画面） | `.env` の `MAIL_PORT`・`MAILPIT_UI_PORT` |

- 確かめた PC では、ほかのプロジェクトとぶつかったため、`DB_PORT=5433`・`SERVER_PORT=8081` にずらしている
- 画面の 5173 は、バックエンドが許可する画面の URL（`FRONTEND_ORIGIN`。既定は `http://localhost:5173`）と一致している必要がある。変えるなら `frontend/vite.config.ts` の `port` と `.env` の `FRONTEND_ORIGIN` を両方変えることになるため、空けるほうが簡単
- 何がポートを使っているか：Windows は `netstat -ano | findstr :8080`（最後の数字がプロセスの番号）、macOS・Linux は `lsof -i :8080`

## 4. 起動

ターミナルを3つ使う（Docker・バックエンド・画面）。**それぞれ「動いたことの確かめ方」まで確かめてから次へ進む。**

### 4-1. DB とメール受信（Docker）

1. **Docker Desktop を起動しておく**（起動していないと、下のコマンドが分かりにくいエラーになる。「7. 詰まったとき」）
2. リポジトリの一番上で：

   ```bash
   docker compose up -d
   ```

- 確かめ方：`docker compose ps` で `postgres` と `mailpit` が `Up` になっている
- この2つは、Docker Desktop を起動するたびに自動で立ち上がる（`restart: unless-stopped`）。ほかのプロジェクトとポートがぶつかるときは `docker compose stop` で止める

### 4-2. バックエンド

```bash
cd backend
./mvnw spring-boot:run
```

- Windows の PowerShell・コマンドプロンプトでは `mvnw.cmd spring-boot:run`
- **`backend/` に入ってから起動する**（`.env` を `../.env` として読むため。ほかの場所から起動すると読まれない）
- 初回はダウンロードで数分かかる
- 確かめ方：ログに `Started BackendApplication` と出て、ブラウザで `http://localhost:<SERVER_PORT>/api/health` を開くと `{"status":"ok"}` が出る
  - テストデータを入れた場合は、ログに `開発用のテストデータを投入しました` と出る

### 4-3. 画面

```bash
cd frontend
npm ci
npm run dev
```

- **`npm install` ではなく `npm ci`**（`package-lock.json` に書かれた版のとおりに入れ、lock ファイルを書き換えない。Node.js の版が合わなければここで止まる）
- 確かめ方：`http://localhost:5173/health` を開くと、バックエンドの `/api/health` の結果が出る
- **`127.0.0.1` ではなく `localhost` で開く**（「7. 詰まったとき」）

## 5. 動作確認

### 5-1. 画面

1. `http://localhost:5173/login` を開く
2. テストデータのアカウント（README「開発用のテストデータ」の「アカウント」。例：`dev_aoi@example.com`）でログインする
3. ホームに、写真つきの投稿が並ぶ（写真が壊れて出ていない）ことを確かめる

テストデータを入れていない場合は、`http://localhost:5173/register` で新規会員登録してから、写真つきの投稿を1件作って確かめる。

### 5-2. メール

1. ログイン画面の「パスワードをお忘れですか？」から、登録したメールアドレスで再設定のメールを頼む
2. `http://localhost:<MAILPIT_UI_PORT>`（既定 `http://localhost:8025`）を開くと、届いたメールが読める（外へは送られない）

### 5-3. テスト

```bash
# バックエンド（数分かかる。DB は使わない（テスト用のメモリの DB を使う））
cd backend
./mvnw test

# 画面（Vitest。ブラウザの代わりに jsdom で描画する）
cd frontend
npm test
```

- 確かめた PC では、バックエンド 787 件・画面 315 件がすべて通った（2026-10-29）
- **落ちたテストがあったら、名前を必ず控える**。再実行して通っても、それで終わりにしない（「7. 詰まったとき」の最後の行）
  - バックエンド：`backend/target/surefire-reports/` の `.txt` に、落ちたテストと理由が残る（次に流すと上書きされる）
  - 画面：次のように流すと、結果が `frontend/test-results/` にファイルで残る（流すたびに別のファイルになり、上書きされない。`.gitignore` 済み）

    ```bash
    # Git Bash / macOS / Linux
    npx vitest run --reporter=default --reporter=junit --outputFile.junit=test-results/vitest-$(date +%Y%m%d-%H%M%S).xml
    ```

    ```powershell
    # PowerShell
    npx vitest run --reporter=default --reporter=junit --outputFile.junit="test-results/vitest-$(Get-Date -Format yyyyMMdd-HHmmss).xml"
    ```

    落ちたテストは、ファイルの中の `<failure` の直前の `<testcase classname="…" name="…">` に、ファイル名とテスト名が書かれている

## 6. 止め方・やり直し

### 6-1. 止める

- バックエンド・画面：それぞれのターミナルで `Ctrl + C`
- **止めたつもりでも、プロセスが残ることがある**。ターミナルやそれを動かしていたシェルだけが終わり、Java（8080 など）や Vite（5173）が動き続ける。実際に、Java で2回、Vite で1回起きた。残ったものは古いコードで動き続けるため、「直したのに古い動きのまま」になる
  - 起動し直す前に、ポートを使っているプロセスがないかを確かめる：Windows は `netstat -ano | findstr ":8080 :5173"`、macOS・Linux は `lsof -i :8080 -i :5173`
  - 残っていたら止める：Windows は PowerShell で `Stop-Process -Id <プロセスの番号>`、macOS・Linux は `kill <プロセスの番号>`
- DB とメール受信：`docker compose stop`（データは残る）

### 6-2. DB の作り直し

| コマンド | コンテナ | DB のデータ |
| --- | --- | --- |
| `docker compose stop` | 止める | 残る |
| `docker compose down` | 消す | 残る |
| `docker compose down -v` | 消す | **消える** |

- **表の形が変わるブランチを取り込んだら、DB を作り直す**：`docker compose down -v` → `docker compose up -d` → バックエンドを起動し直す。開発中は、アプリが起動時に表を作り足す設定（`ddl-auto=update`）のため、列の名前を変えたときなどに古い列が残り、保存できなくなることがある（README「措置を取り込んだときの DB」）
- DB を作り直したら、`backend/uploads/` と `backend/evidence/` の中のファイルも消す（DB から参照されないファイルになるため）
- テストデータは、DB を作り直してからバックエンドを起動すると、もう一度作られる（`DEV_SEED_ENABLED=true` のとき）

## 7. 詰まったとき

| 症状 | 原因 | 直し方 |
| --- | --- | --- |
| `docker compose up -d` で `error during connect` … `dockerDesktopLinuxEngine` … `cannot find the file` | Docker Desktop が起動していない | Docker Desktop を起動してから、もう一度 |
| `docker compose up -d` で `port is already allocated` など | ほかのプロジェクトのコンテナ・PostgreSQL がポートを使っている | ほかを止めるか、「3-4. ポートを決める」のとおりずらす |
| バックエンドが起動しない（`JWT_SECRET` についてのエラー） | `.env` の `JWT_SECRET` が空 | 「3-2」のとおり作って書く |
| バックエンドが `Port 8080 was already in use` で起動しない | 前に起動したバックエンドが残っている、またはほかのアプリ | 「6-1」のとおり残ったプロセスを止める。ほかのアプリなら `SERVER_PORT` と `VITE_API_BASE_URL` をずらす |
| **直したのに、古い動きのまま** | **止めたつもりのバックエンド（Java）が動き続けていた**（2回起きた。`CLAUDE.md`） | 「6-1」のとおり確かめて止める。新しいコードで動いているかは、ログではなく、その変更で変わったもの（新しい列が DB にあるか、など）で確かめる |
| `.env` の値が効かない | `backend/` の外からバックエンドを起動した | `backend/` に入ってから起動する |
| バックエンドのビルドが `JDK 21 or later is required` で止まる | JDK が 21 より古い（17 など） | JDK 21 以上を入れ、`JAVA_HOME` をそちらに向ける。日本語の部分は、Git Bash などでは文字化けすることがある（Java が Windows の文字コードで出力するため）。先頭の英語の部分を読む |
| macOS・Linux で `./mvnw: Permission denied` | `mvnw` に実行権限がない（この手順書と同じ変更で直した。それより前に取得したもの・zip で取得したもの） | `chmod +x backend/mvnw` |
| `npm ci` が `EBADENGINE` で止まる | Node.js の版が合わない（20 など） | 表示された `Required` の版を入れる（「1. 必要なもの」） |
| `npm run dev` が `Port 5173 is already in use` で止まる | 前に起動した Vite が残っている（1回起きた）、またはほかのアプリ | 「6-1」のとおり残ったプロセスを止める |
| **画面は出るのに、API がすべて失敗する（ブラウザの開発ツールに CORS のエラー）** | 画面を開いた URL が、バックエンドが許可する画面の URL（`FRONTEND_ORIGIN`）と違う。**以前は、5173 が使われていると Vite が黙って 5174 で起動し、この状態になっていた**（2026-10-28 の調査で見つけた。それまで docs に記録がなかった。今は `strictPort` を入れたため、5174 に移らずに上の行のとおり止まる） | `http://localhost:5173` で開く。`frontend/.env` の `VITE_API_BASE_URL` が `SERVER_PORT` と合っているかも確かめる |
| **`http://127.0.0.1:5173` で開くとつながらない** | Vite は IPv6 の `[::1]:5173` でだけ待ち受けている（確かめた PC で、`netstat` で確認した）。仮につながっても、`FRONTEND_ORIGIN` が `localhost` のため CORS で失敗し、ログインの状態も URL ごとに別になる（2026-10-28 の調査で見つけた。それまで docs に記録がなかった） | **`localhost` で開く**（`127.0.0.1` と混ぜない） |
| 写真つきの投稿・DM が保存できない、起動時に DB のエラー（ブランチを取り込んだ後） | 表の形が変わったのに、DB を作り直していない | 「6-2」のとおり作り直す |
| 直したのに、画面が真っ白・古いまま | Vite がファイルの変更を取りこぼし、古い中身を返していた（1回起きた） | ブラウザを再読み込みする。直らなければ、画面の開発サーバーを止めて起動し直す |
| 日時が9時間ずれている | PC のタイムゾーンが日本時間でない | 「0. 前提：PC のタイムゾーンは日本時間」 |
| Windows で、`curl` で日本語を含む値を送ると文字化けする | コマンドの引数の文字コード | 送る内容を UTF-8 のファイルに書き、ファイルから送る |
| 画面側のテストがまれに1件落ちることがある | 分かっていない（2026-10-29 に1回起き、直後に4回流しても再現しなかった） | **再実行して通っても、それで終わりにせず、落ちたテストの名前を控えること**（「5-3. テスト」の流し方なら、ファイルに残る）。時刻に左右されるテストを2件直した経緯があり、まだ残っている可能性がある |

## 8. 付録：版を固定している場所

| もの | 版 | 固定している場所 |
| --- | --- | --- |
| PostgreSQL | 16.15 | `docker-compose.yml`（`postgres:16` だと、時期によって 16 の中の別の版になるため、細かい番号まで書く） |
| Mailpit | v1.22.0 | `docker-compose.yml` |
| Spring Boot と、そのライブラリ・Maven のプラグインの版 | 4.1.1 | `backend/pom.xml` の親（`spring-boot-starter-parent`） |
| Java の版（コンパイルの対象） | 21 | `backend/pom.xml` の `java.version` |
| JDK の版の確かめ | 21 以上 | `backend/pom.xml` の `maven-enforcer-plugin`（21 ちょうどではなく 21 以上。困るのは古い JDK で動かそうとする場合で、新しい JDK の人まで止める必要はないため） |
| Maven | 3.9.16 | `backend/.mvn/wrapper/maven-wrapper.properties`（`mvnw` が使う） |
| Node.js | 24.16.0（確かめた版） | `frontend/.nvmrc` |
| Node.js（動く範囲） | `^22.22.2 \|\| ^24.15.0 \|\| >=26.0.0` | `frontend/package.json` の `engines`。使っているライブラリ（jsdom・vitest・vite）が求める範囲の重なり |
| Node.js の版が合わなければ止める | — | `frontend/.npmrc` の `engine-strict=true`（ないと警告だけで進んでしまう） |
| 画面のライブラリ | すべて | `frontend/package-lock.json`（`npm ci` で、このとおりに入る） |
| 画面のポート | 5173（使われていたら止める） | `frontend/vite.config.ts` の `port`・`strictPort` |

固定していないもの：Git・Docker Desktop・Docker Compose の版、JDK の作り手（Temurin など）と細かい版、OS、ブラウザ。

### `npm audit` の結果と対応

- **今回は対応しない（2026-11-25 に決めた）**。high 3件はすべて開発のときだけ使う道具（`vite`・`undici`・`source-map-js`）のもので、`npm audit --omit=dev` では0件。利用者に配られる画面には入らないため

| 確認日 | `npm audit` | `npm audit --omit=dev` |
| --- | --- | --- |
| 2026-10-30 | high 3件（`vite`・`source-map-js`・`undici`） | 0件 |
| 2026-11-25 | high 3件（同じ3つ） | 0件 |
