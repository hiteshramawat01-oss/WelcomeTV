/* QR codes for Guest Welcome on Samsung TVs: the guest Wi-Fi code.
 *
 * A small QR encoder (ISO/IEC 18004): byte mode, UTF-8, error correction level M, versions 1–15
 * (up to 412 bytes, more than the longest Wi-Fi payload). It matches what the Android apps draw
 * with zxing (shared/.../qr/QrEncoder.kt), and its output is checked by decoding with zxing.
 * Plain ES5 so it runs on older Tizen browsers.
 */
(function () {
  'use strict';

  // Level M, per version: [ec codewords per block, short blocks, data codewords per short block, long blocks].
  // A long block has one more data codeword than a short one.
  var BLOCKS_M = [null,
    [10, 1, 16, 0], [16, 1, 28, 0], [26, 1, 44, 0], [18, 2, 32, 0], [24, 2, 43, 0],
    [16, 4, 27, 0], [18, 4, 31, 0], [22, 2, 38, 2], [22, 3, 36, 2], [26, 4, 43, 1],
    [30, 1, 50, 4], [22, 6, 36, 2], [22, 8, 37, 1], [24, 4, 40, 5], [24, 5, 41, 5]];
  var ALIGN = [null, [], [6, 18], [6, 22], [6, 26], [6, 30], [6, 34], [6, 22, 38], [6, 24, 42], [6, 26, 46],
    [6, 28, 50], [6, 30, 54], [6, 32, 58], [6, 34, 62], [6, 26, 46, 66], [6, 26, 48, 70]];
  var MAX_VERSION = 15;

  function utf8(text) {
    if (typeof TextEncoder !== 'undefined') return Array.prototype.slice.call(new TextEncoder().encode(text));
    var s = unescape(encodeURIComponent(text)), out = [];
    for (var i = 0; i < s.length; i++) out.push(s.charCodeAt(i));
    return out;
  }

  // --- Reed–Solomon over GF(256), polynomial 0x11D --------------------------------------
  function gfMul(x, y) {
    var z = 0;
    for (var i = 7; i >= 0; i--) {
      z = (z << 1) ^ ((z >>> 7) * 0x11D);
      z ^= ((y >>> i) & 1) * x;
    }
    return z;
  }
  function rsDivisor(degree) {
    var result = [], root = 1, i, j;
    for (i = 0; i < degree - 1; i++) result.push(0);
    result.push(1);
    for (i = 0; i < degree; i++) {
      for (j = 0; j < result.length; j++) {
        result[j] = gfMul(result[j], root);
        if (j + 1 < result.length) result[j] ^= result[j + 1];
      }
      root = gfMul(root, 0x02);
    }
    return result;
  }
  function rsRemainder(data, divisor) {
    var result = divisor.map(function () { return 0; });
    for (var k = 0; k < data.length; k++) {
      var factor = data[k] ^ result.shift();
      result.push(0);
      for (var i = 0; i < divisor.length; i++) result[i] ^= gfMul(divisor[i], factor);
    }
    return result;
  }

  // --- data codewords ----------------------------------------------------------------------
  function dataCapacity(version) {
    var b = BLOCKS_M[version];
    return b[1] * b[2] + b[3] * (b[2] + 1);
  }

  function chooseVersion(byteCount) {
    for (var v = 1; v <= MAX_VERSION; v++) {
      var countBits = v < 10 ? 8 : 16;
      if (4 + countBits + byteCount * 8 <= dataCapacity(v) * 8) return v;
    }
    return 0;
  }

  function dataCodewords(bytes, version) {
    var bits = [], capacity = dataCapacity(version) * 8;
    function put(value, length) { for (var i = length - 1; i >= 0; i--) bits.push((value >>> i) & 1); }
    put(4, 4); // byte mode
    put(bytes.length, version < 10 ? 8 : 16);
    bytes.forEach(function (b) { put(b, 8); });
    put(0, Math.min(4, capacity - bits.length)); // terminator
    while (bits.length % 8) bits.push(0);
    for (var pad = 0xEC; bits.length < capacity; pad ^= 0xEC ^ 0x11) put(pad, 8);
    var out = [];
    for (var i = 0; i < bits.length; i += 8) {
      var byte = 0;
      for (var j = 0; j < 8; j++) byte = (byte << 1) | bits[i + j];
      out.push(byte);
    }
    return out;
  }

  /** Splits into blocks, adds error correction, interleaves. */
  function finalCodewords(data, version) {
    var b = BLOCKS_M[version], ecLen = b[0], divisor = rsDivisor(ecLen), blocks = [], pos = 0, i, k;
    for (i = 0; i < b[1] + b[3]; i++) {
      var len = b[2] + (i < b[1] ? 0 : 1);
      var dat = data.slice(pos, pos + len);
      pos += len;
      blocks.push({ data: dat, ec: rsRemainder(dat, divisor) });
    }
    var result = [];
    for (i = 0; i <= b[2]; i++) {
      for (k = 0; k < blocks.length; k++) if (i < blocks[k].data.length) result.push(blocks[k].data[i]);
    }
    for (i = 0; i < ecLen; i++) {
      for (k = 0; k < blocks.length; k++) result.push(blocks[k].ec[i]);
    }
    return result;
  }

  // --- matrix --------------------------------------------------------------------------------
  function Matrix(version) {
    this.version = version;
    this.size = version * 4 + 17;
    this.modules = [];
    this.fixed = [];
    for (var y = 0; y < this.size; y++) {
      this.modules.push(new Array(this.size));
      this.fixed.push(new Array(this.size));
      for (var x = 0; x < this.size; x++) { this.modules[y][x] = false; this.fixed[y][x] = false; }
    }
  }
  Matrix.prototype.setFunction = function (x, y, dark) {
    this.modules[y][x] = dark;
    this.fixed[y][x] = true;
  };
  Matrix.prototype.drawFunctionPatterns = function () {
    var size = this.size, i, self = this;
    for (i = 0; i < size; i++) {
      this.setFunction(6, i, i % 2 === 0);
      this.setFunction(i, 6, i % 2 === 0);
    }
    function finder(cx, cy) {
      for (var dy = -4; dy <= 4; dy++) {
        for (var dx = -4; dx <= 4; dx++) {
          var x = cx + dx, y = cy + dy;
          if (x < 0 || y < 0 || x >= size || y >= size) continue;
          var d = Math.max(Math.abs(dx), Math.abs(dy));
          self.setFunction(x, y, d !== 2 && d !== 4);
        }
      }
    }
    finder(3, 3);
    finder(size - 4, 3);
    finder(3, size - 4);
    var pos = ALIGN[this.version], n = pos.length;
    for (i = 0; i < n; i++) {
      for (var j = 0; j < n; j++) {
        if ((i === 0 && j === 0) || (i === 0 && j === n - 1) || (i === n - 1 && j === 0)) continue;
        for (var dy = -2; dy <= 2; dy++) {
          for (var dx = -2; dx <= 2; dx++) {
            this.setFunction(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
          }
        }
      }
    }
    this.drawFormatBits(0); // reserves the format areas; redrawn with the chosen mask
    this.drawVersion();
  };
  Matrix.prototype.drawFormatBits = function (mask) {
    var data = (0 << 3) | mask; // level M = 00
    var rem = data;
    for (var i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
    var bits = ((data << 10) | rem) ^ 0x5412, size = this.size;
    function bit(i) { return ((bits >>> i) & 1) !== 0; }
    for (i = 0; i <= 5; i++) this.setFunction(8, i, bit(i));
    this.setFunction(8, 7, bit(6));
    this.setFunction(8, 8, bit(7));
    this.setFunction(7, 8, bit(8));
    for (i = 9; i < 15; i++) this.setFunction(14 - i, 8, bit(i));
    for (i = 0; i < 8; i++) this.setFunction(size - 1 - i, 8, bit(i));
    for (i = 8; i < 15; i++) this.setFunction(8, size - 15 + i, bit(i));
    this.setFunction(8, size - 8, true); // dark module
  };
  Matrix.prototype.drawVersion = function () {
    if (this.version < 7) return;
    var rem = this.version;
    for (var i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
    var bits = (this.version << 12) | rem;
    for (i = 0; i < 18; i++) {
      var dark = ((bits >>> i) & 1) !== 0, a = this.size - 11 + (i % 3), b = Math.floor(i / 3);
      this.setFunction(a, b, dark);
      this.setFunction(b, a, dark);
    }
  };
  Matrix.prototype.drawCodewords = function (codewords) {
    var i = 0, size = this.size, total = codewords.length * 8;
    for (var right = size - 1; right >= 1; right -= 2) {
      if (right === 6) right = 5;
      for (var vert = 0; vert < size; vert++) {
        for (var j = 0; j < 2; j++) {
          var x = right - j, upward = ((right + 1) & 2) === 0, y = upward ? size - 1 - vert : vert;
          if (!this.fixed[y][x] && i < total) {
            this.modules[y][x] = ((codewords[i >>> 3] >>> (7 - (i & 7))) & 1) !== 0;
            i++;
          }
        }
      }
    }
  };
  function maskHit(mask, x, y) {
    switch (mask) {
      case 0: return (x + y) % 2 === 0;
      case 1: return y % 2 === 0;
      case 2: return x % 3 === 0;
      case 3: return (x + y) % 3 === 0;
      case 4: return (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0;
      case 5: return (x * y) % 2 + (x * y) % 3 === 0;
      case 6: return ((x * y) % 2 + (x * y) % 3) % 2 === 0;
      default: return ((x + y) % 2 + (x * y) % 3) % 2 === 0;
    }
  }
  Matrix.prototype.applyMask = function (mask) {
    for (var y = 0; y < this.size; y++) {
      for (var x = 0; x < this.size; x++) {
        if (!this.fixed[y][x] && maskHit(mask, x, y)) this.modules[y][x] = !this.modules[y][x];
      }
    }
  };
  /** The standard penalty score; lower is easier to scan. */
  Matrix.prototype.penalty = function () {
    var size = this.size, m = this.modules, score = 0, x, y, dark = 0;
    function lines(get) {
      var s = 0;
      for (var a = 0; a < size; a++) {
        var run = 1, line = '';
        for (var b = 0; b < size; b++) {
          var v = get(a, b);
          line += v ? '1' : '0';
          if (b > 0) {
            if (v === get(a, b - 1)) run++;
            else { if (run >= 5) s += run - 2; run = 1; }
          }
        }
        if (run >= 5) s += run - 2;
        var padded = '0000' + line + '0000', at = padded.indexOf('1011101');
        while (at >= 0) {
          if (padded.substr(at - 4, 4) === '0000' || padded.substr(at + 7, 4) === '0000') s += 40;
          at = padded.indexOf('1011101', at + 1);
        }
      }
      return s;
    }
    score += lines(function (a, b) { return m[a][b]; });
    score += lines(function (a, b) { return m[b][a]; });
    for (y = 0; y < size - 1; y++) {
      for (x = 0; x < size - 1; x++) {
        var c = m[y][x];
        if (c === m[y][x + 1] && c === m[y + 1][x] && c === m[y + 1][x + 1]) score += 3;
      }
    }
    for (y = 0; y < size; y++) for (x = 0; x < size; x++) if (m[y][x]) dark++;
    var total = size * size;
    score += (Math.ceil(Math.abs(dark * 20 - total * 10) / total) - 1) * 10;
    return score;
  };

  /** Encodes text. Returns { size, isDark(x, y) }, or null if it is too long. */
  function encode(text) {
    var bytes = utf8(text), version = chooseVersion(bytes.length);
    if (!version) return null;
    var codewords = finalCodewords(dataCodewords(bytes, version), version);
    var best = null, bestScore = Infinity;
    for (var mask = 0; mask < 8; mask++) {
      var mx = new Matrix(version);
      mx.drawFunctionPatterns();
      mx.drawCodewords(codewords);
      mx.applyMask(mask);
      mx.drawFormatBits(mask);
      var score = mx.penalty();
      if (score < bestScore) { bestScore = score; best = mx; }
    }
    var modules = best.modules;
    return { size: best.size, isDark: function (x, y) { return modules[y][x]; } };
  }

  /** Same as WifiQrPayload.build (shared/.../wifi/WifiQrPayload.kt). */
  function wifiPayload(wifi) {
    function esc(s) { return String(s || '').replace(/([\\;,:"])/g, '\\$1'); }
    var token = { WPA: 'WPA', WEP: 'WEP', OPEN: 'nopass' }[wifi.security || 'WPA'] || 'WPA';
    var out = 'WIFI:T:' + token + ';S:' + esc(wifi.ssid) + ';';
    if (token !== 'nopass') out += 'P:' + esc(wifi.password) + ';';
    if (wifi.hidden) out += 'H:true;';
    return out + ';';
  }

  /** An SVG string of the code with a 2-module quiet zone, drawn on [light] with [dark] modules. */
  function svg(text, pixelSize, dark, light) {
    var qr = encode(text);
    if (!qr) return '';
    var n = qr.size + 4, d = '';
    for (var y = 0; y < qr.size; y++) {
      for (var x = 0; x < qr.size; x++) if (qr.isDark(x, y)) d += 'M' + (x + 2) + ' ' + (y + 2) + 'h1v1h-1z';
    }
    return '<svg xmlns="http://www.w3.org/2000/svg" width="' + pixelSize + '" height="' + pixelSize +
      '" viewBox="0 0 ' + n + ' ' + n + '" shape-rendering="crispEdges" aria-hidden="true">' +
      '<rect width="' + n + '" height="' + n + '" fill="' + light + '"/><path d="' + d + '" fill="' + dark + '"/></svg>';
  }

  var api = { encode: encode, wifiPayload: wifiPayload, svg: svg };
  if (typeof window !== 'undefined') window.GuestQr = api;
  if (typeof module !== 'undefined') module.exports = api;
})();
