/* Content rules and text formatting for Guest Welcome, matching the Android apps:
 * ContentValidator.kt / Validators.kt (what is accepted from the phone), Greeting.kt and
 * TimeText (Clock.kt) for what the welcome screen says. Plain ES5 for older Tizen browsers.
 */
(function () {
  'use strict';

  var NAME_MAX = 40, PROPERTY_MAX = 60, MESSAGE_MAX = 200, NOTE_MAX = 120, MAX_NOTES = 8, MAX_PLACES = 8;
  var SSID_MAX_BYTES = 32;

  function str(v) { return typeof v === 'string'; }
  function utf8Length(s) { return unescape(encodeURIComponent(s)).length; }
  function isHex(s) { return /^[0-9a-fA-F]*$/.test(s); }
  function optional(v, max) { return v === undefined || v === null || (str(v) && v.length <= max); }
  function required(v, max) { return str(v) && v.trim().length > 0 && v.length <= max; }

  function validDate(s) {
    if (!str(s) || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
    var p = s.split('-').map(Number), d = new Date(Date.UTC(p[0], p[1] - 1, p[2]));
    return d.getUTCFullYear() === p[0] && d.getUTCMonth() === p[1] - 1 && d.getUTCDate() === p[2];
  }
  function validTime(s) { return str(s) && /^([01]\d|2[0-3]):[0-5]\d$/.test(s); }

  function wifiPasswordOk(password, security) {
    if (security === 'OPEN') return true;
    if (!str(password) || password.length === 0) return false;
    if (security === 'WEP') {
      return password.length === 5 || password.length === 13 ||
        ((password.length === 10 || password.length === 26) && isHex(password));
    }
    if (password.length === 64 && isHex(password)) return true;
    return password.length >= 8 && password.length <= 63;
  }

  /** Same order and field names as ContentValidator.validate. Returns the first bad field, or null. */
  function problemIn(c) {
    if (!c || typeof c !== 'object') return 'propertyName';
    if (!required(c.propertyName, PROPERTY_MAX)) return 'propertyName';
    if (!optional(c.location, PROPERTY_MAX)) return 'location';
    if (!optional(c.guestName, NAME_MAX)) return 'guestName';
    if (!optional(c.hostMessage, MESSAGE_MAX)) return 'hostMessage';
    var wifi = c.wifi || {};
    if (!str(wifi.ssid) || !wifi.ssid.trim() || utf8Length(wifi.ssid) > SSID_MAX_BYTES) return 'wifi.ssid';
    var security = wifi.security || 'WPA';
    if (['WPA', 'WEP', 'OPEN'].indexOf(security) < 0 || !wifiPasswordOk(wifi.password, security)) return 'wifi.password';
    if (c.checkOut) {
      if (!validDate(c.checkOut.date)) return 'checkOut.date';
      if (!validTime(c.checkOut.time)) return 'checkOut.time';
    }
    var notes = c.houseNotes || [];
    if (!Array.isArray(notes) || notes.length > MAX_NOTES) return 'houseNotes';
    for (var i = 0; i < notes.length; i++) if (!optional(notes[i], NOTE_MAX)) return 'houseNotes';
    var places = c.places || [];
    if (!Array.isArray(places) || places.length > MAX_PLACES) return 'places';
    for (i = 0; i < places.length; i++) {
      if (!places[i] || !required(places[i].name, NAME_MAX) || !optional(places[i].description, NOTE_MAX)) return 'places';
    }
    if (c.hostContact) {
      if (!optional(c.hostContact.name, NAME_MAX)) return 'hostContact.name';
      if (!optional(c.hostContact.phone, 24)) return 'hostContact.phone';
      if (!optional(c.hostContact.note, NOTE_MAX)) return 'hostContact.note';
    }
    return null;
  }

  // --- formatting (English, like the Android app) ------------------------------------------
  var DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  var MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

  /** Greeting.forHour: 5–11 morning, 12–16 afternoon, 17–21 evening, else night. */
  function greeting(hour) {
    if (hour >= 5 && hour <= 11) return 'Good morning';
    if (hour >= 12 && hour <= 16) return 'Good afternoon';
    if (hour >= 17 && hour <= 21) return 'Good evening';
    return 'Good night';
  }

  /** ["7:42", "PM"] or ["19:42", null]. */
  function clock(hours, minutes, format) {
    var mm = ('0' + minutes).slice(-2);
    if (format === 'H24') return [('0' + hours).slice(-2) + ':' + mm, null];
    var h = hours % 12 === 0 ? 12 : hours % 12;
    return [h + ':' + mm, hours < 12 ? 'AM' : 'PM'];
  }
  function clockString(hours, minutes, format) {
    var c = clock(hours, minutes, format);
    return c[1] ? c[0] + ' ' + c[1] : c[0];
  }

  /** "Monday, 23 November". */
  function longDay(date) { return DAYS[date.getDay()] + ', ' + date.getDate() + ' ' + MONTHS[date.getMonth()]; }

  /** "Thu 26 Nov · 11:00 AM", or null if the check-out isn't valid. */
  function checkOutText(c) {
    var co = c && c.checkOut;
    if (!co || !validDate(co.date) || !validTime(co.time)) return null;
    var p = co.date.split('-').map(Number), d = new Date(p[0], p[1] - 1, p[2]);
    var t = co.time.split(':').map(Number);
    return DAYS[d.getDay()].slice(0, 3) + ' ' + d.getDate() + ' ' + MONTHS[d.getMonth()].slice(0, 3) +
      ' · ' + clockString(t[0], t[1], c.timeFormat);
  }

  /** The rotating line: house notes, nearby places, host contact. */
  function tickerItems(c) {
    var items = [];
    (c.houseNotes || []).forEach(function (n) { if (str(n) && n.trim()) items.push(['House note', n.trim()]); });
    (c.places || []).forEach(function (p) {
      if (p && str(p.name) && p.name.trim()) {
        items.push(['Nearby', [p.name, p.description].filter(function (s) { return str(s) && s.trim(); }).join(' — ')]);
      }
    });
    var h = c.hostContact;
    if (h) {
      var text = [h.name, h.phone, h.note].filter(function (s) { return str(s) && s.trim(); }).join(' · ');
      if (text) items.push(['Your host', text]);
    }
    return items;
  }

  window.GuestContent = {
    NAME_MAX: NAME_MAX, MESSAGE_MAX: MESSAGE_MAX,
    problemIn: problemIn, greeting: greeting, clock: clock, longDay: longDay,
    checkOutText: checkOutText, tickerItems: tickerItems
  };
})();
