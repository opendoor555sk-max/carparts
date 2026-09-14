import * as Print from "expo-print";
import * as Sharing from "expo-sharing";
import * as FileSystem from "expo-file-system/legacy";
import { Linking, Platform } from "react-native";

import { fileUrl } from "@/src/api/client";
import { barcodeSvg } from "@/src/utils/barcode128";
import { qrSvg } from "@/src/utils/qr";
import { formatAssignedLocation, type AssignedLocation } from "@/src/components/LocationPicker";

export type Branding = {
  name: string;
  gst?: string;
  phone?: string;
  address?: string;
  bank?: string;
  logoUrl?: string;
};

function esc(s: any): string {
  return String(s ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

// Build a branding object (with logo URL) from the logged-in user.
export async function brandingFromUser(user: any): Promise<Branding> {
  let logoUrl: string | undefined;
  if (user?.store_logo) {
    try {
      logoUrl = await fileUrl(user.store_logo);
    } catch {
      logoUrl = undefined;
    }
  }
  return {
    name: user?.store_name || "Auto Parts Store",
    gst: user?.store_gst || "",
    phone: user?.store_phone || "",
    address: user?.store_address || "",
    bank: user?.store_bank || "",
    logoUrl,
  };
}

function header(b: Branding): string {
  const contact = [b.gst ? `GST: ${esc(b.gst)}` : "", b.phone ? `Phone: ${esc(b.phone)}` : ""]
    .filter(Boolean)
    .join(" &nbsp;|&nbsp; ");
  const logo = b.logoUrl
    ? `<img src="${b.logoUrl}" style="height:56px;max-width:120px;object-fit:contain;margin-right:14px"/>`
    : "";
  return `<div style="display:flex;align-items:center;border-bottom:2px solid #222;padding-bottom:10px;margin-bottom:14px">
    ${logo}
    <div>
      <div style="font-size:22px;font-weight:800">${esc(b.name)}</div>
      ${contact ? `<div style="font-size:12px;color:#555;margin-top:2px">${contact}</div>` : ""}
      ${b.address ? `<div style="font-size:12px;color:#555">${esc(b.address)}</div>` : ""}
      ${b.bank ? `<div style="font-size:12px;color:#555">Bank: ${esc(b.bank)}</div>` : ""}
    </div>
  </div>`;
}

function wrap(title: string, b: Branding, body: string): string {
  return `<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>
    body{font-family:-apple-system,Roboto,Arial,sans-serif;padding:24px;color:#111}
    h2{font-size:15px;color:#333;margin:0 0 4px}
    .meta{font-size:12px;color:#777;margin-bottom:14px}
    table{width:100%;border-collapse:collapse;font-size:12px}
    th,td{border:1px solid #ccc;padding:6px 8px;text-align:left}
    th{background:#f2f2f2}
    .tot{margin-top:16px;font-size:14px;font-weight:bold}
  </style></head><body>
    ${header(b)}
    <h2>${esc(title)}</h2>
    <div class="meta">${esc(new Date().toLocaleString())}</div>
    ${body}
  </body></html>`;
}

export async function printHtml(html: string): Promise<void> {
  if (Platform.OS === "web") {
    // On web / in-app webview, Print.printAsync can print the whole app page.
    // Render ONLY our HTML in an isolated iframe and print that.
    return new Promise<void>((resolve) => {
      const iframe = document.createElement("iframe");
      iframe.style.position = "fixed";
      iframe.style.right = "0";
      iframe.style.bottom = "0";
      iframe.style.width = "0";
      iframe.style.height = "0";
      iframe.style.border = "0";
      document.body.appendChild(iframe);
      const doc = iframe.contentWindow?.document;
      if (!doc) {
        document.body.removeChild(iframe);
        // fallback: new window
        const w = window.open("", "_blank");
        if (w) {
          w.document.open();
          w.document.write(html);
          w.document.close();
          setTimeout(() => { w.focus(); w.print(); }, 500);
        }
        resolve();
        return;
      }
      doc.open();
      doc.write(html);
      doc.close();
      const done = () => {
        try {
          iframe.contentWindow?.focus();
          iframe.contentWindow?.print();
        } catch {}
        setTimeout(() => {
          try { document.body.removeChild(iframe); } catch {}
          resolve();
        }, 1000);
      };
      // give images/QR SVG time to render
      setTimeout(done, 600);
    });
  }
  await Print.printAsync({ html });
}

export async function printInventory(b: Branding, units: any[]): Promise<void> {
  const rows = units
    .map(
      (u, i) =>
        `<tr><td>${i + 1}</td><td>${esc(u.part_number)}</td><td>${esc(u.part_name || "")}</td><td>${esc(
          u.condition || "",
        )}</td><td>${esc(
          [u.location?.rack, u.location?.shelf, u.location?.box, u.location?.position]
            .filter(Boolean)
            .join(" -> "),
        )}</td></tr>`,
    )
    .join("");
  const body = `<table><thead><tr><th>#</th><th>Part Number</th><th>Name</th><th>Condition</th><th>Location</th></tr></thead><tbody>${rows}</tbody></table><div class="tot">Total units: ${units.length}</div>`;
  await printHtml(wrap("Inventory Report", b, body));
}

type ReceiptData = {
  part_number: string;
  name?: string;
  condition?: string;
  price?: any;
  buyer?: string;
  location?: any;
  by?: string;
  // How many units this receipt covers — set when printing a multi-unit Buy
  // line (the consolidated Buy screen buys N units of one part at once).
  // Omitted (or 1) prints the same single-unit receipt as before.
  qty?: number;
};

export async function printReceipt(b: Branding, kind: "BUY" | "SELL", data: ReceiptData): Promise<void> {
  const loc = [data.location?.rack, data.location?.shelf, data.location?.box, data.location?.position]
    .filter(Boolean)
    .join(" -> ");
  const lines: (string[] | null)[] = [
    ["Type", kind === "BUY" ? "Purchase" : "Sale"],
    ["Part Number", data.part_number],
    ["Name", data.name || "-"],
    ["Condition", data.condition || "-"],
    data.qty != null && data.qty !== 1 ? ["Quantity", String(data.qty)] : null,
    loc ? ["Location", loc] : null,
    data.buyer ? ["Buyer", data.buyer] : null,
    data.price != null && data.price !== "" ? ["Price", "Rs. " + data.price] : null,
    data.by ? ["By", data.by] : null,
  ];
  const rows = lines
    .filter(Boolean)
    .map((r: any) => `<tr><th style="width:35%">${esc(r[0])}</th><td>${esc(r[1])}</td></tr>`)
    .join("");
  const barcode = `<div style="text-align:center;margin-top:16px">${barcodeSvg(data.part_number, { height: 60 })}</div>`;
  await printHtml(wrap(kind === "BUY" ? "Purchase Receipt" : "Sale Receipt", b, `<table>${rows}</table>${barcode}`));
}

export async function printBarcodeLabel(b: Branding, partNumber: string, company?: string, qrMm: number = 30): Promise<void> {
  const body = `<div style="text-align:center;padding:10px">
    ${company ? `<div style="font-size:13px;font-weight:700;margin-bottom:6px">${esc(company)}</div>` : ""}
    ${barcodeSvg(partNumber, { height: 90, moduleWidth: 2 })}
    <div style="display:flex;align-items:center;justify-content:center;margin-top:12px">
      <div style="width:${qrMm}mm;height:${qrMm}mm">${qrSvg(partNumber, { margin: 1 })}</div>
    </div>
  </div>`;
  await printHtml(wrap("Barcode Label", b, body));
}

// Simple printable label for the physical rack/shelf spot itself — stuck on the
// rack/carton rather than the part — showing the structured hierarchical
// address in large text plus a barcode of the part number for a quick re-scan.
export async function printLocationSticker(
  b: Branding,
  partNumber: string,
  loc: AssignedLocation,
  partName?: string,
): Promise<void> {
  const addr = formatAssignedLocation(loc) || "No location set";
  const body = `<div style="text-align:center;padding:10px">
    <div style="font-size:20px;font-weight:900;letter-spacing:0.5px">${esc(partNumber)}</div>
    ${partName ? `<div style="font-size:13px;color:#555;margin-top:2px">${esc(partName)}</div>` : ""}
    <div style="margin-top:16px;font-size:17px;font-weight:800;line-height:1.5;border:2px solid #222;border-radius:10px;padding:14px">
      ${esc(addr)}
    </div>
    <div style="display:flex;align-items:center;justify-content:center;margin-top:14px">
      ${barcodeSvg(partNumber, { height: 60, moduleWidth: 2 })}
    </div>
  </div>`;
  await printHtml(wrap("Location Sticker", b, body));
}

export async function printRequirements(b: Branding, reqs: any[]): Promise<void> {
  const rows = reqs
    .map(
      (r, i) =>
        `<tr><td>${i + 1}</td><td>${esc(r.part_number)}</td><td>${esc(r.name || "")}</td><td>${esc(
          r.priority || "",
        )}</td><td>${esc(r.quantity ?? "")}</td><td>${esc(r.stock_count ?? 0)}</td><td>${esc(
          r.status || "",
        )}</td></tr>`,
    )
    .join("");
  const body = `<table><thead><tr><th>#</th><th>Part Number</th><th>Name</th><th>Priority</th><th>Qty</th><th>In Stock</th><th>Status</th></tr></thead><tbody>${rows}</tbody></table><div class="tot">Total: ${reqs.length}</div>`;
  await printHtml(wrap("Requirements / Inquiry List", b, body));
}

export type InvoiceData = {
  invoice_number: string;
  at: string;
  part_number: string;
  description?: string;
  customer_name?: string;
  price?: number | null;
  gst_rate: number;
  gst_amount?: number | null;
  total?: number | null;
};

function money(v?: number | null): string {
  return v != null ? `Rs. ${v.toFixed(2)}` : "-";
}

export function invoiceHtml(b: Branding, inv: InvoiceData): string {
  const metaRows: [string, string][] = [
    ["Invoice No.", inv.invoice_number],
    ["Date", new Date(inv.at).toLocaleString()],
  ];
  if (inv.customer_name) metaRows.push(["Customer", inv.customer_name]);
  const meta = metaRows
    .map(([k, v]) => `<tr><th style="width:35%">${esc(k)}</th><td>${esc(v)}</td></tr>`)
    .join("");
  const body = `<table>${meta}</table>
    <table style="margin-top:14px"><thead><tr><th>Part Number</th><th>Description</th><th>Amount</th></tr></thead>
      <tbody><tr><td>${esc(inv.part_number)}</td><td>${esc(inv.description || "-")}</td><td>${money(inv.price)}</td></tr></tbody>
    </table>
    <table style="margin-top:12px"><tbody>
      <tr><th style="width:70%">Taxable Amount</th><td>${money(inv.price)}</td></tr>
      <tr><th>GST (${(inv.gst_rate * 100).toFixed(0)}%)</th><td>${money(inv.gst_amount)}</td></tr>
      <tr><th>Total</th><td><b>${money(inv.total)}</b></td></tr>
    </tbody></table>`;
  return wrap("Tax Invoice", b, body);
}

// Native: render the invoice to a PDF file and hand it to the OS share sheet
// (WhatsApp, email, save-to-Drive, etc.) so the customer can be sent a copy
// without a physical printer. Web has no equivalent file-share surface, so it
// falls back to the same print-dialog flow as everything else in this file —
// "Save as PDF" from that dialog covers the same need.
export async function shareInvoicePdf(b: Branding, inv: InvoiceData): Promise<void> {
  const html = invoiceHtml(b, inv);
  if (Platform.OS === "web") {
    await printHtml(html);
    return;
  }
  const { uri } = await Print.printToFileAsync({ html });
  const available = await Sharing.isAvailableAsync();
  if (available) {
    await Sharing.shareAsync(uri, { mimeType: "application/pdf", dialogTitle: `Invoice ${inv.invoice_number}` });
  } else {
    // No share sheet available on this device — fall back to the direct
    // print dialog rather than leaving the user with no way out.
    await Print.printAsync({ html });
  }
}

// ---------------------------------------------------------------------------
// WhatsApp sharing — free, manual, no WhatsApp Business API, no native module.
//
// WhatsApp's "click-to-chat" web link (wa.me) is a plain https:// URL: it
// opens the WhatsApp app directly with a message pre-filled (falling back to
// WhatsApp Web / the app/play store if WhatsApp isn't installed), the exact
// same Linking.openURL mechanism already used elsewhere in this app (tel:,
// maps links, the Google Console link in settings.tsx). No canOpenURL check
// is needed since it's a universal https link, not the whatsapp:// scheme.
// An optional `phone` (digits only, with country code) preselects a specific
// chat; omitted, the user picks a contact or group after WhatsApp opens.
//
// This only carries text: wa.me has no attachment parameter, so a file (the
// invoice PDF below) still has to go through the OS share sheet — there is
// no native-module-free way to hand a file to WhatsApp specifically, so that
// path keeps using the same Sharing.shareAsync flow as shareInvoicePdf.
export async function shareTextOnWhatsApp(text: string, phone?: string): Promise<void> {
  const digits = phone ? phone.replace(/[^0-9]/g, "") : "";
  const waUrl = `https://wa.me/${digits}?text=${encodeURIComponent(text)}`;
  try {
    await Linking.openURL(waUrl);
  } catch {
    // No handler for the link (rare) -- fall back to the generic OS share
    // sheet so the user can still pick WhatsApp manually themselves.
    if (Platform.OS !== "web" && (await Sharing.isAvailableAsync())) {
      const uri = FileSystem.cacheDirectory + "whatsapp-share.txt";
      await FileSystem.writeAsStringAsync(uri, text);
      await Sharing.shareAsync(uri, { mimeType: "text/plain", dialogTitle: "Share" });
    } else {
      throw new Error("Could not open WhatsApp");
    }
  }
}

// Plain-text mirror of invoiceHtml above, for the WhatsApp text-summary share
// (kept in English like every other print/receipt/invoice document in this
// file — these are business records, not app-chrome UI, so they sit outside
// the language-toggle sweep the same way invoiceHtml/printReport/etc. do).
export function invoiceWhatsAppText(b: Branding, inv: InvoiceData): string {
  const lines = [`*${b.name}*`, `Invoice: ${inv.invoice_number}`, `Date: ${new Date(inv.at).toLocaleDateString()}`];
  if (inv.customer_name) lines.push(`Customer: ${inv.customer_name}`);
  lines.push(
    `Part: ${inv.part_number}${inv.description ? " - " + inv.description : ""}`,
    `Amount: ${money(inv.price)}`,
    `GST (${(inv.gst_rate * 100).toFixed(0)}%): ${money(inv.gst_amount)}`,
    `*Total: ${money(inv.total)}*`,
    "",
    "Thank you for your business!",
  );
  return lines.join("\n");
}

export async function shareInvoiceOnWhatsApp(b: Branding, inv: InvoiceData): Promise<void> {
  await shareTextOnWhatsApp(invoiceWhatsAppText(b, inv));
}

export type LowStockRow = { part_number: string; name?: string; stock_count: number; low_stock_threshold: number };

export function lowStockWhatsAppText(b: Branding, rows: LowStockRow[]): string {
  const lines = [`*Low Stock Alert — ${b.name}*`, new Date().toLocaleDateString(), ""];
  rows.forEach((r, i) => {
    lines.push(`${i + 1}. ${r.part_number}${r.name ? " - " + r.name : ""} — ${r.stock_count} left (alert ≤ ${r.low_stock_threshold})`);
  });
  lines.push("", `Total: ${rows.length} part(s) low on stock.`);
  return lines.join("\n");
}

export async function shareLowStockOnWhatsApp(b: Branding, rows: LowStockRow[]): Promise<void> {
  await shareTextOnWhatsApp(lowStockWhatsAppText(b, rows));
}

export type DailySalesSummary = {
  units_sold: number;
  total_revenue: number;
  total_cost: number;
  total_profit: number;
  units_with_unknown_cost: number;
};

export function dailySalesWhatsAppText(b: Branding, s: DailySalesSummary, dateLabel: string): string {
  const lines = [
    `*Daily Sales Summary — ${b.name}*`,
    dateLabel,
    "",
    `Units sold: ${s.units_sold}`,
    `Revenue: ${money(s.total_revenue)}`,
    `Cost: ${money(s.total_cost)}`,
    `*Profit: ${money(s.total_profit)}*`,
  ];
  if (s.units_with_unknown_cost > 0) lines.push(`(${s.units_with_unknown_cost} unit(s) had no recorded cost)`);
  return lines.join("\n");
}

export async function shareDailySalesOnWhatsApp(b: Branding, s: DailySalesSummary, dateLabel: string): Promise<void> {
  await shareTextOnWhatsApp(dailySalesWhatsAppText(b, s, dateLabel));
}

export async function printReport(
  b: Branding,
  title: string,
  items: any[],
  showPrice: boolean,
): Promise<void> {
  const groups: Record<string, Record<string, any[]>> = {};
  for (const it of items) {
    const co = it.company || "All";
    const cat = it.category || "Uncategorized";
    groups[co] = groups[co] || {};
    groups[co][cat] = groups[co][cat] || [];
    groups[co][cat].push(it);
  }
  let body = "";
  let grand = 0;
  for (const co of Object.keys(groups).sort()) {
    body += `<h3 style="margin:16px 0 4px;font-size:15px;border-bottom:2px solid #333">${esc(co)}</h3>`;
    for (const cat of Object.keys(groups[co]).sort()) {
      const rows = groups[co][cat]
        .map((it: any) => {
          grand += Number(it.price) || 0;
          return `<tr><td>${esc(it.part_number)}</td><td>${esc(it.part_name || "")}</td><td>${esc(
            it.condition || "",
          )}</td>${showPrice ? `<td>${it.price != null ? "Rs. " + it.price : ""}</td>` : ""}<td>${esc(
            it.at || it.created_at ? new Date(it.at || it.created_at).toLocaleDateString() : "",
          )}</td></tr>`;
        })
        .join("");
      body += `<div style="font-weight:bold;color:#555;margin:8px 0 2px">${esc(cat)} (${groups[co][cat].length})</div>
        <table><thead><tr><th>Part Number</th><th>Name</th><th>Condition</th>${
          showPrice ? "<th>Price</th>" : ""
        }<th>Date</th></tr></thead><tbody>${rows}</tbody></table>`;
    }
  }
  body += `<div class="tot">Total items: ${items.length}${showPrice ? ` &nbsp;|&nbsp; Total Rs. ${grand}` : ""}</div>`;
  await printHtml(wrap(title, b, body));
}
