/* Guest Welcome for Samsung (Tizen) TVs.
 * Shows a guest name and a welcome message. They are set on the TV with the remote, or sent from
 * the Guest Experience Host phone app once it is paired (see sync.js). Kept in local storage.
 */
(function () {
  'use strict';

  var CONTENT_KEY = 'guestWelcome.content.v2';
  var LEGACY_KEY = 'guestWelcome.v1';
  var DEFAULT_MESSAGE = "We're so glad you're here. Make yourself at home and enjoy your stay.";
  var HOLD_TO_EDIT_MS = 3000;
  var NAME_MAX = 40;
  var MESSAGE_MAX = 200;

  var KEY = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13, BACK: 10009, ESC: 27 };

  function $(id) { return document.getElementById(id); }
  var welcome = $('welcome');
  var settings = $('settings');
  var pairing = $('pairing');
  var nameEl = $('guest-name');
  var messageEl = $('guest-message');
  var inputName = $('input-name');
  var inputMessage = $('input-message');
  var btnSave = $('btn-save');
  var btnCancel = $('btn-cancel');
  var btnPair = $('btn-pair');
  var btnPairDone = $('btn-pair-done');
  var btnForget = $('btn-forget');
  var pairCode = $('pair-code');
  var pairStatus = $('pair-status');
  var phonesLine = $('paired-phones');
  var settingsFocus = [inputName, inputMessage, btnSave, btnCancel, btnPair];
  var pairingFocus = [btnPairDone, btnForget];

  // --- content ---------------------------------------------------------------
  // The full PropertyContent JSON from the phone is kept as-is, so fields this TV doesn't show
  // (Wi-Fi, house notes…) survive a round trip. Only guestName and hostMessage are displayed.

  function loadContent() {
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
    try { localStorage.setItem(CONTENT_KEY, JSON.stringify(c)); } catch (e) { /* ignore */ }
  }

  var content = loadContent();

  /** Returns the name of the field that is not acceptable, or null. */
  function problemIn(c) {
    if (!c || typeof c !== 'object') return 'guestName';
    if (typeof c.guestName !== 'string' || c.guestName.length > NAME_MAX) return 'guestName';
    if (typeof c.hostMessage !== 'string' || c.hostMessage.length > MESSAGE_MAX) return 'hostMessage';
    return null;
  }

  function render() {
    var c = content || {};
    nameEl.textContent = (c.guestName || '').trim() || 'Our Guest';
    messageEl.textContent = (c.hostMessage || '').trim() || DEFAULT_MESSAGE;
  }

  // --- screens ---------------------------------------------------------------

  var focusOrder = [];

  function show(screen) {
    welcome.hidden = screen !== welcome;
    settings.hidden = screen !== settings;
    pairing.hidden = screen !== pairing;
    if (document.activeElement) document.activeElement.blur();
  }

  function showWelcome() {
    window.GuestSync && GuestSync.stopPairing();
    render();
    show(welcome);
    focusOrder = [];
    // Fade in on the next frame.
    welcome.classList.remove('shown');
    window.requestAnimationFrame(function () { welcome.classList.add('shown'); });
  }

  function showSettings() {
    window.GuestSync && GuestSync.stopPairing();
    inputName.value = content ? content.guestName || '' : '';
    inputMessage.value = content ? content.hostMessage || DEFAULT_MESSAGE : DEFAULT_MESSAGE;
    btnPair.hidden = !(window.GuestSync && GuestSync.supported);
    show(settings);
    focusOrder = settingsFocus.filter(function (el) { return !el.hidden; });
    inputName.focus();
  }

  var countdown = null;

  function showPairing() {
    var active = GuestSync.startPairing();
    pairCode.textContent = active.code.slice(0, 3) + ' ' + active.code.slice(3);
    pairStatus.textContent = '';
    $('link-status').textContent = GuestSync.statusText();
    updatePhonesLine();
    show(pairing);
    focusOrder = pairingFocus;
    btnPairDone.focus();
    clearInterval(countdown);
    countdown = setInterval(function () {
      var left = Math.max(0, Math.ceil((active.expiresAt - Date.now()) / 1000));
      if (pairing.hidden) { clearInterval(countdown); return; }
      $('link-status').textContent = GuestSync.statusText();
      if (left === 0) { pairCode.textContent = '— — —'; pairStatus.textContent = 'Code expired. Press OK on Done and open Pair a phone again.'; clearInterval(countdown); return; }
      if (!pairStatus.dataset.final) pairStatus.textContent = 'Code works for ' + Math.floor(left / 60) + ':' + ('0' + (left % 60)).slice(-2);
    }, 1000);
    delete pairStatus.dataset.final;
  }

  function updatePhonesLine() {
    var names = GuestSync.pairedPhones();
    phonesLine.textContent = names.length ? 'Paired: ' + names.join(', ') : 'No phones paired yet.';
    btnForget.hidden = names.length === 0;
  }

  function current(screen) { return !screen.hidden; }

  function moveFocus(step) {
    if (!focusOrder.length) return;
    var i = focusOrder.indexOf(document.activeElement);
    i = i < 0 ? 0 : Math.min(focusOrder.length - 1, Math.max(0, i + step));
    focusOrder[i].focus();
  }

  btnSave.addEventListener('click', function () {
    content = content || { schemaVersion: 1 };
    content.guestName = inputName.value.trim().slice(0, NAME_MAX);
    content.hostMessage = (inputMessage.value.trim() || DEFAULT_MESSAGE).slice(0, MESSAGE_MAX);
    content.updatedAt = Date.now();
    saveContent(content);
    showWelcome();
  });

  btnCancel.addEventListener('click', function () {
    if (content) showWelcome(); else inputName.focus();
  });

  btnPair.addEventListener('click', showPairing);
  btnPairDone.addEventListener('click', function () { if (content) showWelcome(); else showSettings(); });
  btnForget.addEventListener('click', function () {
    GuestSync.forgetPhones();
    updatePhonesLine();
    pairStatus.textContent = 'All phones removed. They need to pair again.';
    pairStatus.dataset.final = '1';
    btnPairDone.focus();
  });

  // --- remote control --------------------------------------------------------
  var PRESSES_TO_EDIT = 5;
  var PRESS_WINDOW_MS = 3000;
  var holdStart = 0, holdTimer = null, presses = [];

  function resetHold() { holdStart = 0; clearTimeout(holdTimer); holdTimer = null; }
  function openFromWelcome() { resetHold(); presses = []; if (current(welcome)) showSettings(); }

  document.addEventListener('keydown', function (e) {
    var k = e.keyCode;

    if (!current(welcome)) {
      var typing = document.activeElement === inputName || document.activeElement === inputMessage;
      if (k === KEY.DOWN) { moveFocus(1); e.preventDefault(); }
      else if (k === KEY.UP) { moveFocus(-1); e.preventDefault(); }
      else if (!typing && k === KEY.RIGHT) { moveFocus(1); e.preventDefault(); }
      else if (!typing && k === KEY.LEFT) { moveFocus(-1); e.preventDefault(); }
      else if (k === KEY.BACK || k === KEY.ESC) {
        e.preventDefault();
        if (current(pairing)) showSettings();
        else if (content) showWelcome();
      }
      return;
    }

    // Welcome screen: hold OK for 3 seconds, or press it 5 times quickly, to open settings.
    // (Some remotes send no repeats while a button is held, others send a press/release pair per repeat.)
    if (k === KEY.ENTER) {
      e.preventDefault();
      var now = Date.now();
      presses = presses.filter(function (t) { return now - t < PRESS_WINDOW_MS; });
      presses.push(now);
      if (presses.length >= PRESSES_TO_EDIT) { openFromWelcome(); return; }
      if (!holdStart) holdStart = now;
      else if (now - holdStart >= HOLD_TO_EDIT_MS) { openFromWelcome(); return; }
      if (!holdTimer) holdTimer = setTimeout(openFromWelcome, HOLD_TO_EDIT_MS);
    } else if (k === KEY.BACK) {
      e.preventDefault();
      exitApp();
    }
  });

  document.addEventListener('keyup', function (e) {
    if (e.keyCode === KEY.ENTER) resetHold();
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
    if (!document.hidden) keepScreenOn();
  });

  // --- phone sync --------------------------------------------------------------
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
        var problem = problemIn(incoming);
        if (problem) return problem;
        content = incoming;
        saveContent(content);
        // A phone update shows on the welcome screen straight away, unless someone is in settings.
        if (current(welcome)) render();
        else if (!current(settings)) showWelcome();
        return null;
      },
      onPaired: function (phoneName) {
        pairStatus.textContent = 'Paired with ' + phoneName + '. You can now update this TV from the phone.';
        pairStatus.dataset.final = '1';
        pairCode.textContent = '✓';
        updatePhonesLine();
      },
      onPairingClosed: function () {
        pairStatus.textContent = 'Too many wrong codes. Press OK on Done and open Pair a phone again.';
        pairStatus.dataset.final = '1';
        pairCode.textContent = '— — —';
      }
    });
  }

  keepScreenOn();
  if (content) showWelcome(); else showSettings();
})();
