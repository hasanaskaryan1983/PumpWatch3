# ADR-0002: واچ‌لیست رمزنگاری‌شده با مهاجرت fail-closed

Status: Accepted (Commits 109, 113, 133)

## Context
دادهٔ واچ‌لیست قبلاً plaintext در SharedPreferences بود. مهاجرت به ذخیرهٔ رمزنگاری‌شده نباید هیچ دادهٔ کاربر را گم کند و نباید هرگز به plaintext برگردد.

## Decision
- رمزنگاری AES-256-GCM با کلید داخل Android Keystore
- اگر Keystore در دسترس نباشد: هیچ نوشتنی انجام نمی‌شود (fail-closed) و flag ناامنی ست می‌شود
- مهاجرت فقط وقتی کلید legacy را پاک می‌کند که نوشتن رمزنگاری‌شده موفق شده باشد
- invariant با تست Robolectric قفل شده است (Commit 133)

## Consequences
+ هرگز plaintext ذخیره نمی‌شود
+ دادهٔ legacy در مسیر شکست حفظ می‌شود و مهاجرت قابل تلاش مجدد است
- اگر Keystore خراب باشد، کاربر «موفقیت» می‌بیند ولی داده persist نمی‌شود
  (شکاف UX مستند؛ نامزد کامیت آینده: انتشار نتیجهٔ save به UI)
