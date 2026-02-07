#!/bin/bash

# Notify2Discord 設定バックアップ・復元スクリプト

PACKAGE="com.notify2discord.app"
BACKUP_DIR="./app_backup"

# バックアップ
backup() {
    echo "設定をバックアップしています..."
    mkdir -p "$BACKUP_DIR"
    adb backup -f "$BACKUP_DIR/notify2discord_backup.ab" -noapk "$PACKAGE"
    echo "バックアップ完了: $BACKUP_DIR/notify2discord_backup.ab"
}

# 復元
restore() {
    if [ ! -f "$BACKUP_DIR/notify2discord_backup.ab" ]; then
        echo "エラー: バックアップファイルが見つかりません"
        exit 1
    fi
    echo "設定を復元しています..."
    adb restore "$BACKUP_DIR/notify2discord_backup.ab"
    echo "復元完了"
}

# コマンドライン引数で動作を切り替え
case "$1" in
    backup)
        backup
        ;;
    restore)
        restore
        ;;
    *)
        echo "使い方: $0 {backup|restore}"
        echo "  backup  - 現在の設定をバックアップ"
        echo "  restore - バックアップから設定を復元"
        exit 1
        ;;
esac
