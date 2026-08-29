// A4 sticker-sheet master layouts + anti-wastage print HTML generator.
// Data from the user's sticker chart (Layout Code / Total / W x H mm / Rows x Cols).
import { barcodeSvg } from "@/src/utils/barcode128";
import { codeSvg, svgRatio } from "@/src/utils/codegen";
import { qrSvg } from "@/src/utils/qr";

export type SheetLayout = {
  code: string;
  label: string;
  total: number;
  w: number; // label width mm
  h: number; // label height mm
  rows: number;
  cols: number;
};

// Rows x Cols as printed on the chart (first number = rows, second = cols).
export const SHEET_LAYOUTS: SheetLayout[] = [
  { code: "01P", label: "1 Label (Full A4)", total: 1, w: 210, h: 297, rows: 1, cols: 1 },
  { code: "02L", label: "2 Labels", total: 2, w: 200, h: 146, rows: 2, cols: 1 },
  { code: "04P", label: "4 Labels", total: 4, w: 100, h: 145, rows: 2, cols: 2 },
  { code: "06L", label: "6 Labels", total: 6, w: 99, h: 93, rows: 3, cols: 2 },
  { code: "08L", label: "8 Labels", total: 8, w: 100, h: 72, rows: 4, cols: 2 },
  { code: "08LA", label: "8 Labels (90x55)", total: 8, w: 90, h: 55, rows: 4, cols: 2 },
  { code: "12L", label: "12 Labels", total: 12, w: 100, h: 44, rows: 6, cols: 2 },
  { code: "15L", label: "15 Labels", total: 15, w: 61, h: 21, rows: 5, cols: 3 },
  { code: "16L", label: "16 Labels", total: 16, w: 99, h: 34, rows: 8, cols: 2 },
  { code: "18L", label: "18 Labels", total: 18, w: 63.5, h: 46.6, rows: 6, cols: 3 },
  { code: "21L", label: "21 Labels", total: 21, w: 63.5, h: 38, rows: 7, cols: 3 },
  { code: "22L", label: "22 Labels", total: 22, w: 100, h: 24, rows: 11, cols: 2 },
  { code: "24L", label: "24 Labels", total: 24, w: 64, h: 34, rows: 8, cols: 3 },
  { code: "30L", label: "30 Labels (67x27.5)", total: 30, w: 67, h: 27.5, rows: 10, cols: 3 },
  { code: "30P", label: "30 Labels (39x47.5)", total: 30, w: 39, h: 47.5, rows: 6, cols: 5 },
  { code: "32P", label: "32 Labels", total: 32, w: 25, h: 70, rows: 4, cols: 8 },
  { code: "40L", label: "40 Labels", total: 40, w: 39, h: 35, rows: 8, cols: 5 },
  { code: "40P", label: "40 Labels (18x73)", total: 40, w: 18, h: 73, rows: 20, cols: 2 },
  { code: "48L", label: "48 Labels", total: 48, w: 48, h: 24, rows: 12, cols: 4 },
  { code: "56L", label: "56 Labels", total: 56, w: 48, h: 20, rows: 14, cols: 4 },
  { code: "65L", label: "65 Labels", total: 65, w: 38, h: 21, rows: 13, cols: 5 },
  { code: "84L", label: "84 Labels", total: 84, w: 46, h: 11, rows: 21, cols: 4 },
  { code: "110L", label: "110 Labels", total: 110, w: 35, h: 10, rows: 22, cols: 5 },
];

export type CodeType = "barcode" | "qr" | "both" | "none";

export type LabelContent = {
  partNumber: string;
  line1?: string; // e.g. company / name
  line2?: string; // e.g. category / note
  code: CodeType;
};

export type SheetOptions = {
  layout: SheetLayout;
  cells: number[]; // 1-based cell indices to print on
  showBorder?: boolean; // dashed cut guides
  marginTop?: number | null; // mm, manual top margin override (null/undefined = auto)
  marginLeft?: number | null; // mm, manual left margin override
  pageMargin?: number | null; // mm, PDF @page margin (null/undefined = 0 = edge-to-edge)
};

const A4_W = 210;
const A4_H = 297;

