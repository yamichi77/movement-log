# movement-log

MoveLog（移動ログ記録アプリ）の Android 版移行プロジェクトです。  
Kotlin + Jetpack Compose で、位置情報の収集・履歴確認・接続設定を扱います。

## 主な機能

- 権限確認画面（位置情報・行動認識・通知）
- ホーム画面（収集開始/停止、最新位置、記録件数、行動状態）
- 履歴マップ画面（保存済みログの軌跡表示）
- ログ一覧画面（最新 50 件表示）
- 接続設定画面（IP/Port/証明書エイリアス保存）

## 技術スタック

- Kotlin 2.0.21
- Android Gradle Plugin 9.0.1
- Jetpack Compose + Material 3
- Navigation Compose
- Room
- DataStore
- Google Play Services（Location / Maps）
- Google Maps Compose

## 動作要件

- JDK 11
- Android SDK Platform 36（`minSdk=36`, `targetSdk=36`）
- `local.properties` に `sdk.dir` を設定済みであること
- 履歴マップ表示には Google Maps API キー（`MAPS_API_KEY`）が必要

## セットアップ

1. `local.properties` をプロジェクトルートに作成し、必要な値を設定する。

```properties
sdk.dir=C\:\\Users\\<your-user>\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=your_google_maps_api_key
# 任意: true でデバッグ用の高速GPSモードを有効化
DEBUG=false
```

2. 依存関係を解決してビルドする。

```powershell
.\gradlew.bat :app:assembleDebug
```

## テスト実行

- Unit Test

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

- UI Test（接続済みエミュレータ/実機が必要）

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

- 回帰テスト一括実行（推奨）

```powershell
.\scripts\run-regression-tests.ps1
```

- 接続E2Eテスト（実エンドポイント、`cert.txt` 利用）

```powershell
.\scripts\run-connection-e2e.ps1
```

`SERVER_URL / SERVER_PORT / SERVER_CERT_KEY` は `cert.txt` から読み込まれます。

回帰テストのログは以下に出力されます。

- `docs/build-logs/p4-04-testDebugUnitTest.txt`
- `docs/build-logs/p4-04-connectedDebugAndroidTest.txt`

## ディレクトリ構成

- `app`: アプリ本体
- `docs`: 移行計画・実施記録・テストログ
- `scripts`: テスト実行などの補助スクリプト

## 関連ドキュメント

- `docs/migration-task-table.md`
- `docs/p4-04-test-execution-flow.md`
