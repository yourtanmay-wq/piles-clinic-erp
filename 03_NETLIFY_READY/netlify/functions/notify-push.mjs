/* 🔔 V1298 (১০.০৯.২০২৬, তালিকা ৪১৪, TK: "SMS-এর মতো আসামাত্র একবার সাউন্ড") —
   Supabase-এর briefings টেবিলে নতুন নোটিশ ঢুকলেই DB-trigger (pg_net) এখানে POST করে;
   এই function ঠিক করে কোন কোন স্টাফের ফোনে যাবে (BriefingModel.targetsHit-এর হুবহু নিয়ম:
   all/allStaff · mobiles · roles · branches) আর Google FCM (v1) দিয়ে সেই ফোনগুলোতে push পাঠায়।
   গোপন জিনিস শুধু Netlify env-এ: FCM_SERVICE_ACCOUNT_JSON (Firebase service-account),
   PUSH_SHARED_SECRET (DB-trigger আর এই function-এর মধ্যে মিল)। কোনো npm-প্যাকেজ নেই — node-র নিজের crypto/fetch। */
import crypto from "node:crypto";

const SUPABASE_URL = "https://bcyeogjqtupbdyciqfmz.supabase.co";
const SUPABASE_ANON = "sb_publishable_k_170-JGrdxmZ7rBrjCyTA_-ElK2XdZ";   // ওয়েব-অ্যাপ এমনিতেই যেটা ব্যবহার করে
const CHANNEL_ID = "clinic_notices_v2";

const b64url = (buf) => Buffer.from(buf).toString("base64").replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
const mob = (v) => String(v || "").replace(/\D/g, "").slice(-10);

async function accessToken(sa) {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(JSON.stringify({ iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging", aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600 }));
  const sig = crypto.createSign("RSA-SHA256").update(header + "." + claim).end().sign(sa.private_key);
  const jwt = header + "." + claim + "." + b64url(sig);
  const r = await fetch("https://oauth2.googleapis.com/token", { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body: "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + jwt });
  if (!r.ok) throw new Error("google token " + r.status + " " + (await r.text()).slice(0, 200));
  return (await r.json()).access_token;
}

function targetsHit(targets, tok) {
  const t = targets || {};
  if (t.all === true || t.allStaff === true) return true;
  const m = mob(tok.mobile);
  if (Array.isArray(t.mobiles) && t.mobiles.some((x) => mob(x) === m)) return true;
  const role = String(tok.role || "").toLowerCase();
  if (Array.isArray(t.roles) && t.roles.some((x) => String(x).toLowerCase() === role)) return true;
  const br = String(tok.branch || "").toLowerCase();
  if (Array.isArray(t.branches) && t.branches.some((x) => String(x).toLowerCase() === br)) return true;
  return false;
}

async function sbGet(path) {
  const r = await fetch(SUPABASE_URL + "/rest/v1/" + path, { headers: { apikey: SUPABASE_ANON, Authorization: "Bearer " + SUPABASE_ANON } });
  if (!r.ok) throw new Error("supabase " + r.status);
  return r.json();
}
async function sbDeleteToken(token) {
  try { await fetch(SUPABASE_URL + "/rest/v1/device_tokens?token=eq." + encodeURIComponent(token), { method: "DELETE", headers: { apikey: SUPABASE_ANON, Authorization: "Bearer " + SUPABASE_ANON } }); } catch (_e) {}
}

export default async (req) => {
  if (req.method !== "POST") return new Response("POST only", { status: 405 });
  const secret = Netlify.env.get("PUSH_SHARED_SECRET") || "";
  if (!secret || req.headers.get("x-push-secret") !== secret) return new Response("forbidden", { status: 403 });
  const saRaw = Netlify.env.get("FCM_SERVICE_ACCOUNT_JSON") || "";
  if (!saRaw) return new Response("no service account", { status: 500 });
  let body; try { body = await req.json(); } catch (_e) { return new Response("bad json", { status: 400 }); }
  const row = body && (body.record || body.row || body);
  if (!row || !row.id) return new Response("no row", { status: 400 });
  if (row.deletedAt) return new Response("deleted row — skip", { status: 200 });

  const sa = JSON.parse(saRaw);
  const tokens = await sbGet("device_tokens?select=token,mobile,role,branch&limit=2000");
  const creator = mob(row.createdBy);
  const targets = tokens.filter((t) => t && t.token && targetsHit(row.targets, t) && mob(t.mobile) !== creator);   // যে লিখেছে তার নিজের ফোনে নয়
  if (!targets.length) return new Response(JSON.stringify({ sent: 0 }), { status: 200 });

  const at = await accessToken(sa);
  const title = String(row.title || "New notice").slice(0, 80);
  const text = String(row.message || "").replace(/\s+/g, " ").slice(0, 160) || "Tap to open the Notice Board.";
  let sent = 0, bad = 0;
  for (const t of targets) {
    const msg = { message: { token: t.token,
      notification: { title: "🔔 " + title, body: text },
      android: { priority: "high", notification: { channel_id: CHANNEL_ID, sound: "default", default_vibrate_timings: true, tag: "briefing_" + row.id } },
      data: { kind: "briefing", id: String(row.id) } } };
    const r = await fetch("https://fcm.googleapis.com/v1/projects/" + sa.project_id + "/messages:send", { method: "POST", headers: { Authorization: "Bearer " + at, "content-type": "application/json" }, body: JSON.stringify(msg) });
    if (r.ok) sent++;
    else { bad++; const txt = await r.text(); if (r.status === 404 || /UNREGISTERED|NOT_FOUND|INVALID_ARGUMENT/.test(txt)) await sbDeleteToken(t.token); }
  }
  return new Response(JSON.stringify({ sent, bad, targets: targets.length }), { status: 200, headers: { "content-type": "application/json" } });
};

export const config = { path: "/notify-push" };
