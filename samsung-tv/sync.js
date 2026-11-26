/* Phone → TV sync for Guest Welcome on Samsung TVs.
 *
 * Speaks the same protocol as the Android TV app (shared/.../sync/SyncProtocol.kt): ECDH P-256
 * pairing confirmed with the 6-digit code shown on the TV, then AES-256-GCM messages with a strictly
 * increasing counter. Only the transport differs: Tizen web apps cannot listen on a socket, so
 * messages travel over the TV's own Samsung multiscreen channel (port 8001), which this app joins
 * as the host. Each message is one JSON WireMessage in an event named "guesttv".
 */
(function () {
  'use strict';

  var CHANNEL_ID = 'com.guestexperience.welcome';
  var EVENT = 'guesttv';
  var LOCAL_API = 'http://127.0.0.1:8001/api/v2/';
  var PROTOCOL_VERSION = 1;
  var CODE_TTL_MS = 5 * 60 * 1000;
  var MAX_CODE_ATTEMPTS = 5;
  var PHONES_KEY = 'guestWelcome.phones.v1';

  var subtle = window.crypto && window.crypto.subtle;
  var enc = new TextEncoder();
  var dec = new TextDecoder();

  // --- small helpers -------------------------------------------------------

  function b64(buf) {
    var bytes = new Uint8Array(buf), s = '';
    for (var i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
    return btoa(s);
  }
  function unb64(text) {
    var s = atob(text), bytes = new Uint8Array(s.length);
    for (var i = 0; i < s.length; i++) bytes[i] = s.charCodeAt(i);
    return bytes;
  }
  function randomBytes(n) { var b = new Uint8Array(n); window.crypto.getRandomValues(b); return b; }
  function constantTimeEquals(a, b) {
    if (typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length) return false;
    var diff = 0;
    for (var i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
    return diff === 0;
  }
  function loadJson(key, fallback) {
    try { var raw = localStorage.getItem(key); if (raw) return JSON.parse(raw); } catch (e) { /* ignore */ }
    return fallback;
  }
  function saveJson(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) { /* ignore */ }
  }

  // --- crypto, matching SyncCrypto.kt --------------------------------------

  function hmac(keyBytes, text) {
    return subtle.importKey('raw', keyBytes, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'])
      .then(function (k) { return subtle.sign('HMAC', k, enc.encode(text)); })
      .then(function (sig) { return new Uint8Array(sig); });
  }
  function confirmMac(pairKey, role, nonce) {
    return hmac(pairKey, 'confirm|' + role + '|' + nonce).then(b64);
  }
  function aad(direction, phoneId, counter) { return enc.encode(direction + '|' + phoneId + '|' + counter); }

  function seal(keyBytes, text, aadBytes) {
    var iv = randomBytes(12);
    return subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['encrypt'])
      .then(function (k) { return subtle.encrypt({ name: 'AES-GCM', iv: iv, additionalData: aadBytes, tagLength: 128 }, k, enc.encode(text)); })
      .then(function (ct) { return { iv: b64(iv), ciphertext: b64(ct) }; });
  }
  function open(keyBytes, ivB64, ctB64, aadBytes) {
    return subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['decrypt'])
      .then(function (k) { return subtle.decrypt({ name: 'AES-GCM', iv: unb64(ivB64), additionalData: aadBytes, tagLength: 128 }, k, unb64(ctB64)); })
      .then(function (plain) { return dec.decode(plain); });
  }

  // --- pairing code ----------------------------------------------------------

  var activeCode = null; // { code, expiresAt, failures }

  function startPairing() {
    var digits = randomBytes(6), code = '';
    for (var i = 0; i < 6; i++) code += String(digits[i] % 10);
    activeCode = { code: code, expiresAt: Date.now() + CODE_TTL_MS, failures: 0 };
    return activeCode;
  }
  function stopPairing() { activeCode = null; }
  function currentCode() {
    if (!activeCode || Date.now() >= activeCode.expiresAt) return null;
    return activeCode.code;
  }

  // --- protocol (TV side), matching TvSyncServer.kt --------------------------

  var opts = null;           // { getContent, applyContent, onPaired, onPairingClosed }
  var tvId = 'samsung-tv';
  var tvName = 'Samsung TV';
  var sessions = {};         // per channel client: pairing in progress

  function phones() { return loadJson(PHONES_KEY, {}); }
  function savePhone(p) { var all = phones(); all[p.phoneId] = p; saveJson(PHONES_KEY, all); }

  function handle(from, msg) {
    if (!msg || typeof msg.type !== 'string') return Promise.resolve({ type: 'error', error: 'BAD_PROTOCOL' });
    if (msg.type === 'pair_hello') return pairHello(from, msg);
    if (msg.type === 'pair_confirm') return pairConfirm(from, msg);
    if (msg.type === 'secure') return secure(msg);
    return Promise.resolve({ type: 'error', error: 'BAD_PROTOCOL' });
  }

  function pairHello(from, hello) {
    if (hello.protocol !== PROTOCOL_VERSION) return Promise.resolve({ type: 'pair_result', ok: false, error: 'BAD_PROTOCOL' });
    if (!currentCode()) return Promise.resolve({ type: 'pair_result', ok: false, error: 'NO_PAIRING_CODE' });
    var s = { hello: hello, nonce: b64(randomBytes(16)) };
    var curve = { name: 'ECDH', namedCurve: 'P-256' };
    return subtle.generateKey(curve, false, ['deriveBits'])
      .then(function (keys) { s.keys = keys; return subtle.exportKey('spki', keys.publicKey); })
      .then(function (spki) {
        s.tvPublic = b64(spki);
        return subtle.importKey('spki', unb64(hello.phonePublicKey), curve, false, []);
      })
      .then(function (phoneKey) { return subtle.deriveBits({ name: 'ECDH', public: phoneKey }, s.keys.privateKey, 256); })
      .then(function (secret) {
        s.secret = new Uint8Array(secret);
        sessions[from] = s;
        return { type: 'pair_challenge', tvId: tvId, tvName: tvName, tvPublicKey: s.tvPublic, nonce: s.nonce };
      });
  }

  function pairConfirm(from, confirm) {
    var s = sessions[from];
    delete sessions[from];
    var code = currentCode();
    if (!s) return Promise.resolve({ type: 'pair_result', ok: false, error: 'BAD_PROTOCOL' });
    if (!code) return Promise.resolve({ type: 'pair_result', ok: false, error: 'NO_PAIRING_CODE' });
    var h = s.hello, pairKey;
    var info = 'guesttv-pair-v1|' + code + '|' + h.phonePublicKey + '|' + s.tvPublic + '|' + s.nonce;
    return hmac(s.secret, info)
      .then(function (k) { pairKey = k; return confirmMac(pairKey, 'phone', s.nonce); })
      .then(function (expected) {
        if (!constantTimeEquals(expected, confirm.mac)) {
          activeCode.failures++;
          var stillOpen = activeCode.failures < MAX_CODE_ATTEMPTS;
          if (!stillOpen) { stopPairing(); if (opts.onPairingClosed) opts.onPairingClosed(); }
          return { type: 'pair_result', ok: false, error: stillOpen ? 'WRONG_CODE' : 'TOO_MANY_ATTEMPTS' };
        }
        stopPairing();
        var name = String(h.phoneName || 'Phone').slice(0, 40);
        savePhone({ phoneId: h.phoneId, name: name, keyB64: b64(pairKey), lastCounter: 0, pairedAtMs: Date.now() });
        if (opts.onPaired) opts.onPaired(name);
        return confirmMac(pairKey, 'tv', s.nonce).then(function (mac) { return { type: 'pair_result', ok: true, mac: mac }; });
      });
  }

  function secure(env) {
    var phone = phones()[env.phoneId];
    if (!phone) return Promise.resolve({ type: 'error', error: 'UNKNOWN_PHONE' });
    if (!(env.counter > phone.lastCounter)) return Promise.resolve({ type: 'error', error: 'REPLAY' });
    var key = unb64(phone.keyB64);
    return open(key, env.iv, env.ciphertext, aad('p2t', phone.phoneId, env.counter))
      .then(function (plain) {
        // Only an authentic message moves the counter forward.
        phone.lastCounter = env.counter;
        savePhone(phone);
        var reply;
        try {
          var cmd = JSON.parse(plain);
          if (cmd.type === 'get_content') {
            reply = { type: 'content', content: opts.getContent() };
          } else if (cmd.type === 'put_content') {
            var stamped = cmd.content || {};
            stamped.updatedAt = Date.now();
            var problemField = opts.applyContent(stamped);
            reply = problemField ? { type: 'rejected', error: 'INVALID_CONTENT', field: problemField }
                                 : { type: 'saved', updatedAt: stamped.updatedAt };
          } else {
            reply = { type: 'rejected', error: 'BAD_PROTOCOL' };
          }
        } catch (e) {
          reply = { type: 'rejected', error: 'BAD_PROTOCOL' };
        }
        return seal(key, JSON.stringify(reply), aad('t2p', phone.phoneId, env.counter))
          .then(function (box) { return { type: 'secure', phoneId: phone.phoneId, counter: env.counter, iv: box.iv, ciphertext: box.ciphertext }; });
      }, function () { return { type: 'error', error: 'DECRYPT_FAILED' }; });
  }

  // --- Samsung multiscreen channel (this app is the host) -------------------

  var ws = null, retryMs = 2000;
  // Short, human-readable link state for the pairing screen (helps when a phone can't connect).
  var status = { api: 'checking', link: 'starting', events: [] };
  function note(field, value) { status[field] = value; }
  function noteEvent(name) {
    if (status.events.indexOf(name) < 0) status.events.push(name);
    if (status.events.length > 6) status.events.shift();
  }

  function emit(to, message) {
    if (!ws || ws.readyState !== 1) return;
    ws.send(JSON.stringify({ method: 'ms.channel.emit', params: { event: EVENT, to: to, data: JSON.stringify(message) } }));
  }

  function connect() {
    var url = LOCAL_API.replace('http:', 'ws:') + 'channels/' + CHANNEL_ID + '?name=' + encodeURIComponent(btoa('Guest Welcome TV'));
    note('link', 'connecting');
    try { ws = new WebSocket(url); } catch (e) { note('link', 'blocked: ' + (e && e.message)); return retry(); }
    ws.onopen = function () { retryMs = 2000; note('link', 'open'); };
    ws.onmessage = function (e) {
      var m;
      try { m = JSON.parse(e.data); } catch (err) { return; }
      noteEvent(m.event === 'ms.error' ? 'error:' + (m.data && m.data.message) : m.event);
      if (m.event === 'ms.channel.clientDisconnect' && m.data && m.data.id) { delete sessions[m.data.id]; return; }
      if (m.event !== EVENT || !m.from) return;
      var msg;
      try { msg = typeof m.data === 'string' ? JSON.parse(m.data) : m.data; } catch (err) { msg = null; }
      handle(m.from, msg)
        .then(function (reply) { emit(m.from, reply); },
              function () { emit(m.from, { type: 'error', error: 'INTERNAL' }); });
    };
    ws.onclose = function (e) { ws = null; note('link', 'closed ' + (e && e.code)); retry(); };
    ws.onerror = function () { /* onclose follows */ };
  }
  function retry() { setTimeout(connect, retryMs); retryMs = Math.min(retryMs * 2, 30000); }

  function loadIdentity() {
    return new Promise(function (resolve) {
      var xhr = new XMLHttpRequest();
      xhr.open('GET', LOCAL_API);
      xhr.timeout = 5000;
      xhr.onload = function () {
        try {
          var info = JSON.parse(xhr.responseText);
          tvId = (info.device && info.device.id) || info.id || tvId;
          tvName = String((info.device && info.device.name) || info.name || tvName).replace(/^\[TV\]\s*/, '');
          note('api', 'ok');
        } catch (e) { note('api', 'unreadable ' + xhr.status); }
        resolve();
      };
      xhr.onerror = function () { note('api', 'error'); resolve(); };
      xhr.ontimeout = function () { note('api', 'timeout'); resolve(); };
      xhr.send();
    });
  }

  window.GuestSync = {
    supported: !!(subtle && window.WebSocket && window.TextEncoder),
    start: function (options) {
      opts = options;
      if (!this.supported) return;
      loadIdentity().then(connect);
    },
    startPairing: startPairing,
    stopPairing: stopPairing,
    pairedPhones: function () {
      var all = phones();
      return Object.keys(all).map(function (k) { return all[k].name; });
    },
    forgetPhones: function () { saveJson(PHONES_KEY, {}); },
    isConnected: function () { return !!ws && ws.readyState === 1; },
    statusText: function () {
      if (!this.supported) return 'Phone link: not supported on this TV (' +
        [!subtle && 'crypto', !window.WebSocket && 'websocket', !window.TextEncoder && 'text'].filter(Boolean).join(', ') + ')';
      return 'Phone link · api ' + status.api + ' · channel ' + status.link +
        (status.events.length ? ' · ' + status.events.join(', ') : '');
    }
  };
})();
