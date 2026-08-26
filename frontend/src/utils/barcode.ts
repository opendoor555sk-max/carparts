// Extracts the real Hyundai/Kia/Maruti/Tata/Mahindra OEM part number from a
// scanned barcode/QR payload. Salvage 2D labels encode the part number inside a
// long coded string with field codes (e.g. ">06VTELEP954A0CCAF0SP954SVAFEK...").
// The OEM part number here is "954A0CCAF0" (10 alphanumeric chars, first a digit,
// commonly preceded by a 'P' field code).
//
// Rules:
// - Short values (a user-typed part number, < 16 chars) are returned as-is.
// - Long coded payloads are mined for the 10-char OEM part number.

export function extractPartNumber(raw: string): string {
  const original = (raw ?? "").trim();
  if (!original) return original;

  // Typed part numbers (short) — trust as-is.
  if (original.length < 16) return original;

  // Coded barcode: strip separators/control chars.
  const s = original.toUpperCase().replace(/[^0-9A-Z]/g, "");

  // 1) 'P' field code followed by a 10-char part number (digit + 9 alnum),
  //    ending at another letter field code or end of string.
  let m = s.match(/P([0-9][0-9A-Z]{9})(?=[A-Z]|$)/);
  if (m) return m[1];

  // 2) First 10-char window that starts with a digit and contains a letter
  //    (Hyundai/Kia electrical numbers like 954A0CCAF0, 95400xxxxx).
  m = s.match(/([0-9][0-9A-Z]{4}[A-Z][0-9A-Z]{4})/);
  if (m) return m[1];

  // 3) Could not confidently extract — return the raw payload so the user/admin
  //    can correct it manually rather than silently guessing wrong.
  return original;
}
