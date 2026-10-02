const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: { "content-type": "application/json; charset=UTF-8", "access-control-allow-origin": "*" }
});

async function hmac12(secret, text) {
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(text));
  return [...new Uint8Array(sig)].map(b => b.toString(16).padStart(2, "0")).join("").slice(0, 12).toUpperCase();
}

function parseCode(code) {
  const m = /^([DL]1)-([0-9A-F]{12})-([0-9A-F]{12})$/i.exec(code || "");
  if (!m) return null;
  return { prefix: m[1].toUpperCase(), token: m[2].toUpperCase(), signature: m[3].toUpperCase() };
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: {
        "access-control-allow-origin": "*",
        "access-control-allow-methods": "POST,GET,OPTIONS",
        "access-control-allow-headers": "content-type,x-admin-key"
      }});
    }

    const url = new URL(request.url);

    if (url.pathname === "/health" && request.method === "GET") {
      return json({ ok: true, service: "alkamel-license" });
    }

    if (url.pathname === "/activate" && request.method === "POST") {
      let body;
      try { body = await request.json(); } catch { return json({ message: "بيانات الطلب غير صحيحة" }, 400); }

      const parsed = parseCode(body.code);
      const deviceId = String(body.deviceId || "").trim();
      if (!parsed || !deviceId) return json({ message: "كود التفعيل أو الجهاز غير صحيح" }, 400);

      const expected = await hmac12(env.LICENSE_SECRET, `${parsed.prefix === "D1" ? "DAY" : "LIFE"}|${parsed.token}`);
      if (expected !== parsed.signature) return json({ message: "كود التفعيل غير صحيح" }, 403);

      const type = parsed.prefix === "D1" ? "DAY" : "LIFE";
      const existing = await env.DB.prepare(
        "SELECT token,type,device_id,activated_at,expires_at FROM licenses WHERE token=?"
      ).bind(parsed.token).first();

      if (existing && existing.device_id && existing.device_id !== deviceId) {
        return json({ message: "هذا الكود مستخدم بالفعل على جهاز آخر" }, 409);
      }

      if (!existing) {
        const now = Date.now();
        const expiresAt = type === "DAY" ? now + 24 * 60 * 60 * 1000 : 0;
        const result = await env.DB.prepare(
          "INSERT OR IGNORE INTO licenses(token,type,device_id,activated_at,expires_at) VALUES(?,?,?,?,?)"
        ).bind(parsed.token, type, deviceId, now, expiresAt).run();
        if (!result.success) return json({ message: "تعذر حفظ التفعيل" }, 500);
      }

      const license = await env.DB.prepare(
        "SELECT type,device_id,expires_at FROM licenses WHERE token=?"
      ).bind(parsed.token).first();

      if (!license || license.device_id !== deviceId) {
        return json({ message: "تعذر تثبيت ملكية الكود لهذا الجهاز" }, 409);
      }
      if (license.type === "DAY" && Number(license.expires_at) <= Date.now()) {
        return json({ message: "انتهت صلاحية كود اليوم" }, 410);
      }

      return json({ type: license.type, expiresAt: Number(license.expires_at || 0) });
    }

    if (url.pathname === "/admin/create" && request.method === "POST") {
      if (request.headers.get("x-admin-key") !== env.ADMIN_KEY) return json({ message: "غير مصرح" }, 401);
      let body;
      try { body = await request.json(); } catch { return json({ message: "بيانات الطلب غير صحيحة" }, 400); }

      const type = String(body.type || "").toUpperCase();
      const token = String(body.token || crypto.randomUUID().replaceAll("-", "").slice(0,12)).toUpperCase();
      if (!["DAY","LIFE"].includes(type) || !/^[0-9A-F]{12}$/.test(token)) {
        return json({ message: "type/token غير صحيح" }, 400);
      }

      const prefix = type === "DAY" ? "D1" : "L1";
      const signature = await hmac12(env.LICENSE_SECRET, `${type}|${token}`);
      const code = `${prefix}-${token}-${signature}`;

      const result = await env.DB.prepare(
        "INSERT OR IGNORE INTO licenses(token,type,device_id,activated_at,expires_at) VALUES(?,?,?,?,?)"
      ).bind(token, type, null, null, null).run();

      if (!result.success) return json({ message: "تعذر إنشاء الكود" }, 500);
      return json({ code, type });
    }

    return json({ message: "Not found" }, 404);
  }
};
