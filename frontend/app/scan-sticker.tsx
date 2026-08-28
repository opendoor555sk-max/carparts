import { useCallback, useEffect, useMemo, useState } from "react";
import { ActivityIndicator, Image, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { SvgXml } from "react-native-svg";
import * as ImagePicker from "expo-image-picker";
import { ImageManipulator, SaveFormat } from "expo-image-manipulator";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { FilterChip, Header } from "@/src/components/ui";
import { printHtml } from "@/src/utils/print";
import { CODE_TYPES, codeSvg as genCodeSvg, svgRatio } from "@/src/utils/codegen";
import { Box, SHEET_LAYOUTS, StickerTemplate, TplLine, generateRichStickerSheetHtml } from "@/src/utils/labelSheet";
import { colors, font, radius, spacing } from "@/src/theme";

type ScanResult = { aspect: number; part_number: string; lines: { text: string; bold?: boolean }[]; code: { type: string } | null; logo: Box | null };
type CodeType = string;

const PREVIEW_W = 320;

const COMPANIES = ["Hyundai / Kia", "Maruti Suzuki", "Tata", "Mahindra", "Toyota", "Honda", "Nissan", "Renault", "Ford", "Volkswagen", "Skoda", "MG", "Datsun", "Chevrolet", "Fiat", "Jeep", "Citroen", "Isuzu", "Other"];

// Companies that have a dedicated 2-column layout preset. Others fall back to generic.
const FORMATTED_COMPANIES = ["Hyundai / Kia"];

// Clean vertical layout: no overlap, font shrinks to fit width, code in right column.
function layoutLines(raw: { text: string; bold?: boolean }[], aspect: number, hasCode: boolean, topPad: number): TplLine[] {
  const lines = raw.filter((l) => (l.text || "").trim());
  if (!lines.length) return [];
  const frac = hasCode ? 0.66 : 0.96;
  const widthUnits = aspect * 100 * frac;
  const bottom = 4;
  const slot = Math.max(4, (100 - topPad - bottom) / lines.length);
  return lines.map((ln, i) => {
    const len = Math.max(1, (ln.text || "").length);
    // +25% bigger fonts: taller vertical slot use, larger cap, tighter char-width estimate.
    const fs = Math.max(2.75, Math.min(slot * 0.92, widthUnits / (len * 0.46), 11.25));
    return { text: ln.text, x: 3, y: topPad + i * slot + (slot - fs) / 2, size: fs, bold: ln.bold };
  });
}

// ---------------- Zone-based placement (user arranges each line freely) ----------------
// Each line is assigned a ZONE; the user can re-assign any line's zone and reorder lines.
type ZonedLine = { text: string; bold?: boolean; zone?: string };
type Zone = { key: string; label: string; x: number; y0: number; dy: number; w: number; base: number; center?: boolean };
const ZONES: Zone[] = [
  { key: "leftTop",   label: "L-Top",    x: 2,  y0: 19, dy: 6.5, w: 40, base: 6 },
  { key: "rightTop",  label: "R-Top",    x: 44, y0: 3,  dy: 5.2, w: 54, base: 5.4 },
  { key: "leftMid",   label: "L-Mid",    x: 2,  y0: 44, dy: 6.5, w: 54, base: 6 },
  { key: "rightMid",  label: "R-Mid",    x: 44, y0: 46, dy: 6,   w: 40, base: 5.4 },
  { key: "botCenter", label: "Bottom-C", x: 0,  y0: 82, dy: 5,   w: 96, base: 5.5, center: true },
  { key: "bottom",    label: "Bottom",   x: 2,  y0: 90, dy: 5,   w: 96, base: 4.6 },
];

// Positions zoned lines: groups by zone (in array order) and stacks each group.
function positionLines(lines: ZonedLine[], aspect: number): TplLine[] {
  const fit = (w: number, len: number, base: number) =>
    Math.max(2.0, Math.min(base, (w * aspect) / (Math.max(1, len) * 0.5)));
  const count: Record<string, number> = {};
  return lines.map((ln) => {
    const z = ZONES.find((zz) => zz.key === ln.zone) || ZONES[2]; // default L-Mid
    const i = count[z.key] || 0; count[z.key] = i + 1;
    const size = fit(z.w, ln.text.length, z.base);
    let x = z.x;
    if (z.center) {
      const wpct = (ln.text.length * 0.5 * size) / aspect; // approx text width (% of width)
      x = Math.max(1, 50 - wpct / 2);
    }
    return { text: ln.text, x, y: z.y0 + i * z.dy, size, bold: ln.bold, zone: z.key };
  });
}

// Auto-guess a starting zone for each scanned line of a Hyundai/Kia OEM label.
// The user can override any of these afterwards.
function autoZonesHK(raw: { text: string; bold?: boolean }[]): ZonedLine[] {
  const lines = raw.map((l) => ({ text: (l.text || "").trim(), bold: l.bold })).filter((l) => l.text);
  const isBottom = (u: string) => /(MADE IN|ELECTRONIC|SEOYON|PVT|LTD|\/\/)/.test(u);
  const isBrand = (u: string) => /MOTORS/.test(u) || /^HYUNDAI\s*KIA/.test(u);
  const isRight = (u: string) => /(UNIT ASSY|ASSY|P\/N|LOT|H\/W|S\/W|VER|HKMC|SYEC)/.test(u);
  const isLeft = (u: string) => /(MODEL|IFT|^TA[ -])/.test(u);
  const isCenter = (t: string) => t.length <= 6 && /^[A-Za-z]+$/.test(t); // e.g. VBHH
  const out: ZonedLine[] = [];
  let last = "leftMid";
  for (const ln of lines) {
    const u = ln.text.toUpperCase();
    let zone: string;
    if (isBottom(u)) zone = "bottom";
    else if (isBrand(u)) zone = "leftTop";
    else if (isCenter(ln.text)) zone = "botCenter";
    else if (isLeft(u)) zone = "leftMid";
    else if (isRight(u)) zone = "rightTop";
    else zone = last === "rightTop" ? "rightTop" : last === "bottom" ? "bottom" : "leftMid"; // continuation
    last = zone;
    out.push({ text: ln.text, bold: ln.bold, zone });
  }
  return out;
}

function buildLines(raw: { text: string; bold?: boolean }[], aspect: number, hasCode: boolean, hasLogo: boolean, company?: string): TplLine[] {
  if (company && FORMATTED_COMPANIES.includes(company)) return positionLines(autoZonesHK(raw), aspect);
  return layoutLines(raw, aspect, hasCode, hasLogo ? 20 : 4);
}

export default function ScanSticker() {
  const router = useRouter();
  const { show } = useToast();

  const [busy, setBusy] = useState(false);
  const [tpl, setTpl] = useState<StickerTemplate | null>(null);
  const [partNumber, setPartNumber] = useState("");
  const [codeType, setCodeType] = useState<CodeType>("qr");
  const [codeSize, setCodeSize] = useState(10);

  const [layoutCode, setLayoutCode] = useState("24L");
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [marginTop, setMarginTop] = useState("");
  const [marginLeft, setMarginLeft] = useState("");
  const [pageMargin, setPageMargin] = useState("0");
  const [saved, setSaved] = useState<any[]>([]);
  const [logos, setLogos] = useState<any[]>([]);
  const [company, setCompany] = useState("Hyundai / Kia");
  const [rawLines, setRawLines] = useState<{ text: string; bold?: boolean }[]>([]);
  const [selLines, setSelLines] = useState<Set<number>>(new Set());
  const [nudgeStep, setNudgeStep] = useState(2);
  const [companyFormats, setCompanyFormats] = useState<Record<string, any>>({});
  const layout = useMemo(() => SHEET_LAYOUTS.find((l) => l.code === layoutCode)!, [layoutCode]);

  const loadSaved = useCallback(async () => {
    try { setSaved(await api.get<any[]>("/sticker-templates")); } catch {}
    try { setLogos(await api.get<any[]>("/logos")); } catch {}
    try {
      const fmts = await api.get<any[]>("/company-formats");
      const map: Record<string, any> = {};
      fmts.forEach((f) => { map[f.company] = f.template; });
      setCompanyFormats(map);
    } catch {}
  }, []);
  useEffect(() => { loadSaved(); }, [loadSaved]);

  // Re-apply a saved company format's arrangement onto freshly-scanned lines.
  // Lines are matched by their field key (text before ':' with digits stripped),
  // so recurring OEM fields (HKMC P/N, MODEL, IFT ID...) land where the user placed them.
  const fieldKey = (t: string) => {
    const u = (t || "").toUpperCase().trim();
    const c = u.indexOf(":");
    const k = (c >= 0 ? u.slice(0, c) : u).replace(/[0-9]/g, "").replace(/\s+/g, " ").trim();
    return k || u.slice(0, 4);
  };
  const applyCompanyFormat = (tplIn: StickerTemplate, fmt: any): StickerTemplate => {
    if (!fmt || !Array.isArray(fmt.lines)) return tplIn;
    const map = new Map<string, any>();
    for (const s of fmt.lines) { const k = fieldKey(s.text); if (!map.has(k)) map.set(k, s); }
    const lines = tplIn.lines.map((l) => {
      const s = map.get(fieldKey(l.text));
      return s ? { ...l, x: s.x, y: s.y, size: s.size, bold: s.bold } : l;
    });
    const code = tplIn.code && fmt.code
      ? { ...tplIn.code, type: fmt.code.type || tplIn.code.type, sizeMm: fmt.code.sizeMm ?? tplIn.code.sizeMm, box: { ...tplIn.code.box, y: fmt.code.box?.y ?? tplIn.code.box.y } }
      : tplIn.code;
    return { ...tplIn, lines, code };
  };

  const LOGO_START: Box = { x: 3, y: 2, w: 34, h: 15 };

  const applyLogo = (dataUrl: string | null) =>
    setTpl((prev) => {
      if (!prev) return prev;
      const logo = dataUrl ? { dataUrl, box: prev.logo?.box || LOGO_START } : null;
      // Formatted (zone) layout is independent of the logo → keep the user's line arrangement.
      if (prev.company && FORMATTED_COMPANIES.includes(prev.company)) return { ...prev, logo };
      const lines = layoutLines(prev.lines.map((l) => ({ text: l.text, bold: l.bold })), prev.aspect, !!prev.code, logo ? 20 : 4);
      return { ...prev, logo, lines };
    });

  const nudgeLogo = (dx: number, dy: number) =>
    setTpl((prev) => {
      if (!prev || !prev.logo) return prev;
      const b = prev.logo.box;
      return { ...prev, logo: { ...prev.logo, box: { ...b, x: Math.max(0, Math.min(99, b.x + dx * nudgeStep)), y: Math.max(0, Math.min(99, b.y + dy * nudgeStep)) } } };
    });
  const resizeLogo = (delta: number) =>
    setTpl((prev) => {
      if (!prev || !prev.logo) return prev;
      const b = prev.logo.box;
      const ratio = b.h / b.w;
      const w = Math.max(6, Math.min(90, b.w + delta));
      return { ...prev, logo: { ...prev.logo, box: { ...b, w, h: Math.max(3, w * ratio) } } };
    });

  const addLogo = async () => {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) return show("Gallery permission needed", "error");
    const r = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ["images"], base64: true, quality: 0.9 });
    if (r.canceled || !r.assets?.[0]?.base64) return;
    try {
      const c = ImageManipulator.manipulate(r.assets[0].uri);
      c.resize({ width: 300 });
      const rr = await c.renderAsync();
      const oo = await rr.saveAsync({ format: SaveFormat.PNG, base64: true });
      const dataUrl = `data:image/png;base64,${oo.base64}`;
      await api.post("/logos", { name: "Logo", data_url: dataUrl });
      show("Logo saved", "success");
      loadSaved();
      applyLogo(dataUrl);
    } catch (e: any) { show(e?.message || "Logo save failed", "error"); }
  };
  const deleteLogo = async (id: string) => { try { await api.del(`/logos/${id}`); loadSaved(); } catch {} };

  const codeBox = (aspect: number, comp?: string): Box => {
    const h = 34, w = Math.min(40, h / aspect);
    // Hyundai/Kia: QR sits lower (mid band, below the P/N text block). Generic: vertical center.
    const y = comp && FORMATTED_COMPANIES.includes(comp) ? 46 : (100 - h) / 2;
    return { x: 100 - w - 3, y, w, h };
  };

  const processImage = async (asset: ImagePicker.ImagePickerAsset) => {
    setBusy(true);
    try {
      let sendB64 = asset.base64!;
      try {
        const rs = ImageManipulator.manipulate(asset.uri);
        rs.resize({ width: 800 });
        const rimg = await rs.renderAsync();
        const rout = await rimg.saveAsync({ format: SaveFormat.JPEG, base64: true });
        if (rout.base64) sendB64 = rout.base64;
      } catch {}

      const res = await api.post<ScanResult>("/scan-sticker", { image_base64: sendB64 });
      const aspect = res.aspect || 1.6;
      const hasCode = !!res.code;
      const pn = res.part_number || "";
      const rl = res.lines || [];
      // Auto-detect company from the sticker text.
      const joined = rl.map((l) => (l.text || "").toUpperCase()).join(" ");
      let comp = company;
      if (/HYUNDAI/.test(joined) || /\bKIA\b/.test(joined)) comp = "Hyundai / Kia";
      // Hyundai/Kia OEM labels use a DataMatrix code (not QR). Others: keep detected.
      let ct: CodeType = res.code?.type === "barcode" ? "barcode" : "qr";
      if (FORMATTED_COMPANIES.includes(comp)) ct = "datamatrix";
      setRawLines(rl);
      setCompany(comp);
      buildTpl(rl, aspect, hasCode, ct, pn, null, comp, codeSize);
      // If the user saved a format for this company, re-apply their arrangement.
      const fmt = companyFormats[comp];
      if (fmt) {
        if (fmt.code?.type) ct = fmt.code.type;
        if (fmt.code?.sizeMm) setCodeSize(fmt.code.sizeMm);
        setTpl((prev) => (prev ? applyCompanyFormat(prev, fmt) : prev));
      }
      setPartNumber(pn);
      setCodeType(ct);
      setSelected(new Set());
      show(`Sticker generated${fmt ? ` (${comp} saved format)` : FORMATTED_COMPANIES.includes(comp) ? ` (${comp} format)` : ""}`, "success");
    } catch (e: any) {
      show(e?.message || "Scan failed", "error");
    } finally {
      setBusy(false);
    }
  };

  const buildTpl = (rl: { text: string; bold?: boolean }[], aspect: number, hasCode: boolean, ct: CodeType, pn: string, logo: StickerTemplate["logo"], comp: string, sizeMm: number) => {
    const lines = buildLines(rl, aspect, hasCode, !!logo, comp);
    setTpl({ aspect, lines, code: hasCode ? { type: ct, value: pn, box: codeBox(aspect, comp), sizeMm } : null, logo, company: comp });
  };

  const applyCompany = (comp: string) => {
    setCompany(comp);
    const fmt = companyFormats[comp];
    setTpl((prev) => {
      if (!prev) return prev;
      const lines = buildLines(rawLines, prev.aspect, !!prev.code, !!prev.logo, comp);
      let code = prev.code ? { ...prev.code, box: codeBox(prev.aspect, comp) } : null;
      if (code) {
        // Formatted companies default to DataMatrix; leaving reverts DataMatrix -> QR.
        const nt: CodeType = FORMATTED_COMPANIES.includes(comp) ? "datamatrix" : code.type === "datamatrix" ? "qr" : code.type;
        code = { ...code, type: nt };
        setCodeType(nt);
      }
      const next = { ...prev, company: comp, lines, code };
      if (fmt) {
        if (fmt.code?.sizeMm) setCodeSize(fmt.code.sizeMm);
        if (fmt.code?.type) setCodeType(fmt.code.type);
        return applyCompanyFormat(next, fmt);
      }
      return next;
    });
  };

  const pickGallery = async () => {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) return show("Gallery permission needed", "error");
    const r = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ["images"], base64: true, quality: 0.9 });
    if (!r.canceled && r.assets?.[0]?.base64) processImage(r.assets[0]);
  };
  const takePhoto = async () => {
    const perm = await ImagePicker.requestCameraPermissionsAsync();
    if (!perm.granted) return show("Camera permission needed", "error");
    const r = await ImagePicker.launchCameraAsync({ base64: true, quality: 0.9 });
    if (!r.canceled && r.assets?.[0]?.base64) processImage(r.assets[0]);
  };

  const applyPn = (value: string) => {
    setPartNumber(value);
    setTpl((prev) => (prev ? { ...prev, code: prev.code ? { ...prev.code, value } : null } : prev));
  };
  const setCode = (t: CodeType) => {
    setCodeType(t);
    setTpl((prev) => (prev && prev.code ? { ...prev, code: { ...prev.code, type: t } } : prev));
  };
  const changeCodeSize = (delta: number) => {
    const s = Math.max(5, Math.min(30, Math.round((codeSize + delta) * 10) / 10));
    setCodeSize(s);
    setTpl((prev) => (prev && prev.code ? { ...prev, code: { ...prev.code, sizeMm: s } } : prev));
  };
  const editLine = (idx: number, text: string) =>
    setTpl((prev) => (prev ? { ...prev, lines: prev.lines.map((l, i) => (i === idx ? { ...l, text } : l)) } : prev));

  const toggleLineSel = (idx: number) =>
    setSelLines((p) => { const n = new Set(p); if (n.has(idx)) n.delete(idx); else n.add(idx); return n; });
  const selectAllLines = () => setSelLines(new Set((tpl?.lines || []).map((_, i) => i)));
  const clearLineSel = () => setSelLines(new Set());

  const nudgeSel = (dx: number, dy: number) =>
    setTpl((prev) => {
      if (!prev || selLines.size === 0) return prev;
      const lines = prev.lines.map((l, i) =>
        selLines.has(i)
          ? { ...l, x: Math.max(0, Math.min(99, l.x + dx * nudgeStep)), y: Math.max(0, Math.min(99, l.y + dy * nudgeStep)) }
          : l);
      return { ...prev, lines };
    });
  const resizeSel = (delta: number) =>
    setTpl((prev) => {
      if (!prev || selLines.size === 0) return prev;
      const lines = prev.lines.map((l, i) =>
        selLines.has(i) ? { ...l, size: Math.max(2, Math.min(20, Math.round((l.size + delta) * 10) / 10)) } : l);
      return { ...prev, lines };
    });
  const boldSel = () =>
    setTpl((prev) => {
      if (!prev || selLines.size === 0) return prev;
      const anyNotBold = prev.lines.some((l, i) => selLines.has(i) && !l.bold);
      const lines = prev.lines.map((l, i) => (selLines.has(i) ? { ...l, bold: anyNotBold } : l));
      return { ...prev, lines };
    });

  const saveTemplate = async () => {
    if (!tpl) return;
    try {
      await api.post("/sticker-templates", { name: (partNumber || "Sticker").trim(), bg_data_url: JSON.stringify(tpl), aspect: tpl.aspect, pn_box: null, part_number: partNumber });
      show("Template saved", "success");
      loadSaved();
    } catch (e: any) { show(e?.message || "Save failed", "error"); }
  };

  const saveCompanyFormat = async () => {
    if (!tpl) return;
    try {
      // Store only the arrangement recipe (positions/sizes/code) — not the specific values.
      const template = {
        lines: tpl.lines.map((l) => ({ text: l.text, x: l.x, y: l.y, size: l.size, bold: l.bold })),
        code: tpl.code ? { type: tpl.code.type, sizeMm: tpl.code.sizeMm ?? codeSize, box: { y: tpl.code.box.y } } : null,
        logo: tpl.logo ? { box: tpl.logo.box } : null,
      };
      await api.post("/company-formats", { company, template });
      setCompanyFormats((m) => ({ ...m, [company]: template }));
      show(`Saved as ${company} format`, "success");
    } catch (e: any) { show(e?.message || "Save failed", "error"); }
  };
  const openTemplate = (t: any) => {
    try {
      const parsed: StickerTemplate = JSON.parse(t.bg_data_url);
      const comp = parsed.company || "Hyundai / Kia";
      const rawT = (parsed.lines || []).map((l) => ({ text: l.text, bold: l.bold }));
      // Legacy templates saved before zones: auto-arrange so the zone editor works.
      const needsZones = FORMATTED_COMPANIES.includes(comp) && !(parsed.lines || []).some((l) => l.zone);
      const lines = needsZones ? positionLines(autoZonesHK(rawT), parsed.aspect || 1.6) : parsed.lines;
      setTpl({ ...parsed, company: comp, lines });
      setPartNumber(t.part_number || parsed.code?.value || "");
      setCodeType(parsed.code?.type || "qr");
      setCodeSize(parsed.code?.sizeMm || 10);
      setCompany(comp);
      setRawLines(rawT);
      setSelected(new Set());
    } catch { show("Could not open template", "error"); }
  };
  const deleteTemplate = async (id: string) => { try { await api.del(`/sticker-templates/${id}`); loadSaved(); } catch {} };

  const toggleCell = (num: number) =>
    setSelected((p) => { const n = new Set(p); if (n.has(num)) n.delete(num); else n.add(num); return n; });

  const onPrint = async () => {
    if (!tpl) return;
    if (selected.size === 0) return show("Tap blocks to print on", "error");
    try {
      const mt = marginTop.trim() === "" ? null : Math.max(0, parseFloat(marginTop) || 0);
      const ml = marginLeft.trim() === "" ? null : Math.max(0, parseFloat(marginLeft) || 0);
      const pm = pageMargin.trim() === "" ? 0 : Math.max(0, parseFloat(pageMargin) || 0);
      await printHtml(generateRichStickerSheetHtml(tpl, { layout, cells: Array.from(selected), showBorder: false, marginTop: mt, marginLeft: ml, pageMargin: pm }));
    } catch (e: any) { show(e?.message || "Print failed", "error"); }
  };

  const previewH = tpl ? PREVIEW_W / (tpl.aspect || 1.6) : 220;
  const codeSvg = useMemo(() => {
    if (!tpl?.code?.value) return "";
    return genCodeSvg(codeType, tpl.code.value);
  }, [tpl?.code?.value, codeType]);
  const codeRatio = useMemo(() => (codeSvg ? svgRatio(codeSvg) : 1), [codeSvg]);

  const cellW = Math.min(320 / layout.cols, 60);
  const cellH = Math.max(10, cellW / (layout.w / layout.h));

  return (
    <View style={styles.flex}>
      <Header title="AI Sticker Scanner" subtitle="Generate a clean sticker → change part no → print" onBack={() => router.back()} />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
        <View style={styles.pickRow}>
          <Pressable style={styles.pickBtn} onPress={pickGallery} disabled={busy} testID="pick-gallery">
            <Ionicons name="images" size={20} color={colors.onBrand} /><Text style={styles.pickText}>Gallery</Text>
          </Pressable>
          <Pressable style={styles.pickBtn} onPress={takePhoto} disabled={busy} testID="take-photo">
            <Ionicons name="camera" size={20} color={colors.onBrand} /><Text style={styles.pickText}>Camera</Text>
          </Pressable>
        </View>

        {saved.length ? (
          <>
            <Text style={styles.section}>SAVED STICKERS (tap to reuse)</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.savedRow}>
              {saved.map((t) => (
                <View key={t.id} style={styles.savedCard}>
                  <Pressable onPress={() => openTemplate(t)} style={styles.savedInner} testID={`saved-${t.id}`}>
                    <Ionicons name="pricetag" size={20} color={colors.brand} />
                    <Text numberOfLines={1} style={styles.savedName}>{t.name}</Text>
                  </Pressable>
                  <Pressable style={styles.savedDel} onPress={() => deleteTemplate(t.id)} testID={`del-${t.id}`}>
                    <Ionicons name="close-circle" size={18} color={colors.error} />
                  </Pressable>
                </View>
              ))}
            </ScrollView>
          </>
        ) : null}

        {busy ? (
          <View style={styles.busy}><ActivityIndicator size="large" color={colors.brand} /><Text style={styles.dim}>Generating clean sticker… (a few seconds)</Text></View>
        ) : null}

        {tpl ? (
          <>
            <Text style={styles.section}>PREVIEW (clean generated)</Text>
            <View style={[styles.preview, { width: PREVIEW_W, height: previewH }]}>
              {tpl.logo && tpl.logo.dataUrl ? (
                <Image source={{ uri: tpl.logo.dataUrl }} resizeMode="contain" style={{ position: "absolute", left: (tpl.logo.box.x / 100) * PREVIEW_W, top: (tpl.logo.box.y / 100) * previewH, width: (tpl.logo.box.w / 100) * PREVIEW_W, height: (tpl.logo.box.h / 100) * previewH }} />
              ) : null}
              {tpl.code && codeSvg ? (
                (() => {
                  const hpx = Math.min(previewH - 2, (codeSize / layout.h) * previewH);
                  const wpx = codeRatio > 1.3 ? Math.min(PREVIEW_W * 0.6, hpx * codeRatio) : hpx;
                  const left = PREVIEW_W - wpx - (2 / layout.w) * PREVIEW_W;
                  const top = Math.max(0, Math.min((tpl.code.box.y / 100) * previewH, previewH - hpx));
                  return (
                    <View style={{ position: "absolute", left, top, width: wpx, height: hpx }}>
                      <SvgXml xml={codeSvg} width="100%" height="100%" preserveAspectRatio="xMidYMid meet" />
                    </View>
                  );
                })()
              ) : null}
              {tpl.lines.map((ln, i) => (
                <Text key={i} numberOfLines={1} style={{ position: "absolute", left: (ln.x / 100) * PREVIEW_W, top: (ln.y / 100) * previewH, fontSize: Math.max(6, (ln.size / 100) * previewH), fontWeight: ln.bold ? "800" : "500", color: "#000" }}>{ln.text}</Text>
              ))}
            </View>

            <Text style={styles.section}>PART NUMBER</Text>
            <TextInput style={styles.input} value={partNumber} onChangeText={applyPn} autoCapitalize="characters" testID="scan-pn" />

            <Text style={styles.flabel}>COMPANY FORMAT (tap to switch layout)</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
              {COMPANIES.map((c) => (
                <FilterChip
                  key={c}
                  label={FORMATTED_COMPANIES.includes(c) ? `${c} ★` : c}
                  active={company === c}
                  onPress={() => applyCompany(c)}
                  testID={`company-${c}`}
                />
              ))}
            </ScrollView>
            <Text style={styles.hint}>
              {FORMATTED_COMPANIES.includes(company)
                ? `★ ${company} format: arrange each line's place (L-Top / R-Top / L-Mid / Bottom), reorder, move logo. DataMatrix on the right.`
                : `${company}: standard layout. A dedicated format can be added later.`}
            </Text>

            <Text style={styles.flabel}>COMPANY LOGO (tap to set on sticker)</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.savedRow}>
              <Pressable style={styles.logoAdd} onPress={addLogo} testID="logo-add">
                <Ionicons name="add" size={20} color={colors.brand} />
                <Text style={styles.saveText}>Add</Text>
              </Pressable>
              <Pressable style={styles.logoAdd} onPress={() => applyLogo(null)} testID="logo-none">
                <Ionicons name="ban" size={18} color={colors.info} />
                <Text style={styles.savedName}>None</Text>
              </Pressable>
              {logos.map((lg) => (
                <View key={lg.id} style={styles.logoCard}>
                  <Pressable onPress={() => applyLogo(lg.data_url)} testID={`logo-${lg.id}`}>
                    <Image source={{ uri: lg.data_url }} resizeMode="contain" style={styles.logoThumb} />
                  </Pressable>
                  <Pressable style={styles.savedDel} onPress={() => deleteLogo(lg.id)} testID={`logodel-${lg.id}`}>
                    <Ionicons name="close-circle" size={18} color={colors.error} />
                  </Pressable>
                </View>
              ))}
            </ScrollView>
            {tpl.logo ? (
              <>
                <Text style={styles.subHint}>Logo — move &amp; resize (step {nudgeStep})</Text>
                <View style={styles.sizeRow}>
                  <Pressable style={styles.sizeBtn} onPress={() => nudgeLogo(-1, 0)} testID="logo-left"><Ionicons name="chevron-back" size={20} color={colors.onSurface} /></Pressable>
                  <Pressable style={styles.sizeBtn} onPress={() => nudgeLogo(0, -1)} testID="logo-up"><Ionicons name="chevron-up" size={20} color={colors.onSurface} /></Pressable>
                  <Pressable style={styles.sizeBtn} onPress={() => nudgeLogo(0, 1)} testID="logo-down"><Ionicons name="chevron-down" size={20} color={colors.onSurface} /></Pressable>
                  <Pressable style={styles.sizeBtn} onPress={() => nudgeLogo(1, 0)} testID="logo-right"><Ionicons name="chevron-forward" size={20} color={colors.onSurface} /></Pressable>
                  <Pressable style={styles.sizeBtn} onPress={() => resizeLogo(-3)} testID="logo-smaller"><Ionicons name="remove" size={20} color={colors.onSurface} /></Pressable>
                  <Pressable style={styles.sizeBtn} onPress={() => resizeLogo(3)} testID="logo-bigger"><Ionicons name="add" size={20} color={colors.onSurface} /></Pressable>
                </View>
              </>
            ) : null}

            <Text style={styles.flabel}>CODE TYPE ({CODE_TYPES.length} types)</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
              {CODE_TYPES.map((c) => (
                <FilterChip key={c.key} label={c.label} active={codeType === c.key} onPress={() => setCode(c.key)} testID={`ct-${c.key}`} />
              ))}
            </ScrollView>

            <Text style={styles.subHint}>Code size: {codeSize} mm</Text>
            <View style={styles.sizeRow}>
              <Pressable style={styles.sizeBtn} onPress={() => changeCodeSize(-1)} testID="code-size-minus">
                <Ionicons name="remove" size={20} color={colors.onSurface} />
              </Pressable>
              <View style={styles.sizeVal}><Text style={styles.sizeValText}>{codeSize} mm</Text></View>
              <Pressable style={styles.sizeBtn} onPress={() => changeCodeSize(1)} testID="code-size-plus">
                <Ionicons name="add" size={20} color={colors.onSurface} />
              </Pressable>
            </View>

            <Text style={styles.flabel}>ARRANGE — select lines, then move / resize together</Text>
            <View style={styles.arrangeTop}>
              <Text style={styles.subHint}>Step</Text>
              {[1, 2, 3, 5].map((s) => (
                <FilterChip key={s} label={`${s}`} active={nudgeStep === s} onPress={() => setNudgeStep(s)} testID={`step-${s}`} />
              ))}
              <Pressable style={styles.miniBtn} onPress={selectAllLines} testID="sel-all"><Text style={styles.miniBtnText}>All</Text></Pressable>
              <Pressable style={styles.miniBtn} onPress={clearLineSel} testID="sel-clear"><Text style={styles.miniBtnText}>Clear</Text></Pressable>
            </View>
            <View style={styles.padWrap}>
              <View style={styles.pad}>
                <View style={styles.padRow}><Pressable style={styles.padBtn} onPress={() => nudgeSel(0, -1)} testID="nudge-up"><Ionicons name="chevron-up" size={22} color={colors.onSurface} /></Pressable></View>
                <View style={styles.padRow}>
                  <Pressable style={styles.padBtn} onPress={() => nudgeSel(-1, 0)} testID="nudge-left"><Ionicons name="chevron-back" size={22} color={colors.onSurface} /></Pressable>
                  <View style={styles.padCenter}><Text style={styles.padCount}>{selLines.size}</Text></View>
                  <Pressable style={styles.padBtn} onPress={() => nudgeSel(1, 0)} testID="nudge-right"><Ionicons name="chevron-forward" size={22} color={colors.onSurface} /></Pressable>
                </View>
                <View style={styles.padRow}><Pressable style={styles.padBtn} onPress={() => nudgeSel(0, 1)} testID="nudge-down"><Ionicons name="chevron-down" size={22} color={colors.onSurface} /></Pressable></View>
              </View>
              <View style={styles.fontCol}>
                <Pressable style={styles.padBtn} onPress={() => resizeSel(0.5)} testID="font-bigger"><Text style={styles.fontBig}>A+</Text></Pressable>
                <Pressable style={styles.padBtn} onPress={() => resizeSel(-0.5)} testID="font-smaller"><Text style={styles.fontSmall}>A-</Text></Pressable>
                <Pressable style={styles.padBtn} onPress={boldSel} testID="font-bold"><Text style={styles.fontBoldBtn}>B</Text></Pressable>
              </View>
            </View>

            <Text style={styles.flabel}>TEXT LINES (tap ☐ to select, tap text to edit)</Text>
            {tpl.lines.map((ln, i) => (
              <View key={`e${i}`} style={[styles.lineTopRow, selLines.has(i) && styles.lineSelected]}>
                <Pressable style={styles.checkBtn} onPress={() => toggleLineSel(i)} testID={`sel-${i}`}>
                  <Ionicons name={selLines.has(i) ? "checkbox" : "square-outline"} size={22} color={selLines.has(i) ? colors.brand : colors.info} />
                </Pressable>
                <TextInput style={styles.lineInputFlex} value={ln.text} onChangeText={(t) => editLine(i, t)} testID={`line-${i}`} />
              </View>
            ))}

            <Pressable style={styles.saveBtn} onPress={saveTemplate} testID="save-template">
              <Ionicons name="bookmark" size={18} color={colors.brand} /><Text style={styles.saveText}>Save this sticker{FORMATTED_COMPANIES.includes(company) ? ` (${company})` : ""}</Text>
            </Pressable>
            <Pressable style={styles.fmtBtn} onPress={saveCompanyFormat} testID="save-company-format">
              <Ionicons name="albums" size={18} color={colors.onBrand} />
              <Text style={styles.fmtText}>Save as {company} FORMAT{companyFormats[company] ? " (update)" : ""}</Text>
            </Pressable>
            <Text style={styles.dim}>Format = your arrangement (positions, code, logo). Next scan of {company} auto-uses it.</Text>

            <Text style={styles.section}>A4 SHEET LAYOUT</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
              {SHEET_LAYOUTS.map((l) => (
                <FilterChip key={l.code} label={`${l.code} (${l.total})`} active={layoutCode === l.code} onPress={() => { setLayoutCode(l.code); setSelected(new Set()); }} testID={`layout-${l.code}`} />
              ))}
            </ScrollView>
            <Text style={styles.flabel}>TAP ANY BLOCKS TO PRINT ({selected.size} selected)</Text>
            <View style={styles.gridCard}>
              <View style={[styles.grid, { width: cellW * layout.cols + 2 }]}>
                {Array.from({ length: layout.total }).map((_, i) => {
                  const num = i + 1;
                  const on = selected.has(num);
                  return (
                    <Pressable key={i} onPress={() => toggleCell(num)} style={[styles.cell, { width: cellW, height: cellH }, on && styles.cellOn]} testID={`cell-${num}`}>
                      <Text style={[styles.cellText, on && styles.cellTextOn]}>{num}</Text>
                    </Pressable>
                  );
                })}
              </View>
            </View>

            <Text style={styles.flabel}>PAPER MARGIN (mm) — blank = auto</Text>
            <View style={styles.chipWrap}>
              <TextInput style={styles.marginInput} value={marginTop} onChangeText={setMarginTop} placeholder="Top" placeholderTextColor={colors.info} keyboardType="decimal-pad" testID="margin-top" />
              <TextInput style={styles.marginInput} value={marginLeft} onChangeText={setMarginLeft} placeholder="Left" placeholderTextColor={colors.info} keyboardType="decimal-pad" testID="margin-left" />
              <Pressable style={styles.logoAdd} onPress={() => { setMarginTop("0"); setMarginLeft("0"); }} testID="margin-zero">
                <Text style={styles.saveText}>0 / 0</Text>
              </Pressable>
            </View>

            <Text style={styles.flabel}>PAGE MARGIN (mm) — 0 = edge-to-edge</Text>
            <View style={styles.chipWrap}>
              <TextInput style={styles.marginInput} value={pageMargin} onChangeText={setPageMargin} placeholder="0" placeholderTextColor={colors.info} keyboardType="decimal-pad" testID="page-margin" />
              <Pressable style={styles.logoAdd} onPress={() => setPageMargin("0")} testID="page-margin-zero">
                <Text style={styles.saveText}>Set 0</Text>
              </Pressable>
            </View>
            <Text style={styles.hint}>In the print dialog also choose Margins = None &amp; Scale = 100% for exact edge-to-edge.</Text>

            <Pressable style={styles.printBtn} onPress={onPrint} testID="scan-print">
              <Ionicons name="print" size={20} color={colors.onBrand} /><Text style={styles.printText}>Print A4 Sheet</Text>
            </Pressable>
          </>
        ) : !busy ? (
          <View style={styles.empty}>
            <Ionicons name="scan-outline" size={48} color={colors.info} />
            <Text style={styles.dim}>Pick a sticker photo. The app reads it and generates a clean, straight sticker (always upright). Edit the part number / any line, then print.</Text>
          </View>
        ) : null}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  pickRow: { flexDirection: "row", gap: spacing.md },
  pickBtn: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: spacing.sm, backgroundColor: colors.brand, borderRadius: radius.md, paddingVertical: spacing.md },
  pickText: { color: colors.onBrand, fontWeight: "800", fontSize: font.base },
  busy: { alignItems: "center", gap: spacing.md, paddingVertical: spacing.xl },
  empty: { alignItems: "center", gap: spacing.md, paddingVertical: spacing.xxxl },
  dim: { color: colors.info, fontSize: font.sm, textAlign: "center", paddingHorizontal: spacing.lg, lineHeight: 20 },
  section: { color: colors.brand, fontSize: font.sm, fontWeight: "800", letterSpacing: 0.5, marginTop: spacing.sm },
  flabel: { color: colors.info, fontSize: font.sm - 1, fontWeight: "800", letterSpacing: 0.5, marginTop: spacing.xs },
  hint: { color: colors.info, fontSize: font.sm - 1, lineHeight: 16, marginTop: 2 },
  preview: { backgroundColor: "#fff", borderRadius: radius.sm, alignSelf: "center", overflow: "hidden", borderWidth: 1, borderColor: colors.border },
  input: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.md, color: colors.onSurface, fontSize: font.base },
  lineInput: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.sm, color: colors.onSurface, fontSize: font.sm },
  lineTopRow: { flexDirection: "row", alignItems: "center", gap: spacing.xs, marginBottom: spacing.xs },
  lineSelected: { backgroundColor: colors.brandFaint, borderRadius: radius.sm, paddingHorizontal: 2 },
  checkBtn: { width: 36, height: 40, alignItems: "center", justifyContent: "center" },
  lineInputFlex: { flex: 1, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.sm, color: colors.onSurface, fontSize: font.sm },
  subHint: { color: colors.info, fontSize: font.sm - 1, marginTop: spacing.xs },
  arrangeTop: { flexDirection: "row", alignItems: "center", flexWrap: "wrap", gap: spacing.xs, marginTop: 4 },
  miniBtn: { paddingHorizontal: spacing.md, height: 32, borderRadius: radius.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, alignItems: "center", justifyContent: "center" },
  miniBtnText: { color: colors.onSurface, fontWeight: "700", fontSize: font.sm },
  padWrap: { flexDirection: "row", alignItems: "center", gap: spacing.xl, marginTop: spacing.sm, marginBottom: spacing.xs },
  pad: { alignItems: "center", gap: 6 },
  padRow: { flexDirection: "row", alignItems: "center", gap: 6 },
  padBtn: { width: 48, height: 44, borderRadius: radius.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, alignItems: "center", justifyContent: "center" },
  padCenter: { width: 48, height: 44, borderRadius: radius.sm, backgroundColor: colors.brandFaint, alignItems: "center", justifyContent: "center" },
  padCount: { color: colors.brand, fontWeight: "800", fontSize: font.base },
  fontCol: { gap: 6 },
  fontBig: { color: colors.onSurface, fontWeight: "800", fontSize: 20 },
  fontSmall: { color: colors.onSurface, fontWeight: "800", fontSize: 13 },
  fontBoldBtn: { color: colors.onSurface, fontWeight: "900", fontSize: 18 },
  sizeRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginTop: 4, flexWrap: "wrap" },
  sizeBtn: { width: 44, height: 40, borderRadius: radius.sm, backgroundColor: colors.surface2, alignItems: "center", justifyContent: "center", borderWidth: 1, borderColor: colors.border },
  sizeVal: { minWidth: 80, height: 40, borderRadius: radius.sm, backgroundColor: colors.surface, alignItems: "center", justifyContent: "center", borderWidth: 1, borderColor: colors.border },
  sizeValText: { color: colors.onSurface, fontWeight: "800", fontSize: font.base },
  chipWrap: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  chipRow: { gap: spacing.sm, paddingVertical: spacing.xs },
  gridCard: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md, alignItems: "center" },
  grid: { flexDirection: "row", flexWrap: "wrap", alignSelf: "center" },
  cell: { borderWidth: 0.5, borderColor: colors.border, alignItems: "center", justifyContent: "center", backgroundColor: colors.surface },
  cellOn: { backgroundColor: colors.brand },
  cellText: { color: colors.info, fontSize: 9, fontWeight: "700" },
  cellTextOn: { color: colors.onBrand },
  printBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: spacing.sm, backgroundColor: colors.brand, borderRadius: radius.md, paddingVertical: spacing.md, marginTop: spacing.md },
  printText: { color: colors.onBrand, fontSize: font.base, fontWeight: "800", letterSpacing: 0.5 },
  saveBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: spacing.sm, borderWidth: 1, borderColor: colors.brand, borderRadius: radius.md, paddingVertical: spacing.sm, marginTop: spacing.xs },
  saveText: { color: colors.brand, fontSize: font.sm, fontWeight: "800" },
  fmtBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: spacing.sm, backgroundColor: colors.brand, borderRadius: radius.md, paddingVertical: spacing.sm, marginTop: spacing.xs },
  fmtText: { color: colors.onBrand, fontSize: font.sm, fontWeight: "800" },
  savedRow: { gap: spacing.md, paddingVertical: spacing.xs },
  savedCard: { width: 110 },
  savedInner: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, padding: spacing.md, alignItems: "center", gap: 4 },
  savedName: { color: colors.onSurface, fontSize: font.sm - 1, fontWeight: "700" },
  savedDel: { position: "absolute", top: -6, right: -6, backgroundColor: colors.surface, borderRadius: 10 },
  logoAdd: { width: 64, height: 56, borderWidth: 1, borderColor: colors.brand, borderRadius: radius.sm, alignItems: "center", justifyContent: "center", gap: 2 },
  logoCard: { width: 72 },
  logoThumb: { width: 72, height: 56, borderRadius: radius.sm, backgroundColor: "#fff", borderWidth: 1, borderColor: colors.border },
  marginInput: { width: 80, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.sm, color: colors.onSurface, fontSize: font.base },
});
