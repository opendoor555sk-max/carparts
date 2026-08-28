// Minimal Code128 (auto B) barcode generator -> SVG string.
// Works for both on-screen (react-native-svg SvgXml) and print (HTML img/inline svg).

const PATTERNS = [
  "212222","222122","222221","121223","121322","131222","122213","122312","132212","221213",
  "221312","231212","112232","122132","122231","113222","123122","123221","223211","221132",
  "221231","213212","223112","312131","311222","321122","321221","312212","322112","322211",
  "212123","212321","232121","111323","131123","131321","112313","132113","132311","211313",
  "231113","231311","112133","112331","132131","113123","113321","133121","313121","211331",
  "231131","213113","213311","213131","311123","311321","331121","312113","312311","332111",
  "314111","221411","431111","111224","111422","121124","121421","141122","141221","112214",
  "112412","122114","122411","142112","142211","241211","221114","413111","241112","134111",
  "111242","121142","121241","114212","124112","124211","411212","421112","421211","212141",
  "214121","412121","111143","111341","131141","114113","114311","411113","411311","113141",
  "114131","311141","411131","211412","211214","211232","2331112",
];
const START_B = 104;
const STOP = 106;

function encode(value: string): number[] {
  const codes: number[] = [START_B];
  let sum = START_B;
  for (let i = 0; i < value.length; i++) {
    let v = value.charCodeAt(i) - 32;
    if (v < 0 || v > 94) v = 0; // fallback to space for unsupported chars
    codes.push(v);
    sum += v * (i + 1);
  }
  codes.push(sum % 103);
  codes.push(STOP);
  return codes;
}

export function barcodeSvg(
  value: string,
  opts: { height?: number; moduleWidth?: number; showText?: boolean } = {},
): string {
  const raw = (value || "").toUpperCase().replace(/[^ -~]/g, "");
  const height = opts.height ?? 70;
  const mw = opts.moduleWidth ?? 2;
  const showText = opts.showText ?? true;
  const textH = showText ? 20 : 0;
  const codes = encode(raw);

  let x = 10;
  let bars = "";
  for (const c of codes) {
    const pat = PATTERNS[c];
    let bar = true;
    for (const ch of pat) {
      const w = parseInt(ch, 10) * mw;
      if (bar) bars += `<rect x="${x}" y="0" width="${w}" height="${height}" fill="#000"/>`;
      x += w;
      bar = !bar;
    }
  }
  const width = x + 10;
  const text = showText
    ? `<text x="${width / 2}" y="${height + 15}" font-family="monospace" font-size="14" text-anchor="middle" fill="#000">${raw}</text>`
    : "";
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height + textH}" viewBox="0 0 ${width} ${height + textH}"><rect width="${width}" height="${height + textH}" fill="#fff"/>${bars}${text}</svg>`;
}
