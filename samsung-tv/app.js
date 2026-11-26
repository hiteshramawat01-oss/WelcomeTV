/* Guest Welcome for Samsung (Tizen) TVs: the "Welcome v2" screen shared with the Android TV app.
 * The guest name and message can be set on the TV with the remote; everything (incl. Wi-Fi,
 * check-out, notes, places) comes from the paired Guest Experience Host phone app (sync.js).
 * Kept in local storage. Uses content.js (rules, text), scene.js (background), qr.js (Wi-Fi code).
 */
(function () {
  'use strict';

  var CONTENT_KEY = 'guestWelcome.content.v2';
  var LEGACY_KEY = 'guestWelcome.v1';
  var DEFAULT_MESSAGE = "We're so glad you're here. Make yourself at home and enjoy your stay.";
  var TICKER_MS = 12000;
  var C = window.GuestContent;

  var KEY = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13, BACK: 10009, ESC: 27 };

  function $(id) { return document.getElementById(id); }
  var welcome = $('welcome'), settings = $('settings'), pairing = $('pairing');
  var overlay = $('qr-overlay'), wifiCard = $('wifi-card'), btnSettings = $('btn-settings');
  var inputName = $('input-name'), inputMessage = $('input-message');
  var btnSave = $('btn-save'), btnCancel = $('btn-cancel'), btnPair = $('btn-pair');
  var btnPairDone = $('btn-pair-done'), btnForget = $('btn-forget');
  var pairCode = $('pair-code'), pairStatus = $('pair-status'), phonesLine = $('paired-phones');

  // --- content -----------------------------------------------------------------
  // The full PropertyContent JSON from the phone is kept as-is, so fields this TV doesn't use
  // (a photo background, which only Android TV has) survive a round trip.

  function loadContent() {
    if (/[?&]demo\b/.test(location.search)) return demoContent();
    try {
      var raw = localStorage.getItem(CONTENT_KEY);
      if (raw) return JSON.parse(raw);
      var legacy = localStorage.getItem(LEGACY_KEY);
      if (legacy) {
        var old = JSON.parse(legacy);
        return { schemaVersion: 1, guestName: old.name || '', hostMessage: old.message || '' };
      }
    } catch (e) { /* storage unavailable or corrupt: start fresh */ }
    return null;
  }

  function saveContent(c) {
    if (/[?&]demo\b/.test(location.search)) return;
    try { localStorage.setItem(CONTENT_KEY, JSON.stringify(c)); } catch (e) { /* ignore */ }
  }

  /** Browser preview only (index.html?demo or ?demo=night / ?demo=plain): sample stay, never saved. */
  function demoContent() {
    var scene = /demo=night/.test(location.search) ? 'udaipur_night' : /demo=plain/.test(location.search) ? 'plain' : 'udaipur_dusk';
    return {
      schemaVersion: 1, propertyName: 'Pichola Haveli', location: 'Udaipur', guestName: 'Ananya',
      hostMessage: 'Chai is on the house. Make yourself at home, and ring us any time you need anything.',
      wifi: { ssid: 'Pichola-Guest', password: 'lakeview2026', security: 'WPA', hidden: false },
      checkOut: { date: '2026-11-27', time: '11:00' },
      houseNotes: ['Rooftop sunset is best at 5:30'],
      places: [{ name: 'Ambrai Ghat', description: 'Sunset views across the lake' }],
      hostContact: { name: 'Hitesh', phone: '+91 98765 43210', note: 'Call any time before 10 pm' },
      background: { scene: scene, useCustomPhoto: false }, timeFormat: 'H12'
    };
  }

  var content = loadContent();

  // --- welcome screen -------------------------------------------------------------

  function setText(id, text) { $(id).textContent = text || ''; }
  function show(el, visible) { el.hidden = !visible; }

  function renderClock() {
    var c = content || {}, now = new Date();
    var t = C.clock(now.getHours(), now.getMinutes(), c.timeFormat);
    setText('time', t[0]);
    setText('ampm', t[1]);
    show($('ampm'), !!t[1]);
    setText('date', C.longDay(now));
    setText('greet', C.greeting(now.getHours()));
  }

  var ticker = { items: [], index: 0, timer: null };

  function renderTicker() {
    var item = ticker.items[ticker.index];
    if (!item) return;
    setText('ticker-label', item[0]);
    setText('ticker-text', item[1]);
    var dots = $('ticker-dots');
    dots.innerHTML = '';
    if (ticker.items.length > 1) {
      ticker.items.forEach(function (_, i) {
        var d = document.createElement('i');
        if (i === ticker.index) d.className = 'on';
        dots.appendChild(d);
      });
    }
    show(dots, ticker.items.length > 1);
  }

  function nextTicker() {
    var text = $('ticker-text');
    text.className = 'value fading';
    setTimeout(function () {
      ticker.index = (ticker.index + 1) % ticker.items.length;
      renderTicker();
      text.className = 'value';
    }, 500);
  }

  function render() {
    var c = content || {};
    GuestScene.draw($('scene'), c.background);

    var place = [c.propertyName, c.location].filter(function (s) { return s && String(s).trim(); }).join(' · ');
    setText('property-text', place);
    show($('property'), !!place);

    var name = (c.guestName || '').trim();
    setText('guest-name', name || 'Welcome');
    show($('welcome-word'), !!name);
    setText('guest-message', (c.hostMessage || '').trim() || DEFAULT_MESSAGE);
    renderClock();

    var checkOut = C.checkOutText(c);
    setText('checkout', checkOut);
    show($('checkout-row'), !!checkOut);
    ticker.items = C.tickerItems(c);
    ticker.index = 0;
    clearInterval(ticker.timer);
    show($('ticker-row'), ticker.items.length > 0);
    show($('stay-divider'), !!checkOut && ticker.items.length > 0);
    show($('stay'), !!checkOut || ticker.items.length > 0);
    renderTicker();
    if (ticker.items.length > 1) ticker.timer = setInterval(nextTicker, TICKER_MS);

    var wifi = c.wifi || {};
    var hasWifi = !!(wifi.ssid && String(wifi.ssid).trim());
    show(wifiCard, hasWifi);
    if (hasWifi) {
      var payload = GuestQr.wifiPayload(wifi);
      $('wifi-qr').innerHTML = GuestQr.svg(payload, 200, '#0A0C0F', '#F6F1E8');
      $('big-qr').innerHTML = GuestQr.svg(payload, 600, '#0A0C0F', '#F6F1E8');
      var open = wifi.security === 'OPEN';
      setText('wifi-ssid', wifi.ssid);
      setText('wifi-pass', open ? '' : wifi.password);
      show($('wifi-pass'), !open);
      setText('big-ssid', wifi.ssid);
      setText('big-pass', open ? 'No password' : 'Password · ' + wifi.password);
      wifiCard.setAttribute('aria-label', 'Wi-Fi ' + wifi.ssid + '. Press OK to enlarge the QR code.');
    }
  }

  // Clock: redraw on the minute.
  (function tick() {
    if (!welcome.hidden) renderClock();
    var now = new Date();
    setTimeout(tick, 60000 - (now.getSeconds() * 1000 + now.getMilliseconds()) + 50);
  })();

  // --- screens -----------------------------------------------------------------------

  var focusOrder = [];

  function screen(el) {
    welcome.hidden = el !== welcome;
    settings.hidden = el !== settings;
    pairing.hidden = el !== pairing;
    if (document.activeElement) document.activeElement.blur();
  }

  function welcomeTargets() {
    return [wifiCard, btnSettings].filter(function (el) { return !el.hidden; });
  }

  function showWelcome() {
    if (window.GuestSync) GuestSync.stopPairing();
    overlay.hidden = true;
    render();
    screen(welcome);
    focusOrder = [];
    welcomeTargets()[0].focus();
    welcome.classList.remove('shown');
    window.requestAnimationFrame(function () { welcome.classList.add('shown'); });
  }

  function showSettings() {
    if (window.GuestSync) GuestSync.stopPairing();
    inputName.value = content ? content.guestName || '' : '';
    inputMessage.value = content ? content.hostMessage || DEFAULT_MESSAGE : DEFAULT_MESSAGE;
    btnPair.hidden = !(window.GuestSync && GuestSync.supported);
    screen(settings);
    focusOrder = [inputName, inputMessage, btnSave, btnCancel, btnPair].filter(function (el) { return !el.hidden; });
    inputName.focus();
  }

  var countdown = null;

  function showPairing() {
    var active = GuestSync.startPairing();
    pairCode.textContent = active.code.slice(0, 3) + ' ' + active.code.slice(3);
    pairStatus.textContent = '';
    delete pairStatus.dataset.final;
    $('link-status').textContent = GuestSync.statusText();
    updatePhonesLine();
    screen(pairing);
    focusOrder = [btnPairDone, btnForget];
    btnPairDone.focus();
    clearInterval(countdown);
    countdown = setInterval(function () {
      if (pairing.hidden) { clearInterval(countdown); return; }
      $('link-status').textContent = GuestSync.statusText();
      if (pairStatus.dataset.final) return;
      var left = Math.max(0, Math.ceil((active.expiresAt - Date.now()) / 1000));
      if (left === 0) {
        pairCode.textContent = '— — —';
        pairStatus.textContent = 'Code expired. Choose Done, then Pair a phone again.';
        clearInterval(countdown);
        return;
      }
      pairStatus.textContent = 'Code works for ' + Math.floor(left / 60) + ':' + ('0' + (left % 60)).slice(-2);
    }, 1000);
  }

  function updatePhonesLine() {
    var names = GuestSync.pairedPhones();
    phonesLine.textContent = names.length ? 'Paired: ' + names.join(', ') : 'No phones paired yet.';
    btnForget.hidden = names.length === 0;
    focusOrder = [btnPairDone, btnForget].filter(function (el) { return !el.hidden; });
  }

  function moveFocus(step) {
    if (!focusOrder.length) return;
    var i = focusOrder.indexOf(document.activeElement);
    i = i < 0 ? 0 : Math.min(focusOrder.length - 1, Math.max(0, i + step));
    focusOrder[i].focus();
  }

  function openQr() {
    overlay.hidden = false;
    overlay.focus();
  }
  function closeQr() {
    overlay.hidden = true;
    wifiCard.focus();
  }

  wifiCard.addEventListener('click', openQr);
  btnSettings.addEventListener('click', showSettings);
  overlay.addEventListener('click', closeQr);

  btnSave.addEventListener('click', function () {
    content = content || { schemaVersion: 1 };
    content.guestName = inputName.value.trim().slice(0, C.NAME_MAX);
    content.hostMessage = (inputMessage.value.trim() || DEFAULT_MESSAGE).slice(0, C.MESSAGE_MAX);
    content.updatedAt = Date.now();
    saveContent(content);
    showWelcome();
  });
  btnCancel.addEventListener('click', function () { if (content) showWelcome(); else inputName.focus(); });
  btnPair.addEventListener('click', showPairing);
  btnPairDone.addEventListener('click', function () { if (content) showWelcome(); else showSettings(); });
  btnForget.addEventListener('click', function () {
    GuestSync.forgetPhones();
    updatePhonesLine();
    pairStatus.textContent = 'All phones removed. They need to pair again.';
    pairStatus.dataset.final = '1';
    btnPairDone.focus();
  });

  // --- remote control ----------------------------------------------------------------

  document.addEventListener('keydown', function (e) {
    var k = e.keyCode;

    if (!welcome.hidden) {
      if (!overlay.hidden) {
        // Enlarged QR: OK or Back closes it.
        if (k === KEY.ENTER || k === KEY.BACK || k === KEY.ESC) { e.preventDefault(); closeQr(); }
        return;
      }
      // Two targets: Wi-Fi card (bottom right) and settings (top right). Edges keep focus.
      if (k === KEY.UP || k === KEY.RIGHT) {
        btnSettings.focus();
        e.preventDefault();
      } else if (k === KEY.DOWN || k === KEY.LEFT) {
        (wifiCard.hidden ? btnSettings : wifiCard).focus();
        e.preventDefault();
      } else if (k === KEY.BACK) {
        e.preventDefault();
        exitApp();
      } else if (k === KEY.ENTER && !welcomeTargets().some(function (el) { return el === document.activeElement; })) {
        // Focus got lost (e.g. after a phone update): put it back instead of doing nothing.
        welcomeTargets()[0].focus();
        e.preventDefault();
      }
      return;
    }

    var typing = document.activeElement === inputName || document.activeElement === inputMessage;
    if (k === KEY.DOWN) { moveFocus(1); e.preventDefault(); }
    else if (k === KEY.UP) { moveFocus(-1); e.preventDefault(); }
    else if (!typing && k === KEY.RIGHT) { moveFocus(1); e.preventDefault(); }
    else if (!typing && k === KEY.LEFT) { moveFocus(-1); e.preventDefault(); }
    else if (k === KEY.BACK || k === KEY.ESC) {
      e.preventDefault();
      if (!pairing.hidden) showSettings();
      else if (content) showWelcome();
    }
  });

  function exitApp() {
    try { window.tizen.application.getCurrentApplication().exit(); } catch (e) { /* not on a TV */ }
  }

  // Keep the TV's screensaver from covering the welcome screen.
  function keepScreenOn() {
    try {
      var ac = window.webapis && window.webapis.appcommon;
      if (ac) ac.setScreenSaver(ac.AppCommonScreenSaverState.SCREEN_SAVER_OFF);
    } catch (e) { /* not on a TV, or not supported */ }
  }
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden) { keepScreenOn(); if (!welcome.hidden) renderClock(); }
  });

  // --- phone sync ----------------------------------------------------------------------
  if (window.GuestSync) {
    GuestSync.start({
      getContent: function () {
        var c = content || {};
        c.schemaVersion = c.schemaVersion || 1;
        c.guestName = c.guestName || '';
        c.hostMessage = c.hostMessage || '';
        return c;
      },
      applyContent: function (incoming) {
        var problem = C.problemIn(incoming);
        if (problem) return problem;
        content = incoming;
        saveContent(content);
        // A phone update shows straight away, unless someone is in settings.
        if (!welcome.hidden) { var hadFocus = document.activeElement; render(); if (hadFocus && !hadFocus.hidden) hadFocus.focus(); else welcomeTargets()[0].focus(); }
        else if (settings.hidden) showWelcome();
        return null;
      },
      onPaired: function (phoneName) {
        pairStatus.textContent = 'Paired with ' + phoneName + '. You can now update this TV from the phone.';
        pairStatus.dataset.final = '1';
        pairCode.textContent = '✓';
        updatePhonesLine();
      },
      onPairingClosed: function () {
        pairStatus.textContent = 'Too many wrong codes. Choose Done, then Pair a phone again.';
        pairStatus.dataset.final = '1';
        pairCode.textContent = '— — —';
      }
    });
  }

  keepScreenOn();
  if (content) showWelcome(); else showSettings();
})();
