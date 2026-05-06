# Misattitude プライバシーポリシー

最終更新日: <Phase C 統合時の日付に更新>

## 1. はじめに

本プライバシーポリシー（以下「本ポリシー」）は、開発者 Takara Kasai（以下「開発者」）が
提供する Android アプリケーション「Misattitude」（以下「本アプリ」）における、
利用者の情報の取扱いについて定めるものです。本アプリをご利用いただくことにより、
利用者は本ポリシーに同意したものとみなします。

## 2. 本アプリの概要

本アプリは、3 次元剛体の姿勢（attitude / rotation）をクォータニオン・回転行列・
オイラー角の 3 つの数学表現で可視化する教育用ツールです。アプリの中核機能は
端末内で完結しますが、無料利用時は広告配信のため一部の情報が第三者サービスに
送信されます。

## 3. 取得する情報

### 3.1 開発者が直接取得する情報

開発者は、利用者から以下の情報を**いずれも直接取得しません**:

- 氏名・メールアドレス・電話番号などの個人を特定できる情報
- 位置情報
- カメラ・マイク・連絡先・ストレージ等のデバイス機能
- アカウント情報・認証情報
- 利用ログ・操作履歴

### 3.2 第三者サービス（Google AdMob）が取得する情報

本アプリは無料版で広告配信のため Google AdMob を利用しており、AdMob は
広告 SDK の機能として以下の情報を取得します:

- **Android 広告 ID（AAID）** — 広告のパーソナライズおよびフリークエンシーキャッピング
- **デバイス情報** — 機種名、OS バージョン、画面解像度、システム言語、タイムゾーン
- **ネットワーク情報** — おおまかな国・地域、接続種別（Wi-Fi / モバイル）
- **広告操作情報** — 広告の表示・クリック等のインタラクション

これらの情報は本アプリ内に保存されず、開発者は個々の利用者を特定する形で
取得しません。詳細は Google の広告ポリシーをご参照ください:
https://policies.google.com/technologies/ads

利用者は Android の設定 > Google > 広告 > **広告 ID をリセット** または
**パーソナライズド広告をオプトアウト**することで、広告のパーソナライズを
無効化できます。

### 3.3 第三者サービス（Google Play Billing）が取得する情報

本アプリ内で「広告除去」のアプリ内課金を利用される場合、課金処理は
Google Play Billing を介して行われます。決済情報（クレジットカード情報等）は
Google が処理し、開発者には支払い手段の詳細は提供されません。開発者が受け取るのは
購入完了の事実とトークンのみです。

詳細: https://policies.google.com/privacy

## 4. 個人情報の第三者提供

開発者は、利用者から取得した情報を第三者に提供することはありません。
3.2 / 3.3 に記載の第三者サービスが取得する情報は、各サービスのプライバシー
ポリシーに従って取り扱われます。

## 5. 必要な権限（Permission）

本アプリは以下の権限を使用します:

- **`android.permission.INTERNET`** — 広告配信および課金処理に必要
- **`android.permission.ACCESS_NETWORK_STATE`** — ネットワーク接続状態の確認
  （オフライン時の不要な広告リクエスト抑制）

## 6. EEA / 英国の利用者について（GDPR）

EEA / 英国 / スイスの利用者については、Google の User Messaging Platform（UMP）
を通じて広告のパーソナライズに対する同意を取得します。同意管理画面は本アプリの
初回起動時に表示され、設定はいつでも変更可能です。

## 7. 子どもの安全

本アプリは特定の年齢層を対象としていませんが、教育目的のため 13 歳以上の
一般利用者向けに設計されています。COPPA / GDPR-K に該当する子どもからの
情報は意図的に収集しません。

## 8. ポリシーの変更

本アプリの機能変更により本ポリシーを更新する場合、本ページ上部の最終更新日を
変更します。重要な変更については、本アプリ内またはアプリストアの説明欄で
お知らせします。

## 9. 開発者・お問い合わせ先

開発者: Takara Kasai
連絡先: kohya.th@gmail.com
リポジトリ: https://github.com/takarakasai/misattitude

---

## English Version (Summary)

### Misattitude Privacy Policy

Last updated: <update on Phase C integration>

The free version of this application uses **Google AdMob** for ad delivery,
which collects the Android Advertising ID (AAID), device information (model,
OS version, screen resolution, locale, timezone), approximate network/region
information, and ad interaction events for ad personalization and frequency
capping. The developer does not directly collect or store this information.

**In-app purchases** for ad removal are processed via **Google Play Billing**.
Payment details are handled by Google; the developer only receives purchase
confirmation tokens.

Required permissions: `INTERNET`, `ACCESS_NETWORK_STATE`.

For users in the EEA / UK / Switzerland, ad personalization consent is
collected via Google's User Messaging Platform (UMP) at first launch.

Users can reset or opt out of personalized ads at any time via Android system
settings (Settings > Google > Ads).

For inquiries: `kohya.th@gmail.com`
Repository: https://github.com/takarakasai/misattitude
