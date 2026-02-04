# Notify2Discord

Android アプリの通知を Discord へ自動転送するアプリです。

## 機能

- **通知転送** — 指定アプリの通知を Discord Webhook へリアルタイム送信
- **アプリ別Webhook** — 各アプリに個別の Webhook URL を設定可能
- **転送アプリ選択** — 転送対象アプリを任意に選び、検索・ソート対応
- **通知履歴** — 送信済み通知を履歴として閲覧・検索・削除 (LINE風チャットバブル設計)
- **複数選択削除** — 履歴を長押しで複数選択し、まとめて削除
- **履歴保持期間設定** — 1週間〜3ヶ月・無制限のいずれかを選び、古い履歴を自動・手動で削除
- **バッテリー最適化免除** — バックグラウンド転送を維持するために自動で免除リクエスト
- **テーマ切り替え** — ライト・ダーク・システム自動の3通り
- **バージョン管理** — 設定画面でバージョン情報を確認可能
- **テスト送信** — 設定済みWebhookへのテスト通知を即時送信

## 環境要件

|項目 | バージョン |
|------|-----------|
| Android | API 34 (Android 14) 以上 |
| JDK | 17 |
| Gradle | プロジェクト付属のwrapper使用 |

## ビルド手順

```bash
# JAVA_HOME を JDK 17 に設定してからビルド
export JAVA_HOME="/path/to/jdk-17"
./gradlew :app:assembleDebug
```

生成される APK:
`app/build/outputs/apk/debug/app-debug.apk`

## リンク

- [GitHub リポジトリ](https://github.com/contras11/Notify2Discord)
- [個人開発ポータル](https://remudo.com/)
- [X (Twitter)](https://x.com/remudo_)
