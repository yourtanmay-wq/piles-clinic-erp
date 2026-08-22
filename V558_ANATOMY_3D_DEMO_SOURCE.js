/* ================= PILES CLINIC — 3D ANATOMY (V558) =================
   TK APPROVED PLAN: রোগের ছবি হাতে আঁকা নয় — একটা সত্যিকারের ঘোরানো-যায়
   ত্রিমাত্রিক (3D) মডেল। কোনো বাইরের লাইব্রেরি নেই (offline + Free Plan),
   পুরোটা নিজের হাতে লেখা software renderer, শুধু canvas 2D ব্যবহার করে।
   ফোনের Kotlin কোডেও ঠিক এই একই অঙ্ক বসবে।
   =================================================================== */
(function (root) {
  'use strict';

  /* ---------- ছোট ভেক্টর অঙ্ক ---------- */
  function norm(v) { var l = Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]) || 1; return [v[0]/l, v[1]/l, v[2]/l]; }

  /* ---------- মডেলের মাপ (সব একক = সেন্টিমিটার) ---------- */
  var FIELD_CM   = 7.0;   // ছবিতে চারপাশের কতটা চামড়া দেখা যাবে (ব্যাসার্ধ)
  var VERGE_CM   = 1.35;  // পায়ুদ্বারের মুখ (anal verge)
  var CANAL_CM   = 3.6;   // পায়ুনালীর গভীরতা
  var N_FOLD     = 10;    // চারপাশের ভাঁজের সংখ্যা

  /* ---------- উচ্চতা-ফাংশন: এখানেই পুরো শরীরটা তৈরি হয় ---------- */
  // r = কেন্দ্র থেকে দূরত্ব (cm), a = কোণ (radian, 0 = ডান দিক, ঘড়ির ১২টা = -PI/2)
  function surfaceZ(r, a, piles) {
    var x = r * Math.cos(a), y = r * Math.sin(a);
    // ১. দুই পাছা — দুটো গোল ঢিবি, মাঝখানে আপনা থেকেই খাঁজ তৈরি হয়
    var bx = 3.30, z = 0;
    z += 3.60 * Math.exp(-((x - bx) * (x - bx) / 5.6 + y * y / 26.0));
    z += 3.60 * Math.exp(-((x + bx) * (x + bx) / 5.6 + y * y / 26.0));
    // ৩. পায়ুদ্বারের চোঙা — কেন্দ্রে সবচেয়ে গভীর
    var t = r / VERGE_CM;
    z -= 0.52 * Math.exp(-t * t * 2.2);
    // ৪. পায়ুদ্বারের চারপাশের ভাঁজ (radial folds)
    var fw = (r - VERGE_CM * 0.80) / (VERGE_CM * 0.80);
    z += 0.11 * Math.exp(-fw * fw) * Math.cos(N_FOLD * a);
    // ৫. পাইলস — প্রতিটা ফোলা একটা করে আঙুরের মত ঢিবি
    for (var i = 0; i < piles.length; i++) {
      var p = piles[i];
      var dx = x - p.r * Math.cos(p.a), dy = y - p.r * Math.sin(p.a);
      var d2 = dx * dx + dy * dy, w = pileWidth(p.size);
      z += p.size * Math.exp(-d2 / (w * w));
    }
    return z;
  }
  function pileWidth(size) { return 0.40 + 0.30 * size; }   // যত বড় ফোলা তত চওড়া

  /* ---------- রং ---------- */
  function mix(c1, c2, t) {
    t = t < 0 ? 0 : (t > 1 ? 1 : t);
    return [c1[0] + (c2[0]-c1[0])*t, c1[1] + (c2[1]-c1[1])*t, c1[2] + (c2[2]-c1[2])*t];
  }
  var SKIN   = [172, 124,  96];   // চারপাশের চামড়া
  var SKIN_D = [126,  84,  64];   // খাঁজের ছায়া-চামড়া
  var VERGE  = [166,  96,  92];   // পায়ুদ্বারের চারপাশ
  var MUCOSA = [188,  92, 104];   // ভিতরের মিউকোসা
  var PILE   = [156,  50,  74];   // রক্ত-জমা ফোলা পাইলস
  var DEEP   = [ 74,  32,  38];   // নালীর ভিতরের অন্ধকার

  function surfaceColor(r, a, piles) {
    var base;
    if (r > VERGE_CM * 1.55) base = mix(SKIN_D, SKIN, Math.min(1, (r - VERGE_CM*1.55) / 1.6));
    else if (r > VERGE_CM * 0.92) base = mix(VERGE, SKIN_D, (r - VERGE_CM*0.92) / (VERGE_CM*0.63));
    else base = mix(MUCOSA, VERGE, r / (VERGE_CM * 0.92));
    // কেন্দ্রের ছিদ্র অন্ধকার
    if (r < VERGE_CM * 0.62) base = mix(DEEP, base, Math.pow(r / (VERGE_CM*0.62), 0.55));
    // পাইলসের উপরটা লালচে-বেগুনি
    var lift = 0;
    for (var i = 0; i < piles.length; i++) {
      var p = piles[i];
      var dx = r*Math.cos(a) - p.r*Math.cos(p.a), dy = r*Math.sin(a) - p.r*Math.sin(p.a);
      var d = Math.sqrt(dx*dx + dy*dy), w = pileWidth(p.size);
      lift += Math.exp(-(d*d)/(w*w));
    }
    if (lift > 0.02) base = mix(base, PILE, Math.min(0.92, lift * 1.15));
    return base;
  }

  // ভিতরের মিউকোসা ও পাইলস ভেজা — তাই সেখানে আলোর চকচকে ছোপ পড়ে
  function wetAt(r, a, piles) {
    var w = r < VERGE_CM * 1.5 ? 1 - (r / (VERGE_CM * 1.5)) * 0.45 : 0.18;
    for (var i = 0; i < piles.length; i++) {
      var p = piles[i];
      var dx = r*Math.cos(a) - p.r*Math.cos(p.a), dy = r*Math.sin(a) - p.r*Math.sin(p.a);
      var d2 = dx*dx + dy*dy, ww = pileWidth(p.size);
      w = Math.max(w, Math.exp(-d2 / (ww * ww)));
    }
    return w;
  }

  /* ---------- মেশ তৈরি ---------- */
  // নমুনা: rings × spokes টুকরো। ফোনেও একই সংখ্যা যাতে ছবিটা হুবহু এক হয়।
  var RINGS = 38, SPOKES = 84;

  function buildMesh(state) {
    var piles = state.piles || [];
    var cut = !!state.cut;                 // বইয়ের মত কাটা ছবি
    var grid = [], i, j;
    for (i = 0; i <= RINGS; i++) {
      var row = [];
      // কেন্দ্রের কাছে ঘন নমুনা (ভাঁজ ও ফোলা যাতে মসৃণ দেখায়)
      var f = i / RINGS, r = FIELD_CM * Math.pow(f, 1.75);
      for (j = 0; j <= SPOKES; j++) {
        var a = (j / SPOKES) * Math.PI * 2;
        row.push([r * Math.cos(a), r * Math.sin(a), surfaceZ(r, a, piles), r, a]);
      }
      grid.push(row);
    }
    var quads = [];
    for (i = 0; i < RINGS; i++) {
      for (j = 0; j < SPOKES; j++) {
        var p00 = grid[i][j], p10 = grid[i+1][j], p11 = grid[i+1][j+1], p01 = grid[i][j+1];
        var rm = (p00[3] + p10[3]) / 2, am = (p00[4] + p01[4]) / 2;
        // কাটা ছবিতে সামনের অর্ধেকটা সরিয়ে ভিতরটা দেখানো হয়
        if (cut && Math.sin(am) > 0.02 && rm < VERGE_CM * 2.2) continue;
        quads.push({ v: [p00, p10, p11, p01], c: surfaceColor(rm, am, piles), wet: wetAt(rm, am, piles) });
      }
    }
    if (cut) quads = quads.concat(canalQuads(state));
    return quads;
  }

  /* ---------- কাটা ছবির ভিতরের নালী ---------- */
  function canalQuads(state) {
    var piles = state.piles || [], out = [], i, j;
    var DEPTH = 26;
    for (i = 0; i < DEPTH; i++) {
      for (j = 0; j < SPOKES; j++) {
        var a0 = (j / SPOKES) * Math.PI * 2, a1 = ((j + 1) / SPOKES) * Math.PI * 2;
        var am = (a0 + a1) / 2;
        if (Math.sin(am) > 0.02) continue;                    // সামনের অর্ধেক কাটা
        var d0 = (i / DEPTH), d1 = ((i + 1) / DEPTH);
        var z0 = surfaceZ(0.001, am, piles) - d0 * CANAL_CM;
        var z1 = surfaceZ(0.001, am, piles) - d1 * CANAL_CM;
        // নালী উপরে চওড়া, গভীরে সরু হয়ে আবার একটু চওড়া (ampulla)
        var w0 = canalRadius(d0), w1 = canalRadius(d1);
        var v = [
          [w0*Math.cos(a0), w0*Math.sin(a0), z0, 0, a0],
          [w1*Math.cos(a0), w1*Math.sin(a0), z1, 0, a0],
          [w1*Math.cos(a1), w1*Math.sin(a1), z1, 0, a1],
          [w0*Math.cos(a1), w0*Math.sin(a1), z0, 0, a1]
        ];
        var col = mix(DEEP, MUCOSA, 1 - d0 * 0.75);
        // dentate line (আঁচিলরেখা) — ভিতরের ও বাইরের পাইলসের সীমানা
        if (d0 > 0.30 && d0 < 0.36) col = mix(col, [232, 214, 170], 0.55);
        // ভিতরের পাইলস কাটা ছবিতেও ফুলে থাকে
        for (var k = 0; k < piles.length; k++) {
          var p = piles[k];
          if (!p.inner) continue;
          var da = Math.abs(((am - p.a + Math.PI * 3) % (Math.PI * 2)) - Math.PI);
          var dd = Math.abs(d0 - 0.24);
          var g = Math.exp(-(da*da)/0.30) * Math.exp(-(dd*dd)/0.020);
          if (g > 0.03) col = mix(col, PILE, Math.min(1, g));
        }
        out.push({ v: v, c: col, inner: true, wet: 0.85 });
      }
    }
    return out;
  }
  function canalRadius(d) { return VERGE_CM * (0.62 + 0.55 * Math.sin(Math.PI * Math.min(1, d * 0.9))); }

  /* ---------- ক্যামেরা: এখানেই ঘোরানো ---------- */
  function makeCamera(yaw, pitch, zoom, cx, cy) {
    var cy1 = Math.cos(yaw), sy1 = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
    return {
      project: function (p) {
        var x = p[0] * cy1 + p[2] * sy1;
        var z = -p[0] * sy1 + p[2] * cy1;
        var y = p[1] * cp - z * sp;
        var zz = p[1] * sp + z * cp;
        var k = 1 / (1 - zz * 0.030);          // হালকা perspective
        return [cx + x * zoom * k, cy + y * zoom * k, zz];
      },
      depth: function (p) {
        var z = -p[0] * sy1 + p[2] * cy1;
        return p[1] * sp + z * cp;
      }
    };
  }

  /* ---------- আঁকা ---------- */
  var LIGHT = norm([-0.45, -0.62, 0.75]);
  var HALF  = norm([-0.45, -0.62, 1.75]);

  function faceNormal(v) {
    var ax = v[1][0]-v[0][0], ay = v[1][1]-v[0][1], az = v[1][2]-v[0][2];
    var bx = v[3][0]-v[0][0], by = v[3][1]-v[0][1], bz = v[3][2]-v[0][2];
    return norm([ay*bz - az*by, az*bx - ax*bz, ax*by - ay*bx]);
  }

  function render(ctx, w, h, state) {
    var yaw = state.yaw || 0, pitch = (state.pitch === undefined ? 0.62 : state.pitch);
    var zoom = (w / (FIELD_CM * 2.35)) * (state.zoom || 1);
    var cam = makeCamera(yaw, pitch, zoom, w / 2, h / 2 + h * 0.04);

    // পটভূমি
    var g = ctx.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, '#F5EFE7'); g.addColorStop(1, '#E6DCD0');
    ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);

    var quads = buildMesh(state);
    for (var i = 0; i < quads.length; i++) {
      var q = quads[i], v = q.v;
      q.d = (cam.depth(v[0]) + cam.depth(v[1]) + cam.depth(v[2]) + cam.depth(v[3])) / 4;
      var n = faceNormal(v);
      var lam = n[0]*LIGHT[0] + n[1]*LIGHT[1] + n[2]*LIGHT[2];
      q.sh = 0.42 + 0.74 * Math.max(0, lam);
      if (q.inner) q.sh *= 0.72;
      var zm = (v[0][2] + v[1][2] + v[2][2] + v[3][2]) / 4;
      q.sh *= 0.72 + 0.28 * Math.min(1, Math.max(0, (zm + 0.4) / 2.6));   // খাঁজের ভিতর কম আলো ঢোকে                      // ভিতরটা স্বাভাবিকভাবেই কম আলো পায়
      var hv = n[0]*HALF[0] + n[1]*HALF[1] + n[2]*HALF[2];
      q.spec = (q.wet || 0) * 52 * Math.pow(Math.max(0, hv), 13);
    }
    quads.sort(function (a, b) { return a.d - b.d; });   // painter's algorithm

    ctx.lineWidth = 0.6;
    for (i = 0; i < quads.length; i++) {
      var qq = quads[i], p0 = cam.project(qq.v[0]), p1 = cam.project(qq.v[1]),
          p2 = cam.project(qq.v[2]), p3 = cam.project(qq.v[3]);
      var c = qq.c, s = qq.sh;
      var sp = qq.spec || 0;
      var col = 'rgb(' + Math.min(255, (c[0]*s + sp) | 0) + ',' + Math.min(255, (c[1]*s + sp) | 0) + ',' + Math.min(255, (c[2]*s + sp) | 0) + ')';
      ctx.beginPath();
      ctx.moveTo(p0[0], p0[1]); ctx.lineTo(p1[0], p1[1]); ctx.lineTo(p2[0], p2[1]); ctx.lineTo(p3[0], p3[1]);
      ctx.closePath();
      ctx.fillStyle = col; ctx.strokeStyle = col; ctx.fill(); ctx.stroke();
    }

    var vg = ctx.createRadialGradient(w/2, h/2 + h*0.04, w*0.30, w/2, h/2 + h*0.04, w*0.56);
    vg.addColorStop(0, 'rgba(240,232,222,0)'); vg.addColorStop(0.72, 'rgba(240,232,222,0.35)');
    vg.addColorStop(1, 'rgba(238,229,218,1)');
    ctx.fillStyle = vg; ctx.fillRect(0, 0, w, h);

    if (state.mode === 'piles') drawClock(ctx, cam, state);
    if (state.fistula && state.fistula.length > 1) drawTract(ctx, cam, state);
    drawMarkers(ctx, cam, state);
  }

  /* ---------- ঘড়ির কাঁটার দাগ (কোন জায়গায় পাইলস) ---------- */
  function drawClock(ctx, cam, state) {
    var R = FIELD_CM * 0.68;
    ctx.font = '600 15px system-ui, sans-serif';
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    for (var t = 1; t <= 12; t++) {
      var a = (t / 12) * Math.PI * 2 - Math.PI / 2;
      var p = cam.project([R * Math.cos(a), R * Math.sin(a), surfaceZ(R, a, state.piles || [])]);
      ctx.fillStyle = 'rgba(16,34,58,0.62)';
      ctx.fillText(String(t), p[0], p[1]);
    }
  }

  /* ---------- ফিস্টুলার নালী: আঙুল যেখান দিয়ে টানা হয়েছে ---------- */
  function drawTract(ctx, cam, state) {
    var pts = state.fistula, i;
    ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    // নিচে হালকা ছায়া, উপরে টানা লাইন — চামড়ার নিচে যাচ্ছে বোঝাতে
    for (var pass = 0; pass < 2; pass++) {
      ctx.beginPath();
      for (i = 0; i < pts.length; i++) {
        var r = pts[i][0], a = pts[i][1];
        var z = surfaceZ(r, a, state.piles || []) + 0.06;
        var p = cam.project([r * Math.cos(a), r * Math.sin(a), z]);
        if (i === 0) ctx.moveTo(p[0], p[1]); else ctx.lineTo(p[0], p[1]);
      }
      ctx.strokeStyle = pass === 0 ? 'rgba(30,10,10,0.35)' : '#E8B23A';
      ctx.lineWidth = pass === 0 ? 9 : 4.5;
      if (pass === 1) ctx.setLineDash([9, 6]);
      ctx.stroke(); ctx.setLineDash([]);
    }
  }

  /* ---------- বাইরের মুখ ও ভিতরের মুখের চিহ্ন ---------- */
  function drawMarkers(ctx, cam, state) {
    var f = state.fistula;
    if (f && f.length) {
      var e = f[f.length - 1], s = f[0];
      dot(ctx, cam, state, e[0], e[1], '#8E1B1B', 7.5);      // বাইরের মুখ
      dot(ctx, cam, state, s[0], s[1], '#F0C24A', 6);        // ভিতরের মুখ
    }
    var pl = state.piles || [];
    for (var i = 0; i < pl.length; i++) {
      if (!state.showPileNum) break;
      var p = pl[i];
      var pt = cam.project([p.r*Math.cos(p.a), p.r*Math.sin(p.a), surfaceZ(p.r, p.a, pl) + p.size + 0.25]);
      ctx.font = '700 13px system-ui, sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillStyle = 'rgba(255,255,255,0.92)';
      ctx.beginPath(); ctx.arc(pt[0], pt[1], 11, 0, Math.PI*2); ctx.fill();
      ctx.fillStyle = '#8E1B1B'; ctx.fillText(clockOf(p.a) + '', pt[0], pt[1]);
    }
  }
  function dot(ctx, cam, state, r, a, col, rad) {
    var p = cam.project([r*Math.cos(a), r*Math.sin(a), surfaceZ(r, a, state.piles || []) + 0.1]);
    ctx.beginPath(); ctx.arc(p[0], p[1], rad, 0, Math.PI*2);
    ctx.fillStyle = col; ctx.fill();
    ctx.lineWidth = 2; ctx.strokeStyle = 'rgba(255,255,255,0.9)'; ctx.stroke();
  }

  /* ---------- কোণ → ঘড়ির কাঁটা (ডাক্তারের ভাষা) ---------- */
  function clockOf(a) {
    var deg = ((a + Math.PI / 2) * 180 / Math.PI + 360) % 360;
    var t = Math.round(deg / 30); if (t === 0) t = 12;
    return t;
  }
  function angleOfClock(t) { return (t / 12) * Math.PI * 2 - Math.PI / 2; }

  /* ---------- নালীর লম্বা কত সেন্টিমিটার ---------- */
  function tractCm(pts) {
    var s = 0;
    for (var i = 1; i < pts.length; i++) {
      var x0 = pts[i-1][0]*Math.cos(pts[i-1][1]), y0 = pts[i-1][0]*Math.sin(pts[i-1][1]);
      var x1 = pts[i][0]*Math.cos(pts[i][1]),     y1 = pts[i][0]*Math.sin(pts[i][1]);
      s += Math.sqrt((x1-x0)*(x1-x0) + (y1-y0)*(y1-y0));
    }
    return Math.round(s * 10) / 10;
  }

  root.Anatomy3D = {
    render: render, surfaceZ: surfaceZ, clockOf: clockOf, angleOfClock: angleOfClock,
    tractCm: tractCm, FIELD_CM: FIELD_CM, VERGE_CM: VERGE_CM, pileWidth: pileWidth
  };
})(typeof window !== 'undefined' ? window : globalThis);
