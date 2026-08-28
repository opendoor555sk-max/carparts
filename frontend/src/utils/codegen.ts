// Unified 2D / matrix code generator -> SVG string (no DOM/canvas).
// Standard QR uses qrcode-generator, DataMatrix uses datamatrix-svg-ts, and the
// remaining symbologies are produced by @bwip-js/generic (drawingSVG).
// NOTE: iQR / Frame QR / QR Model 1 are Denso-proprietary and cannot be produced
// by any open library, so they are intentionally not offered.
import * as bwip from "@bwip-js/generic";
import { qrSvg } from "@/src/utils/qr";
import { dataMatrixSvg } from "@/src/utils/dmatrix";
import { barcodeSvg } from "@/src/utils/barcode128";

export type CodeTypeDef = { key: string; label: string; wide?: boolean };

// Types verified to encode plain alphanumeric part numbers reliably.
export const CODE_TYPES: CodeTypeDef[] = [
  { key: "qr", label: "QR Code" },
  { key: "datamatrix", label: "DataMatrix" },
  { key: "azteccode", label: "Aztec" },
  { key: "azteccodecompact", label: "Aztec Compact" },
  { key: "hanxin", label: "Han Xin" },
  { key: "dotcode", label: "DotCode" },
  { key: "maxicode", label: "MaxiCode" },
  { key: "pdf417", label: "PDF417", wide: true },
  { key: "pdf417compact", label: "PDF417 Compact", wide: true },
  { key: "micropdf417", label: "Micro PDF417", wide: true },
  { key: "barcode", label: "Barcode 128", wide: true },
];

const BWIP_IDS = new Set([
  "azteccode", "azteccodecompact", "hanxin", "dotcode", "maxicode",
  "pdf417", "pdf417compact", "micropdf417",
]);

export function codeSvg(type: string, value: string): string {
  const v = value && value.length ? value : " ";
  try {
    if (type === "qr") return qrSvg(v, { margin: 1 });
    if (type === "datamatrix") return dataMatrixSvg(v, { margin: 1 });
    if (type === "barcode") return barcodeSvg(v, { height: 60, moduleWidth: 2, showText: false });
    if (BWIP_IDS.has(type)) return (bwip as any)[type]({ text: v }, (bwip as any).drawingSVG());
  } catch {}
  return qrSvg(v, { margin: 1 });
}

// width / height ratio from an SVG's viewBox (used to keep aspect while sizing).
export function svgRatio(svg: string): number {
  const m = svg.match(/viewBox="[\d.]+ [\d.]+ ([\d.]+) ([\d.]+)"/);
  if (!m) return 1;
  const w = parseFloat(m[1]), h = parseFloat(m[2]);
  return h > 0 ? w / h : 1;
}
