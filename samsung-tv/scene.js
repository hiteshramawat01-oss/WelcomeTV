/* The welcome background: Lake Pichola at dusk or night, the same 1920×1080 vector scene as the
 * Android app (tv/.../ui/components/UdaipurScene.kt), as SVG. "plain" is a dark CSS gradient.
 */
(function () {
  'use strict';

  var PALACE = 'M980 568h860v72H980zM1040 512h170v60h-170zM1230 478h230v94h-230zM1478 440h150v132h-150z' +
    'M1646 496h160v76h-160zM1066 512a22 22 0 0 1 44 0zM1144 512a22 22 0 0 1 44 0zM1250 478a15 15 0 0 1 30 0z' +
    'M1312 478a15 15 0 0 1 30 0zM1374 478a15 15 0 0 1 30 0zM1426 478a15 15 0 0 1 30 0zM1494 440a26 26 0 0 1 52 0z' +
    'M1562 440a26 26 0 0 1 52 0zM1668 496a18 18 0 0 1 36 0zM1752 496a18 18 0 0 1 36 0zM1518 402h4v14h-4z' +
    'M1586 402h4v14h-4zM1086 480h4v12h-4zM1164 480h4v12h-4z';
  var WINDOWS = [[1260, 520, 14], [1300, 520, 14], [1340, 520, 14], [1380, 520, 14], [1420, 520, 14],
    [1500, 480, 16], [1540, 480, 16], [1580, 480, 16], [1500, 530, 16], [1580, 530, 16],
    [1070, 534, 12], [1120, 534, 12], [1170, 534, 12], [1680, 524, 12], [1730, 524, 12], [1780, 524, 12]];

  function stops(list) {
    return list.map(function (s) { return '<stop offset="' + s[0] + '" stop-color="' + s[1] + '"/>'; }).join('');
  }

  /** "udaipur_night" → night, "plain" → plain, anything else → dusk (incl. a photo only Android TV has). */
  function kindOf(background) {
    var id = background && background.scene;
    if (id === 'plain' && !(background && background.useCustomPhoto)) return 'plain';
    if (id === 'udaipur_night' && !(background && background.useCustomPhoto)) return 'night';
    return 'dusk';
  }

  function svg(kind) {
    var night = kind === 'night';
    var sky = night ? [[0, '#07060D'], [0.55, '#1C1426'], [1, '#3B2433']]
      : [[0, '#110D1B'], [0.45, '#3A2238'], [0.8, '#B4553F'], [1, '#E8A35E']];
    var lake = night ? [[0, '#241824'], [1, '#050607']] : [[0, '#5A2E33'], [0.35, '#24161F'], [1, '#0A0C0F']];
    var windowAlpha = night ? 0.7 : 0.85;
    var out = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1920 1080" preserveAspectRatio="xMidYMid slice" aria-hidden="true">' +
      '<defs>' +
      '<linearGradient id="sky" x1="0" y1="0" x2="0" y2="640" gradientUnits="userSpaceOnUse">' + stops(sky) + '</linearGradient>' +
      '<linearGradient id="lake" x1="0" y1="640" x2="0" y2="1080" gradientUnits="userSpaceOnUse">' + stops(lake) + '</linearGradient>' +
      '<radialGradient id="moonGlow" cx="1250" cy="190" r="120" gradientUnits="userSpaceOnUse">' +
      '<stop offset="0" stop-color="#E9E3D8" stop-opacity="0.16"/><stop offset="1" stop-color="#E9E3D8" stop-opacity="0"/></radialGradient>' +
      '</defs>' +
      '<rect width="1920" height="640" fill="url(#sky)"/>';
    if (night) {
      out += '<circle cx="1250" cy="190" r="120" fill="url(#moonGlow)"/><circle cx="1250" cy="190" r="34" fill="#E9E3D8" fill-opacity="0.8"/>';
    } else {
      out += '<circle cx="760" cy="606" r="44" fill="#F6C27E" fill-opacity="0.9"/>';
    }
    out += '<path d="M0 600C220 548 420 566 620 590C780 610 900 560 1100 556C1360 552 1600 520 1920 548L1920 640L0 640Z" fill="' + (night ? '#20172A' : '#4A2A36') + '"/>';
    if (!night) out += '<path d="M0 622C260 600 520 612 760 624C1000 636 1300 604 1920 612L1920 640L0 640Z" fill="#2C1B26"/>';
    out += '<path d="' + PALACE + '" fill="' + (night ? '#0E0A12' : '#1A1220') + '"/>';
    out += '<g fill="#F0B86A" fill-opacity="' + windowAlpha + '">' +
      WINDOWS.map(function (w) { return '<rect x="' + w[0] + '" y="' + w[1] + '" width="8" height="' + w[2] + '"/>'; }).join('') + '</g>';
    out += '<rect x="1010" y="600" width="820" height="3" fill="#F0B86A" fill-opacity="' + (windowAlpha * 0.4) + '"/>';
    out += '<rect y="640" width="1920" height="440" fill="url(#lake)"/>';
    out += '<path d="' + PALACE + '" transform="translate(0 1280) scale(1 -1)" fill="#120C16" fill-opacity="' + (night ? 0.22 : 0.28) + '"/>';
    if (!night) {
      out += '<g fill="#F6C27E"><rect x="700" y="660" width="120" height="3" fill-opacity="0.5"/><rect x="680" y="690" width="160" height="2" fill-opacity="0.35"/>' +
        '<rect x="720" y="724" width="90" height="2" fill-opacity="0.3"/><rect x="660" y="770" width="200" height="2" fill-opacity="0.18"/></g>';
    }
    return out + '</svg>';
  }

  window.GuestScene = {
    kindOf: kindOf,
    /** Fills [element] with the scene for [background]; returns the kind drawn. */
    draw: function (element, background) {
      var kind = kindOf(background);
      if (element.getAttribute('data-kind') === kind) return kind;
      element.setAttribute('data-kind', kind);
      element.className = 'scene scene-' + kind;
      element.innerHTML = kind === 'plain' ? '' : svg(kind);
      return kind;
    }
  };
})();
