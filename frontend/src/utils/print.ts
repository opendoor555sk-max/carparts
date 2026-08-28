import * as Print from "expo-print";

import { fileUrl } from "@/src/api/client";

export type Branding = {
  name: string;
  gst?: string;
  phone?: string;
  address?: string;
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
    loc ? ["Location", loc] : null,
    data.buyer ? ["Buyer", data.buyer] : null,
    data.price != null && data.price !== "" ? ["Price", "Rs. " + data.price] : null,
    data.by ? ["By", data.by] : null,
  ];
  const rows = lines
    .filter(Boolean)
    .map((r: any) => `<tr><th style="width:35%">${esc(r[0])}</th><td>${esc(r[1])}</td></tr>`)
    .join("");
  await printHtml(wrap(kind === "BUY" ? "Purchase Receipt" : "Sale Receipt", b, `<table>${rows}</table>`));
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