function labelInner(c: LabelContent, w: number, h: number): string {
  const fs = Math.max(2.0, Math.min(h * 0.20, 5.25)); // mm (+25% bigger text)
  const small = fs * 0.75;
  const wantBar = c.code === "barcode" || c.code === "both";
  const wantQr = c.code === "qr" || c.code === "both";
  const pn = (c.partNumber || "").trim();

  // QR is a FIXED 10mm x 10mm square (capped to fit tiny cells) for reliable scanning.
  const qmm = Math.min(10, h - 1, w - 1);
  const qrBlock = wantQr
    ? `<div style="flex:0 0 auto;display:flex;align-items:center;justify-content:center">${qrSvg(pn, { margin: 1 }).replace("<svg ", `<svg style="height:${qmm.toFixed(1)}mm;width:${qmm.toFixed(1)}mm" `)}</div>`
    : "";
  const barBlock = wantBar
    ? `<div style="flex:0 0 auto;width:96%;display:flex;align-items:center;justify-content:center">${barcodeSvg(pn, { height: 60, moduleWidth: 2, showText: false }).replace("<svg ", `<svg preserveAspectRatio="none" style="width:100%;height:${Math.min(h * 0.32, 12)}mm" `)}</div>`
    : "";

  const l1 = c.line1 ? `<div style="font-size:${small}mm;font-weight:600;line-height:1.05;text-align:center;max-width:98%;overflow:hidden">${escapeHtml(c.line1)}</div>` : "";
  const l2 = c.line2 ? `<div style="font-size:${small * 0.9}mm;color:#333;line-height:1.05;text-align:center;max-width:98%;overflow:hidden">${escapeHtml(c.line2)}</div>` : "";
  const pnBlock = pn ? `<div style="font-size:${fs}mm;font-weight:800;letter-spacing:0.2px;line-height:1.05;text-align:center;max-width:98%;overflow:hidden">${escapeHtml(pn)}</div>` : "";

  const both = wantQr && wantBar;
  if (both) {
    // qr left, text+barcode right
    return `<div style="width:100%;height:100%;display:flex;flex-direction:row;align-items:center;justify-content:center;gap:1mm;padding:0.8mm;box-sizing:border-box">
      <div style="flex:0 0 auto;height:100%;display:flex;align-items:center">${qrSvg(pn, { margin: 1 }).replace("<svg ", `<svg style="height:${qmm.toFixed(1)}mm;width:${qmm.toFixed(1)}mm" `)}</div>
      <div style="flex:1 1 auto;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:0.4mm;overflow:hidden">${l1}${pnBlock}${barBlock}${l2}</div>
    </div>`;
  }

  return `<div style="width:100%;height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:0.5mm;padding:0.8mm;box-sizing:border-box">
    ${l1}${qrBlock}${barBlock}${pnBlock}${l2}
  </div>`;
}

