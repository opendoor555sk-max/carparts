// Pure-JS QR generator -> scalable SVG string (no DOM / canvas).
// Works for on-screen (react-native-svg SvgXml) and print (HTML inline svg).
import QR from "qrcode-generator";

export function qrSvg(value: string, opts: { margin?: number } = {}): string {
  const qr = QR(0, "M");
  qr.addData(value && value.length ? value : " ");
  qr.make();
  const count = qr.getModuleCount();
  const margin = opts.margin ?? 2;
  const size = count + margin * 2;
  let rects = "";
  for (let r = 0; r < count; r++) {
    for (let c = 0; c < count; c++) {
      if (qr.isDark(r, c)) {
        rects += `<rect x="${c + margin}" y="${r + margin}" width="1.02" height="1.02" fill="#000"/>`;
      }
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" shape-rendering="crispEdges"><rect width="${size}" height="${size}" fill="#fff"/>${rects}</svg>`;
}
