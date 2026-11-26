/* Guest Welcome for Samsung (Tizen) TVs.
 * Shows a guest name and a welcome message. Both are set on the TV with the remote
 * and kept in the app's local storage, so the screen survives restarts.
 */
(function () {
  'use strict';

  var STORAGE_KEY = 'guestWelcome.v1';
  var DEFAULT_MESSAGE = "We're so glad you're here. Make yourself at home and enjoy your stay.";
  var HOLD_TO_EDIT_MS = 3000;

  var KEY = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13, BACK: 10009, ESC: 27 };

  var welcome = document.getElementById('welcome');
  var settings = document.getElementById('settings');
  var nameEl = document.getElementById('guest-name');
  var messageEl = document.getElementById('guest-message');
  var inputName = document.getElementById('input-name');
  var inputMessage = document.getElementById('input-message');
  var btnSave = document.getElementById('btn-save');
  var btnCancel = document.getElementById('btn-cancel');
  var focusOrder = [inputName, inputMessage, btnSave, btnCancel];

  function load() {
    try {
      var raw = window.localStorage.getItem(STORAGE_KEY);
      if (raw) return JSON.parse(raw);
    } catch (e) { /* storage unavailable or corrupt: start fresh */ }
    return null;
  }

  function save(data) {
    try { window.localStorage.setItem(STORAGE_KEY, JSON.stringify(data)); } catch (e) { /* ignore */ }
  }

  var content = load();

  function showWelcome() {
    var c = content || { name: '', message: DEFAULT_MESSAGE };
    nameEl.textContent = c.name || 'Our Guest';
    messageEl.textContent = c.message || DEFAULT_MESSAGE;
    settings.hidden = true;
    welcome.hidden = false;
    if (document.activeElement) document.activeElement.blur();
    // Fade in on the next frame.
    welcome.classList.remove('shown');
    window.requestAnimationFrame(function () { welcome.classList.add('shown'); });
  }

  function showSettings() {
    inputName.value = content ? content.name : '';
    inputMessage.value = content ? content.message : DEFAULT_MESSAGE;
    welcome.hidden = true;
    settings.hidden = false;
    inputName.focus();
  }

  function inSettings() { return !settings.hidden; }

  function moveFocus(step) {
    var i = focusOrder.indexOf(document.activeElement);
    if (i < 0) i = 0; else i = Math.min(focusOrder.length - 1, Math.max(0, i + step));
    focusOrder[i].focus();
  }

  btnSave.addEventListener('click', function () {
    content = {
      name: inputName.value.trim(),
      message: inputMessage.value.trim() || DEFAULT_MESSAGE
    };
    save(content);
    showWelcome();
  });

  btnCancel.addEventListener('click', function () {
    if (content) showWelcome(); else inputName.focus();
  });

  // --- Remote control -------------------------------------------------------
  var holdStart = 0;

  document.addEventListener('keydown', function (e) {
    var k = e.keyCode;

    if (inSettings()) {
      var typing = document.activeElement === inputName || document.activeElement === inputMessage;
      if (k === KEY.DOWN) { moveFocus(1); e.preventDefault(); }
      else if (k === KEY.UP) { moveFocus(-1); e.preventDefault(); }
      else if (!typing && k === KEY.RIGHT) { moveFocus(1); e.preventDefault(); }
      else if (!typing && k === KEY.LEFT) { moveFocus(-1); e.preventDefault(); }
      else if (k === KEY.BACK || k === KEY.ESC) {
        e.preventDefault();
        if (content) showWelcome();
      }
      return;
    }

    // Welcome screen: hold OK for a few seconds to open settings.
    if (k === KEY.ENTER) {
      if (!holdStart) holdStart = Date.now();
      else if (Date.now() - holdStart >= HOLD_TO_EDIT_MS) { holdStart = 0; showSettings(); }
      e.preventDefault();
    } else if (k === KEY.BACK) {
      e.preventDefault();
      exitApp();
    }
  });

  document.addEventListener('keyup', function (e) {
    if (e.keyCode === KEY.ENTER) holdStart = 0;
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

  keepScreenOn();
  if (content) showWelcome(); else showSettings();
})();