function escapeHtml(s: any): string {
  return String(s ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

export type Box = { x: number; y: number; w: number; h: number };
export type TplLine = { text: string; x: number; y: number; size: number; bold?: boolean; zone?: string };
export type StickerTemplate = {
  aspect: number;
  lines: TplLine[];
  code: { type: string; value: string; box: Box; sizeMm?: number } | null;
  logo?: { dataUrl: string; box: Box } | null;
  company?: string;
};

// Builds a CLEAN white sticker: typed text lines + a regenerated code. No photo pixels.
function templateInner(tpl: StickerTemplate, wMm: number, hMm: number): string {
  let out = "";
  if (tpl.logo && tpl.logo.dataUrl) {
    const b = tpl.logo.box;
    out += `<img src="${tpl.logo.dataUrl}" style="position:absolute;left:${b.x}%;top:${b.y}%;width:${b.w}%;height:${b.h}%;object-fit:contain"/>`;
  }
  for (const ln of tpl.lines) {
    const fs = Math.max(0.8, (ln.size / 100) * hMm); // mm
    out += `<div style="position:absolute;left:${ln.x}%;top:${ln.y}%;font-size:${fs.toFixed(2)}mm;font-weight:${ln.bold ? 800 : 500};white-space:nowrap;line-height:1;color:#000;font-family:Arial,Helvetica,sans-serif">${escapeHtml(ln.text)}</div>`;
  }
  if (tpl.code && tpl.code.value) {
    // Unified: any code type. Size from code.sizeMm (default 10mm, capped to cell).
    // Free X/Y placement from box.x / box.y. Wide codes keep their aspect.
    const svg = codeSvg(tpl.code.type, tpl.code.value);
    const ratio = svgRatio(svg);
    const sizeMm = Math.min(tpl.code.sizeMm ?? 10, hMm - 1);
    const codeWmm = ratio > 1.3 ? Math.min(sizeMm * ratio, wMm * 0.6) : sizeMm;
    const topMm = Math.max(0, Math.min((tpl.code.box.y / 100) * hMm, hMm - sizeMm));
    const maxLeft = Math.max(0, wMm - codeWmm);
    const leftMm = Math.max(0, Math.min((tpl.code.box.x / 100) * wMm, maxLeft));
    out += `<div style="position:absolute;left:${leftMm.toFixed(2)}mm;top:${topMm.toFixed(2)}mm;width:${codeWmm.toFixed(2)}mm;height:${sizeMm.toFixed(2)}mm;display:flex;align-items:center;justify-content:center">${svg.replace("<svg ", `<svg preserveAspectRatio="xMidYMid meet" style="width:100%;height:100%" `)}</div>`;
  }
  return out;
}

function buildSheet(opts: SheetOptions, innerFor: (w: number, h: number) => string): string {
  const selected = new Set(opts.cells);
  // Per-cell renderer: fill only the selected cells with the single sticker.
  return renderSheet(opts, (i, w, h) => (selected.has(i + 1) ? innerFor(w, h) : ""));
}

// Core sheet renderer — innerForCell(cellIndex0, w, h) returns the cell's inner HTML ("" = empty).
function renderSheet(opts: SheetOptions, innerForCell: (i: number, w: number, h: number) => string): string {
  const { layout, showBorder } = opts;
  const { w, h, rows, cols, total } = layout;
  const autoLeft = Math.max(0, (A4_W - cols * w) / 2);
  const autoTop = Math.min(Math.max(0, (A4_H - rows * h) / 2), autoLeft);
  const leftM = opts.marginLeft == null ? autoLeft : Math.max(0, opts.marginLeft);
  const topM = opts.marginTop == null ? autoTop : Math.max(0, opts.marginTop);
  const pageM = opts.pageMargin == null ? 0 : Math.max(0, opts.pageMargin);
  let out = "";
  for (let i = 0; i < total; i++) {
    const r = Math.floor(i / cols);
    const col = i % cols;
    const x = leftM + col * w;
    const y = topM + r * h;
    const border = showBorder ? "border:0.2mm dashed #bbb;" : "";
    const inner = innerForCell(i, w, h);
    out += `<div style="position:absolute;left:${x}mm;top:${y}mm;width:${w}mm;height:${h}mm;box-sizing:border-box;overflow:hidden;background:#fff;${border}"><div style="position:relative;width:100%;height:100%">${inner}</div></div>`;
  }
  return `<html><head><meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    @page { size: A4; margin: ${pageM}mm; }
    html,body { margin:0; padding:0; }
    .sheet { position:relative; width:${A4_W}mm; height:${A4_H}mm; }
    * { -webkit-print-color-adjust:exact; print-color-adjust:exact; }
  </style></head>
  <body><div class="sheet">${out}</div></body></html>`;
}

export function generateSheetHtml(content: LabelContent, opts: SheetOptions): string {
  return buildSheet(opts, (w, h) => labelInner(content, w, h));
}

export function generateRichStickerSheetHtml(tpl: StickerTemplate, opts: SheetOptions): string {
  return buildSheet(opts, (w, h) => templateInner(tpl, w, h));
}

// Batch: fill the grid sequentially with a queue of stickers (anti-wastage).
// Each queue entry = one printed sticker; cells fill left→right, top→bottom from startCell.
export function generateBatchSheetHtml(queue: StickerTemplate[], opts: SheetOptions & { startCell?: number }): string {
  const start = Math.max(0, (opts.startCell ?? 1) - 1);
  return renderSheet(opts, (i, w, h) => {
    const q = i - start;
    return q >= 0 && q < queue.length ? templateInner(queue[q], w, h) : "";
  });
}

// Composed sheet: each cell (1-based) can hold a DIFFERENT sticker (any company/part).
export function generateComposedSheetHtml(cellTpls: Record<number, StickerTemplate>, opts: SheetOptions): string {
  return renderSheet(opts, (i, w, h) => {
    const t = cellTpls[i + 1];
    return t ? templateInner(t, w, h) : "";
  });
}
