/* ============ PILES CLINIC — ভিতরের কাটা ছবি (V558) ============
   ডাক্তারি বইয়ের মত পাশ থেকে কাটা ছবি: পায়ুনালী, দাঁতের রেখা,
   ভিতরের ও বাইরের পাইলস, ফিস্টুলার নালী, ফিসারের ফাটল।
   ছবিটা ১ নম্বর ছবিতে যা টিক দেওয়া হয়েছে তার সাথেই মিলে যায় —
   ঘড়ির কাঁটার নম্বর দেখেই বোঝা যায় কোনটা কোন পাশে।
   বাইরের কোনো লাইব্রেরি নেই, শুধু canvas 2D।                       */
(function (root) {
  'use strict';

  var CM = 62;                                   // ১ সেমি = কত পিক্সেল
  var VERGE_Y = 0, CANAL_CM = 3.6, DENTATE_CM = 2.0, TOP_CM = 4.6;

  function px(ctx, cx, cy, xcm, ycm) { return [cx + xcm * CM, cy - ycm * CM]; }

  // নালীর অর্ধেক চওড়া — নিচে (মুখে) সরু, উপরে (মলাশয়) চওড়া
  function halfWidth(hcm, piles, side) {
    var d = hcm / CANAL_CM;
    var w = 0.26 + 0.62 * Math.pow(Math.max(0, d), 1.05);
    for (var i = 0; i < piles.length; i++) {
      var p = piles[i];
      if (!p.inner) continue;
      if (pileSide(p) !== side) continue;
      var t = (hcm - (p.hcm || (DENTATE_CM + 0.55))) / 0.55;
      w -= p.size * 0.85 * Math.exp(-t * t);
    }
    return Math.max(0.10, w);
  }
  // ঘড়ির কাঁটা থেকে বোঝা যায় ফোলাটা বাঁ না ডান দেওয়ালে
  function pileSide(p) { return Math.cos(p.a) >= 0 ? 1 : -1; }

  var C_FAT   = '#EFDCB2', C_FAT2 = '#E3CB9C';
  var C_SKIN  = '#B0785C', C_MUSC = '#B0554F', C_MUSC2 = '#8E3C3A';
  var C_MUCO  = '#B75C6A', C_ANOD = '#D7A79A', C_LUMEN = '#3A1A20';
  var C_PILE  = '#9B2F4A', C_DENT = '#E4C98E';

  function render(ctx, W, H, st) {
    var piles = st.piles || [], cx = W * 0.5, cy = H * 0.86;
    ctx.clearRect(0, 0, W, H);
    var g = ctx.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, '#FBF6EE'); g.addColorStop(1, '#F2E9DC');
    ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);

    /* ---- চর্বি ও শরীরের মাংস ---- */
    ctx.fillStyle = C_FAT;
    ctx.beginPath();
    ctx.moveTo(cx - 3.7 * CM, cy - 5.3 * CM);
    ctx.lineTo(cx + 3.6 * CM, cy - 5.3 * CM);
    ctx.quadraticCurveTo(cx + 3.9 * CM, cy - 1.2 * CM, cx + 2.5 * CM, cy + 0.35 * CM);
    ctx.quadraticCurveTo(cx, cy + 0.95 * CM, cx - 2.5 * CM, cy + 0.35 * CM);
    ctx.quadraticCurveTo(cx - 3.9 * CM, cy - 1.2 * CM, cx - 3.7 * CM, cy - 5.3 * CM);
    ctx.closePath(); ctx.fill();
    ctx.strokeStyle = C_FAT2; ctx.lineWidth = 1;
    for (var i = 0; i < 26; i++) {                       // চর্বির দানা
      var ax = cx + (((i * 37) % 70) / 70 - 0.5) * 7 * CM;
      var ay = cy - 0.3 * CM - ((i * 53) % 45) / 45 * 4.4 * CM;
      ctx.beginPath(); ctx.arc(ax, ay, 5 + (i % 3) * 2, 0, 6.3); ctx.stroke();
    }

    /* ---- চামড়ার কিনারা ---- */
    ctx.strokeStyle = C_SKIN; ctx.lineWidth = 7; ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(cx - 3.6 * CM, cy - 0.9 * CM);
    ctx.quadraticCurveTo(cx - 2.4 * CM, cy + 0.42 * CM, cx - 0.55 * CM, cy + 0.06 * CM);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(cx + 3.6 * CM, cy - 0.9 * CM);
    ctx.quadraticCurveTo(cx + 2.4 * CM, cy + 0.42 * CM, cx + 0.55 * CM, cy + 0.06 * CM);
    ctx.stroke();

    /* ---- মাংসপেশি: ভিতরের গোল পেশি (internal sphincter) ---- */
    [-1, 1].forEach(function (side) {
      ctx.beginPath();
      wallPath(ctx, cx, cy, piles, side, 0, false, 0, CANAL_CM);
      wallPath(ctx, cx, cy, piles, side, 0.62, true, 0, CANAL_CM);
      ctx.closePath();
      var lg = ctx.createLinearGradient(cx, 0, cx + side * 2.4 * CM, 0);
      lg.addColorStop(0, C_MUSC); lg.addColorStop(1, C_MUSC2);
      ctx.fillStyle = lg; ctx.fill();
    });
    /* ---- বাইরের গোল পেশি (external sphincter) — গোল গোল কাটা মুখ ---- */
    [-1, 1].forEach(function (side) {
      for (var k = 0; k < 4; k++) {
        var hh = 0.30 + k * 0.52;
        var w = halfWidth(hh, piles, side) + 0.80;
        var p = px(ctx, cx, cy, side * w, hh);
        ctx.beginPath(); ctx.ellipse(p[0], p[1], 0.30 * CM, 0.24 * CM, 0, 0, 6.3);
        ctx.fillStyle = '#C4635A'; ctx.fill();
        ctx.strokeStyle = '#8E3C3A'; ctx.lineWidth = 1.6; ctx.stroke();
      }
    });

    /* ---- নালীর ফাঁকা জায়গা ---- */
    ctx.beginPath();
    wallPath(ctx, cx, cy, piles, -1, 0);
    wallPath(ctx, cx, cy, piles, 1, 0, true);
    ctx.closePath();
    ctx.fillStyle = C_LUMEN; ctx.fill();

    /* ---- নালীর গায়ের পর্দা: নিচে ফ্যাকাশে, উপরে লাল ---- */
    [-1, 1].forEach(function (side) {
      ctx.save(); ctx.lineWidth = 6; ctx.lineCap = 'round';
      ctx.beginPath(); wallPath(ctx, cx, cy, piles, side, 0, false, 0, DENTATE_CM);
      ctx.strokeStyle = C_ANOD; ctx.stroke();
      ctx.beginPath(); wallPath(ctx, cx, cy, piles, side, 0, false, DENTATE_CM, CANAL_CM);
      ctx.strokeStyle = C_MUCO; ctx.stroke();
      ctx.restore();
    });

    /* ---- ভিতরের পাইলস — লাল ফোলা, ঘড়ির নম্বর সহ ---- */
    var stackL = 0, stackR = 0;
    piles.forEach(function (p) {
      if (!p.inner) return;
      var side = pileSide(p);
      var n = side > 0 ? stackR++ : stackL++;
      var hh = p.hcm = DENTATE_CM + 0.50 + n * 0.62;
      var w = halfWidth(hh, piles, side);
      var c = px(ctx, cx, cy, side * (w + p.size * 0.14), hh);
      ctx.beginPath();
      ctx.ellipse(c[0], c[1], (0.14 + p.size * 0.34) * CM, (0.18 + p.size * 0.38) * CM, 0, 0, 6.3);
      var rg = ctx.createRadialGradient(c[0] - side * 6, c[1] - 8, 3, c[0], c[1], (0.3 + p.size * 0.5) * CM);
      rg.addColorStop(0, '#C4506B'); rg.addColorStop(1, C_PILE);
      ctx.fillStyle = rg; ctx.fill();
      ctx.strokeStyle = '#7C1F38'; ctx.lineWidth = 1.5; ctx.stroke();
      var tx = cx + side * 3.05 * CM;
      ctx.strokeStyle = 'rgba(124,31,56,0.55)'; ctx.lineWidth = 1.2;
      ctx.beginPath(); ctx.moveTo(c[0] + side * (0.2 + p.size * 0.4) * CM, c[1]); ctx.lineTo(tx, c[1]); ctx.stroke();
      tag(ctx, tx, c[1], clockText(p.a) + 'টা');
    });
    /* ---- বাইরের পাইলস — দাঁতের রেখার নিচে, মুখের কাছে ---- */
    piles.forEach(function (p) {
      if (p.inner) return;
      var side = pileSide(p), hh = 0.10;
      var w = halfWidth(hh, piles, side);
      var c = px(ctx, cx, cy, side * (w + 0.30 + p.size * 0.32), hh);
      ctx.beginPath();
      ctx.ellipse(c[0], c[1], (0.18 + p.size * 0.36) * CM, (0.16 + p.size * 0.32) * CM, 0, 0, 6.3);
      ctx.fillStyle = '#7E3350'; ctx.fill();
      ctx.strokeStyle = '#551F35'; ctx.lineWidth = 1.4; ctx.stroke();
      var tx2 = cx + side * 3.05 * CM;
      ctx.strokeStyle = 'rgba(85,31,53,0.55)'; ctx.lineWidth = 1.2;
      ctx.beginPath(); ctx.moveTo(c[0] + side * 0.3 * CM, c[1]); ctx.lineTo(tx2, c[1]); ctx.stroke();
      tag(ctx, tx2, c[1], clockText(p.a) + 'টা');
    });

    /* ---- দাঁতের রেখা ---- */
    var dl = px(ctx, cx, cy, 0, DENTATE_CM);
    ctx.save(); ctx.setLineDash([7, 5]); ctx.strokeStyle = '#B07A22'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(cx - 3.0 * CM, dl[1]); ctx.lineTo(cx + 3.0 * CM, dl[1]); ctx.stroke();
    ctx.restore();
    label(ctx, cx - 3.62 * CM, dl[1] - 12, 'দাঁতের রেখা · ২ সেমি', '#8A5A12', 'left');

    /* ---- ফিস্টুলার নালী ---- */
    if (st.fistula) {
      var f = st.fistula, sd = f.side || 1;
      var out = px(ctx, cx, cy, sd * (f.outCm || 2.4), -0.25);
      var inn = px(ctx, cx, cy, sd * halfWidth(f.inHcm || DENTATE_CM, piles, sd), f.inHcm || DENTATE_CM);
      ctx.save(); ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.moveTo(out[0], out[1]);
      ctx.quadraticCurveTo(out[0] - sd * 0.5 * CM, inn[1] + 0.5 * CM, inn[0], inn[1]);
      ctx.strokeStyle = '#4A2A16'; ctx.lineWidth = 11; ctx.stroke();
      ctx.strokeStyle = '#E8B23A'; ctx.lineWidth = 5; ctx.setLineDash([9, 6]); ctx.stroke();
      ctx.restore();
      dot(ctx, out[0], out[1], '#8E1B1B', 8);       // বাইরের মুখ
      dot(ctx, inn[0], inn[1], '#F0C24A', 7);       // ভিতরের মুখ
      label(ctx, out[0] + sd * 14, out[1] + 20, 'বাইরের মুখ', '#8E1B1B', sd > 0 ? 'left' : 'right');
      label(ctx, inn[0] - sd * 14, inn[1] - 16, 'ভিতরের মুখ', '#8A5A12', sd > 0 ? 'right' : 'left');
    }

    /* ---- ফিসার (ফাটল) ---- */
    if (st.fissure) {
      var fy = px(ctx, cx, cy, 0, 0.9), sdf = st.fissure.side || -1;
      var w2 = halfWidth(0.9, piles, sdf);
      ctx.save(); ctx.strokeStyle = '#C0392B'; ctx.lineWidth = 4; ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.moveTo(cx + sdf * w2 * CM, fy[1] + 0.5 * CM);
      ctx.lineTo(cx + sdf * (w2 + 0.22) * CM, fy[1]);
      ctx.lineTo(cx + sdf * (w2 + 0.05) * CM, fy[1] - 0.45 * CM);
      ctx.stroke(); ctx.restore();
      label(ctx, cx + sdf * 1.85 * CM, fy[1] - 0.75 * CM, 'ফাটল', '#C0392B', sdf > 0 ? 'left' : 'right');
    }

    /* ---- মাপকাঠি ও নাম ---- */
    scaleBar(ctx, W - 58, cy, cy - CANAL_CM * CM);
    label(ctx, cx, cy + 0.92 * CM, 'পায়ুদ্বার (বাইরের মুখ)', '#5A4030', 'center');
    label(ctx, cx, cy - (TOP_CM + 0.42) * CM, 'মলাশয় (উপরের দিকে)', '#5A4030', 'center');
    label(ctx, cx - 3.62 * CM, cy - 0.95 * CM, 'গোল মাংসপেশি', '#8E3C3A', 'left');
  }

  // নালীর এক পাশের দেওয়াল
  function wallPath(ctx, cx, cy, piles, side, off, reverse, h0, h1) {
    var lo = (h0 === undefined) ? 0 : h0, hi = (h1 === undefined) ? CANAL_CM : h1, i, first = true;
    var N = 40;
    for (i = 0; i <= N; i++) {
      var t = reverse ? (N - i) / N : i / N;
      var hh = lo + (hi - lo) * t;
      var w = halfWidth(hh, piles, side) + off;
      var p = px(ctx, cx, cy, side * w, hh);
      if (first && !reverse) { ctx.lineTo(p[0], p[1]); first = false; }
      else ctx.lineTo(p[0], p[1]);
    }
  }

  function clockText(a) {
    var deg = ((a + Math.PI / 2) * 180 / Math.PI + 360) % 360;
    var t = Math.round(deg / 30); if (t === 0) t = 12;
    return t;
  }
  function dot(ctx, x, y, col, r) {
    ctx.beginPath(); ctx.arc(x, y, r, 0, 6.3);
    ctx.fillStyle = col; ctx.fill();
    ctx.lineWidth = 2; ctx.strokeStyle = 'rgba(255,255,255,0.92)'; ctx.stroke();
  }
  function label(ctx, x, y, txt, col, al) {
    ctx.font = '600 13px "Noto Sans Bengali", system-ui, sans-serif';
    ctx.textAlign = al || 'left'; ctx.textBaseline = 'middle';
    ctx.lineWidth = 3; ctx.strokeStyle = 'rgba(255,255,255,0.85)';
    ctx.strokeText(txt, x, y); ctx.fillStyle = col; ctx.fillText(txt, x, y);
  }
  function tag(ctx, x, y, txt) {
    ctx.font = '700 12px system-ui, sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.beginPath(); ctx.arc(x, y, 13, 0, 6.3);
    ctx.fillStyle = 'rgba(255,255,255,0.94)'; ctx.fill();
    ctx.strokeStyle = '#7C1F38'; ctx.lineWidth = 1.4; ctx.stroke();
    ctx.fillStyle = '#7C1F38'; ctx.fillText(txt, x, y);
  }
  function scaleBar(ctx, x, y0, y1) {
    ctx.strokeStyle = '#6A5A48'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(x, y0); ctx.lineTo(x, y1); ctx.stroke();
    for (var c = 0; c <= 3; c++) {
      var yy = y0 - c * CM;
      ctx.beginPath(); ctx.moveTo(x - 6, yy); ctx.lineTo(x + 6, yy); ctx.stroke();
      label(ctx, x - 10, yy, c + ' সেমি', '#6A5A48', 'right');
    }
  }

  root.AnatomySection = { render: render, CM: CM, CANAL_CM: CANAL_CM, DENTATE_CM: DENTATE_CM };
})(typeof window !== 'undefined' ? window : globalThis);
