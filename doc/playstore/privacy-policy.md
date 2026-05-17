# Misattitude — プライバシーポリシー / Privacy Policy

**最終更新日 / Last updated:** 2026-05-17

---

## 日本語

### 1. はじめに

Misattitude（以下「本アプリ」）は、回転姿勢（attitude）を 3D で可視化する
教育用ツールです。本アプリの開発者（takarakasai）は、利用者のプライバシーを
最優先に考えています。

### 2. 本アプリ自身がサーバーに送信するデータ

**本アプリのコード自体は、利用者のいかなる個人情報・利用情報もサーバーに
送信しません。** 3D 計算、設定の保存、購入状態の管理はすべて端末内で完結します。

具体的に、本アプリのコードは以下を一切収集・送信しません：

- 氏名、メールアドレス、電話番号などの個人を特定できる情報
- 端末識別子（IMEI、デバイス ID 等）
- 位置情報、連絡先、カレンダー、写真、ファイル
- 利用状況・操作ログ・クラッシュレポート

### 3. サードパーティ SDK によるデータ収集

無料版では以下のサードパーティ SDK を組み込んでおり、それぞれが独立した
データ収集を行います。Pro 版（広告非表示、アプリ内購入）を購入された場合、
**Google Mobile Ads SDK は完全に無効化** され、データ収集も行われません。

#### 3-1. Google Mobile Ads SDK (AdMob) — 無料版のみ

- **収集者**: Google LLC
- **目的**: バナー広告の表示、広告効果測定
- **収集される可能性のあるデータ**:
  - 広告 ID（Android Advertising ID / リセット可能）
  - おおよその位置情報（IP アドレスから推定される国・地域レベル）
  - 端末モデル、OS バージョン、ロケール
  - 広告の表示・クリック・操作に関するイベント
- **EU / EEA / UK の利用者**: Google User Messaging Platform (UMP) による
  同意ダイアログが起動時に表示されます。利用者の同意・拒否に応じて、
  パーソナライズ広告または非パーソナライズ広告（あるいは広告非表示）が
  配信されます。
- **Google のプライバシーポリシー**: https://policies.google.com/privacy

#### 3-2. Google Play Billing Library

- **収集者**: Google LLC
- **目的**: アプリ内購入（Pro 版アップグレード）の処理
- **収集される可能性のあるデータ**:
  - 購入トークン（Google アカウントに紐付く、本アプリ内のみで使用）
  - 購入者の Google アカウント識別子（Google のみが扱う）
- **Google のプライバシーポリシー**: https://policies.google.com/privacy

### 4. アプリの権限

本アプリは以下の Android 権限を宣言しています：

| 権限 | 用途 |
|---|---|
| `INTERNET` | AdMob による広告配信および Play Billing による購入処理 |
| `ACCESS_NETWORK_STATE` | AdMob がネットワーク状態に応じて広告を読み込むため |

カメラ・マイク・位置情報・連絡先・ストレージなどの個別アクセス権限は
一切要求しません。

### 5. Pro 版による広告 SDK の無効化

「Pro版（広告非表示）」アプリ内購入を完了された利用者については、
Google Mobile Ads SDK の初期化および広告リクエストを完全に停止します。
Pro 状態は同じ Google アカウントでサインインしている全端末で自動的に
有効化されます。

### 6. 子供のプライバシー

本アプリは 13 歳未満を主たる対象としていません。

### 7. 利用者の選択

- **広告のパーソナライズ拒否**: EU 圏では起動時の UMP ダイアログ、
  EU 圏外では Android の「広告 ID をリセット」または「広告のパーソナライズを
  オフにする」機能で対応可能です。
- **広告を完全に削除する**: Pro 版アップグレードを購入してください。
- **広告 ID の削除**: Android 設定 → Google → 広告 から「広告 ID を削除」
  を選択できます。

### 8. 本ポリシーの変更

本ポリシーが変更される場合、本ページにて告知します。実質的な変更がある場合は
アプリ内通知または更新リリースノートで告知します。

### 9. お問い合わせ

ご質問・ご要望は GitHub Issues にてご連絡ください：
https://github.com/takarakasai/misattitude

---

## English

### 1. Introduction

Misattitude (the "App") is an educational tool for 3D visualization of rotation
attitude. The developer (takarakasai) places the highest priority on user privacy.

### 2. Data Sent by the App Itself

**The App's own code does not transmit any personal or usage information to any
server.** All 3D computation, settings persistence, and purchase-state management
happens on-device.

Specifically, the App's own code does **not** collect or transmit any of:

- Personally identifiable information (name, email, phone, address)
- Device identifiers (IMEI, device ID)
- Location data, contacts, calendar, photos, files
- Usage statistics, interaction logs, or crash reports

### 3. Data Collected by Third-Party SDKs

The free version embeds the following third-party SDKs, each of which performs
its own independent data collection. If you purchase the **Pro upgrade** (in-app
purchase), the **Google Mobile Ads SDK is fully disabled** and performs no data
collection.

#### 3-1. Google Mobile Ads SDK (AdMob) — free version only

- **Collected by**: Google LLC
- **Purpose**: Display banner ads, measure ad performance
- **Data potentially collected**:
  - Android Advertising ID (user-resettable)
  - Approximate location (country / region inferred from IP address)
  - Device model, OS version, locale
  - Ad-impression / click / interaction events
- **EU / EEA / UK users**: Google's User Messaging Platform (UMP) presents a
  consent dialog at launch. Personalised, non-personalised, or no ads will be
  served according to the user's choice.
- **Google's privacy policy**: https://policies.google.com/privacy

#### 3-2. Google Play Billing Library

- **Collected by**: Google LLC
- **Purpose**: Process in-app purchases (Pro upgrade)
- **Data potentially collected**:
  - Purchase tokens (tied to your Google account, used only by the App)
  - Purchaser's Google account identifier (handled solely by Google)
- **Google's privacy policy**: https://policies.google.com/privacy

### 4. App Permissions

The App declares the following Android permissions:

| Permission | Purpose |
|---|---|
| `INTERNET` | Ad delivery via AdMob and purchase processing via Play Billing |
| `ACCESS_NETWORK_STATE` | AdMob uses it to adapt ad loading to network conditions |

The App requests **no** camera, microphone, location, contacts, or storage
permissions.

### 5. Pro Version Disables the Ad SDK

For users who have completed the "Pro upgrade (remove ads)" in-app purchase,
Google Mobile Ads SDK initialisation and ad requests are fully suppressed.
Pro status applies automatically to all devices signed in to the same Google
account.

### 6. Children's Privacy

The App is not primarily targeted at users under 13.

### 7. User Choices

- **Opt out of personalised ads**: In the EU, use the UMP dialog at launch.
  Outside the EU, use Android's "Reset advertising ID" or "Turn off ad
  personalisation" settings.
- **Remove ads entirely**: Purchase the Pro upgrade in-app.
- **Delete advertising ID**: Android Settings → Google → Ads → "Delete
  advertising ID".

### 8. Changes to This Policy

If this policy changes, we will announce the changes on this page. Any
substantive changes will also be announced via in-app notification or release
notes.

### 9. Contact

For questions or feedback, please open an issue on GitHub:
https://github.com/takarakasai/misattitude
