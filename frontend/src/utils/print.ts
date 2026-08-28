import * as Print from "expo-print";

function esc(s: any): string {
  return String(s ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function wrap(title: string, store: string, body: string): string {
  return `<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>
    body{font-family:-apple-system,Roboto,Arial,sans-serif;padding:24px;color:#111}
    h1{font-size:22px;margin:0}
    h2{font-size:14px;color:#555;margin:4px 0 12px}
    .meta{font-size:12px;color:#777;margin-bottom:16px}
    table{width:100%;border-collapse:collapse;font-size:12px}
    th,td{border:1px solid #ccc;padding:6px 8px;text-align:left}
    th{background:#f2f2f2}
    .tot{margin-top:16px;font-size:14px;font-weight:bold}
  </style></head><body>
    <h1>${esc(store || "Auto Parts Store")}</h1>
    <h2>${esc(title)}</h2>
    <div class="meta">${esc(new Date().toLocaleString())}</div>
    ${body}
  </body></html>`;
}

export async function printHtml(html: string): Promise<void> {
  await Print.printAsync({ html });
}

export async function printInventory(store: string, units: any[]): Promise<void> {
  const rows = units
    .map(
      (u, i) =>
        `<tr><td>${i + 1}</td><td>${esc(u.part_number)}</td><td>${esc(u.part_name || "")}</td><td>${esc(
          u.condition || "",
        )}</td><td>${esc(
          [u.location?.rack, u.location?.shelf, u.location?.box, u.location?.position]
            .filter(Boolean)
            .join(" → "),
        )}</td></tr>`,
    )
    .join("");
  const body = `<table><thead><tr><th>#</th><th>Part Number</th><th>Name</th><th>Condition</th><th>Location</th></tr></thead><tbody>${rows}</tbody></table><div class="tot">Total units: ${units.length}</div>`;
  await printHtml(wrap("Inventory Report", store, body));
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

export async function printReceipt(store: string, kind: "BUY" | "SELL", data: ReceiptData): Promise<void> {
  const loc = [data.location?.rack, data.location?.shelf, data.location?.box, data.location?.position]
    .filter(Boolean)
    .join(" → ");
  const lines: (string[] | null)[] = [
    ["Type", kind === "BUY" ? "Purchase (ખરીદી)" : "Sale (વેચાણ)"],
    ["Part Number", data.part_number],
    ["Name", data.name || "-"],
    ["Condition", data.condition || "-"],
    loc ? ["Location", loc] : null,
    data.buyer ? ["Buyer", data.buyer] : null,
    data.price != null && data.price !== "" ? ["Price", "₹ " + data.price] : null,
    data.by ? ["By", data.by] : null,
  ];
  const rows = lines
    .filter(Boolean)
    .map((r: any) => `<tr><th style="width:35%">${esc(r[0])}</th><td>${esc(r[1])}</td></tr>`)
    .join("");
  const body = `<table>${rows}</table>`;
  await printHtml(wrap(kind === "BUY" ? "Purchase Receipt" : "Sale Receipt", store, body));
}

export async function printRequirements(store: string, reqs: any[]): Promise<void> {
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
  await printHtml(wrap("Requirements / Inquiry List", store, body));
}

export async function printHistory(store: string, kind: "buy" | "sell", txns: any[]): Promise<void> {
  const rows = txns
    .map(
      (t, i) =>
        `<tr><td>${i + 1}</td><td>${esc(t.part_number)}</td><td>${esc(t.part_name || "")}</td><td>${esc(
          t.by || "",
        )}</td><td>${esc(t.buyer || "")}</td><td>${esc(t.price != null ? "₹ " + t.price : "")}</td><td>${esc(
          t.at ? new Date(t.at).toLocaleString() : "",
        )}</td></tr>`,
    )
    .join("");
  const total = txns.reduce((s, t) => s + (Number(t.price) || 0), 0);
  const body = `<table><thead><tr><th>#</th><th>Part Number</th><th>Name</th><th>By</th><th>Buyer</th><th>Price</th><th>Date</th></tr></thead><tbody>${rows}</tbody></table><div class="tot">Entries: ${txns.length} &nbsp; | &nbsp; Total ₹ ${total}</div>`;
  await printHtml(wrap(kind === "buy" ? "Purchase (ખરીદ) History" : "Sale (વેચાણ) History", store, body));
}
