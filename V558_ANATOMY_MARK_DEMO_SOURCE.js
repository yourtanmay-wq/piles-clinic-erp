/* ============ PILES CLINIC — ছবির উপরে দাগ (V558) ============
   TK-এর নিয়ম: ডাক্তার রোগীকে বোঝানোর সময় *যে কোনো* ছবির উপরেই
   দাগ দিতে পারবেন — বইয়ের ছবি, আমাদের আঁকা ছবি, যেটাই হোক।
   দাগগুলো ছবির শতকরা হিসেবে জমা হয় (যেমন x=42%, y=63%), তাই
   ছোট ফোন, বড় ফোন, ওয়েব — সব জায়গায় একই জায়গায় বসে।
   ছবি বদলালে দাগও সেই ছবির নিজের দাগ থাকে।                      */
(function (root) {
  'use strict';

  var COLORS = { pile: '#D81E3F', tract: '#F0A400', arrow: '#1B6FD8', ring: '#12A05A', pen: '#111111' };

  /* --------- জমা হওয়ার চেহারা: এক লাইনে, নতুন ঘর লাগে না ---------
     pic=pic1|pile:42.1,63.4,বড়|tract:20,80;28,72;36,66|ring:55,40,9|note=... */
  function format(picKey, marks, note) {
    var out = ['pic=' + picKey];
    for (var i = 0; i < marks.length; i++) {
      var m = marks[i];
      if (m.kind === 'tract' || m.kind === 'pen') {
        out.push(m.kind + ':' + m.pts.map(function (p) { return n1(p[0]) + ',' + n1(p[1]); }).join(';'));
      } else if (m.kind === 'ring') {
        out.push('ring:' + n1(m.x) + ',' + n1(m.y) + ',' + n1(m.r));
      } else if (m.kind === 'arrow') {
        out.push('arrow:' + n1(m.x) + ',' + n1(m.y) + ',' + n1(m.x2) + ',' + n1(m.y2));
      } else {
        out.push('pile:' + n1(m.x) + ',' + n1(m.y) + ',' + (m.label || ''));
      }
    }
    if (note) out.push('note=' + note.replace(/[|]/g, '/'));
    return out.join('|');
  }
  function n1(v) { return Math.round(v * 10) / 10; }

  function parse(saved) {
    var res = { pic: '', marks: [], note: '' };
    if (!saved) return res;
    var parts = String(saved).split('|');
    for (var i = 0; i < parts.length; i++) {
      var t = parts[i].trim(); if (!t) continue;
      if (t.indexOf('pic=') === 0) { res.pic = t.slice(4); continue; }
      if (t.indexOf('note=') === 0) { res.note = t.slice(5); continue; }
      var c = t.indexOf(':'); if (c < 0) continue;
      var kind = t.slice(0, c), body = t.slice(c + 1);
      if (kind === 'tract' || kind === 'pen') {
        var pts = body.split(';').map(function (s) {
          var a = s.split(','); return [parseFloat(a[0]), parseFloat(a[1])];
        }).filter(function (p) { return !isNaN(p[0]) && !isNaN(p[1]); });
        if (pts.length > 1) res.marks.push({ kind: kind, pts: pts });
      } else if (kind === 'ring') {
        var r = body.split(',');
        res.marks.push({ kind: 'ring', x: +r[0], y: +r[1], r: +r[2] });
      } else if (kind === 'arrow') {
        var q = body.split(',');
        res.marks.push({ kind: 'arrow', x: +q[0], y: +q[1], x2: +q[2], y2: +q[3] });
      } else if (kind === 'pile') {
        var p2 = body.split(',');
        res.marks.push({ kind: 'pile', x: +p2[0], y: +p2[1], label: p2.slice(2).join(',') });
      }
    }
    return res;
  }

  /* --------- ছবির উপরে আঁকা --------- */
  function draw(ctx, W, H, marks, opts) {
    opts = opts || {};
    var s = Math.min(W, H) / 100;                 // সব মাপ ছবির অনুপাতে
    ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    for (var i = 0; i < marks.length; i++) {
      var m = marks[i];
      if (m.kind === 'tract' || m.kind === 'pen') {
        var w = (m.kind === 'tract' ? 2.2 : 1.4) * s;
        stroke(ctx, m.pts, W, H, 'rgba(0,0,0,0.45)', w + 1.6 * s);
        stroke(ctx, m.pts, W, H, m.kind === 'tract' ? COLORS.tract : COLORS.pen, w,
               m.kind === 'tract' ? [3.4 * s, 2.4 * s] : null);
        if (m.kind === 'tract' && opts.showCm) {
          var last = m.pts[m.pts.length - 1];
          chip(ctx, last[0] * W / 100 + 3 * s, last[1] * H / 100, tractCm(m.pts, opts) + ' সেমি', COLORS.tract, s);
        }
      } else if (m.kind === 'ring') {
        ctx.beginPath();
        ctx.ellipse(m.x * W / 100, m.y * H / 100, m.r * W / 100, m.r * W / 100 * 0.82, 0, 0, 6.3);
        ctx.strokeStyle = 'rgba(0,0,0,0.4)'; ctx.lineWidth = 2.8 * s; ctx.stroke();
        ctx.strokeStyle = COLORS.ring; ctx.lineWidth = 1.6 * s; ctx.stroke();
      } else if (m.kind === 'arrow') {
        arrow(ctx, m.x * W / 100, m.y * H / 100, m.x2 * W / 100, m.y2 * H / 100, s);
      } else if (m.kind === 'pile') {
        var x = m.x * W / 100, y = m.y * H / 100;
        ctx.beginPath(); ctx.arc(x, y, 2.4 * s, 0, 6.3);
        ctx.fillStyle = COLORS.pile; ctx.fill();
        ctx.strokeStyle = '#fff'; ctx.lineWidth = 1.1 * s; ctx.stroke();
        if (m.label) chip(ctx, x + 3.4 * s, y, m.label, COLORS.pile, s);
      }
    }
  }

  function stroke(ctx, pts, W, H, col, w, dash) {
    ctx.save();
    if (dash) ctx.setLineDash(dash);
    ctx.beginPath();
    for (var i = 0; i < pts.length; i++) {
      var x = pts[i][0] * W / 100, y = pts[i][1] * H / 100;
      if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
    }
    ctx.strokeStyle = col; ctx.lineWidth = w; ctx.stroke();
    ctx.restore();
  }
  function arrow(ctx, x1, y1, x2, y2, s) {
    var a = Math.atan2(y2 - y1, x2 - x1), hl = 4.2 * s;
    ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2);
    ctx.strokeStyle = 'rgba(0,0,0,0.4)'; ctx.lineWidth = 3.2 * s; ctx.stroke();
    ctx.strokeStyle = COLORS.arrow; ctx.lineWidth = 1.8 * s; ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(x2, y2);
    ctx.lineTo(x2 - hl * Math.cos(a - 0.42), y2 - hl * Math.sin(a - 0.42));
    ctx.lineTo(x2 - hl * Math.cos(a + 0.42), y2 - hl * Math.sin(a + 0.42));
    ctx.closePath(); ctx.fillStyle = COLORS.arrow; ctx.fill();
  }
  function chip(ctx, x, y, txt, col, s) {
    ctx.font = '700 ' + (3.1 * s).toFixed(1) + 'px "Noto Sans Bengali", system-ui, sans-serif';
    ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
    var w = ctx.measureText(txt).width + 2.6 * s, h = 5.2 * s, r = 2.6 * s;
    ctx.beginPath();
    ctx.moveTo(x + r, y - h / 2); ctx.lineTo(x + w - r, y - h / 2);
    ctx.quadraticCurveTo(x + w, y - h / 2, x + w, y); ctx.quadraticCurveTo(x + w, y + h / 2, x + w - r, y + h / 2);
    ctx.lineTo(x + r, y + h / 2); ctx.quadraticCurveTo(x, y + h / 2, x, y);
    ctx.quadraticCurveTo(x, y - h / 2, x + r, y - h / 2);
    ctx.closePath();
    ctx.fillStyle = 'rgba(255,255,255,0.94)'; ctx.fill();
    ctx.strokeStyle = col; ctx.lineWidth = 0.9 * s; ctx.stroke();
    ctx.fillStyle = col; ctx.fillText(txt, x + 1.3 * s, y + 0.2 * s);
  }

  /* নালীর লম্বা — ছবির গায়ে দেওয়া মাপকাঠি অনুযায়ী (cmPerPct) */
  function tractCm(pts, opts) {
    var k = (opts && opts.cmPerPct) || 0.09, sum = 0;
    for (var i = 1; i < pts.length; i++) {
      var dx = pts[i][0] - pts[i - 1][0], dy = pts[i][1] - pts[i - 1][1];
      sum += Math.sqrt(dx * dx + dy * dy);
    }
    return Math.round(sum * k * 10) / 10;
  }

  root.AnatomyMark = { format: format, parse: parse, draw: draw, tractCm: tractCm, COLORS: COLORS };
})(typeof window !== 'undefined' ? window : globalThis);
