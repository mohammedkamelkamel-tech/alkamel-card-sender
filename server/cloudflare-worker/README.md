# خادم تفعيل الكامل أونلاين

خادم بسيط على Cloudflare Workers + D1 لمنع استخدام نفس كود التفعيل على أكثر من جهاز.

## الإعداد

1. أنشئ Worker باسم `alkamel-license`.
2. أنشئ قاعدة D1 باسم `alkamel-licenses`.
3. نفّذ `schema.sql` داخل D1.
4. ضع `database_id` في `wrangler.toml`.
5. أضف Secret باسم `LICENSE_SECRET`.
6. أضف Secret باسم `ADMIN_KEY`.
7. انشر Worker.
8. ضع رابط Worker في `LicenseManager.kt` مكان `SERVER_URL` ثم ابنِ APK.

## إنشاء كود

POST إلى `/admin/create` مع Header `x-admin-key`.

لليوم: `{"type":"DAY"}`

للحياة: `{"type":"LIFE"}`

الخادم يعيد كودًا من الشكل `D1-XXXXXXXXXXXX-XXXXXXXXXXXX` أو `L1-XXXXXXXXXXXX-XXXXXXXXXXXX`.

عند أول تفعيل يتم ربط الكود بمعرف الجهاز، وأي جهاز آخر يُرفض.
