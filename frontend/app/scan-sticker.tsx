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
import { qrSvg } from "@/src/utils/qr";
import { barcodeSvg } from "@/src/utils/barcode128";
import { Box, SHEET_LAYOUTS, StickerTemplate, TplLine, generateRichStickerSheetHtml } from "@/src/utils/labelSheet";
import { colors, font, radius, spacing } from "@/src/theme";

type ScanResult = { aspect: number; part_number: string; lines: { text: string; bold?: boolean }[]; code: { type: string } | null; logo: Box | null };

const PREVIEW_W = 320;

const COMPANIES = ["Hyundai", "Kia", "Maruti Suzuki", "Tata", "Mahindra", "Toyota", "Honda", "Nissan", "Renault", "Ford", "Volkswagen", "Skoda", "MG", "Datsun", "Chevrolet", "Fiat", "Jeep", "Citroen", "Isuzu", "Other"];

// Companies that have a dedicated 2-column layout preset. Others fall back to generic.
const FORMATTED_COMPANIES = ["Hyundai", "Kia"];

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

// Hyundai / Kia real-sticker 2-column layout (matches OEM label, per user's zone spec):
//  - Right-top: UNIT ASSY / HKMC P/N / SYEC P/N / LOT N/O / (value) / H/W Ver / S/W Ver
//  - Left-top (under logo): HYUNDAI KIA MOTORS
//  - Left-mid: MODEL / TA / IFT ID   then below it the leftover code (e.g. CRCH-23369)
//  - Right-mid: QR (placed via code box)
//  - Bottom-center: VBHH ; Bottom full-width: SEOYON ... MADE IN INDIA
function layoutHyundaiKia(raw: { text: string; bold?: boolean }[], aspect: number): TplLine[] {
  const lines = raw.map((l) => ({ text: (l.text || "").trim(), bold: l.bold })).filter((l) => l.text);

  const isBottom = (u: string) => /(MADE IN|ELECTRONIC|SEOYON|PVT|LTD|\/\/)/.test(u);
  const isBrand = (u: string) => /MOTORS/.test(u) || /^HYUNDAI\s*KIA/.test(u);
  const isRight = (u: string) => /(UNIT ASSY|ASSY|P\/N|LOT|H\/W|S\/W|VER|HKMC|SYEC)/.test(u);
  const isLeft = (u: string) => /(MODEL|IFT|^TA[ -])/.test(u);
  const isCenter = (t: string) => t.length <= 6 && /^[A-Za-z]+$/.test(t); // e.g. VBHH

  const brand: typeof lines = [], rightTop: typeof lines = [], leftMid: typeof lines = [], bottom: typeof lines = [];
  let center: { text: string; bold?: boolean } | null = null;
  let last: "right" | "left" | "bottom" | "brand" | "center" = "left";
  for (const ln of lines) {
    const u = ln.text.toUpperCase();
    if (isBottom(u)) { bottom.push(ln); last = "bottom"; }
    else if (isBrand(u)) { brand.push(ln); last = "brand"; }
    else if (isCenter(ln.text)) { if (!center) center = ln; last = "center"; }
    else if (isLeft(u)) { leftMid.push(ln); last = "left"; }
    else if (isRight(u)) { rightTop.push(ln); last = "right"; }
    else {
      // unmatched: it's a continuation → inherit the previous line's zone.
      if (last === "right") rightTop.push(ln);
      else if (last === "bottom") bottom.push(ln);
      else { leftMid.push(ln); last = "left"; }
    }
  }

  const fit = (wPct: number, len: number, base: number) =>
    Math.max(2.0, Math.min(base, (wPct * aspect) / (Math.max(1, len) * 0.5)));
  const out: TplLine[] = [];
  // Left-top brand line, just under the logo.
  brand.forEach((ln, i) => out.push({ text: ln.text, x: 2, y: 19 + i * 6.5, size: fit(40, ln.text.length, 6), bold: true }));
  // Right-top P/N block.
  const rtStep = rightTop.length ? Math.min(5.5, (44 - 3) / rightTop.length) : 0;
  rightTop.forEach((ln, i) => out.push({ text: ln.text, x: 44, y: 3 + i * rtStep, size: fit(54, ln.text.length, 5.4), bold: ln.bold }));
  // Left-mid block (MODEL/TA/IFT) + leftover code stacked below.
  leftMid.forEach((ln, i) => out.push({ text: ln.text, x: 2, y: 44 + i * 6.5, size: fit(54, ln.text.length, 6), bold: ln.bold }));
  // Bottom center small line.
  if (center) out.push({ text: center.text, x: 42, y: 82, size: fit(28, center.text.length, 5.5), bold: false });
  // Bottom full-width line(s).
  bottom.forEach((ln, i) => out.push({ text: ln.text, x: 2, y: 90 + i * 5, size: fit(96, ln.text.length, 4.6), bold: false }));
  return out;
}

function buildLines(raw: { text: string; bold?: boolean }[], aspect: number, hasCode: boolean, hasLogo: boolean, company?: string): TplLine[] {
  if (company && FORMATTED_COMPANIES.includes(company)) return layoutHyundaiKia(raw, aspect);
  return layoutLines(raw, aspect, hasCode, hasLogo ? 20 : 4);
}

export default function ScanSticker() {
  const router = useRouter();
  const { show } = useToast();

  const [busy, setBusy] = useState(false);
  const [tpl, setTpl] = useState<StickerTemplate | null>(null);
  const [partNumber, setPartNumber] = useState("");
  const [codeType, setCodeType] = useState<"qr" | "barcode">("qr");

  const [layoutCode, setLayoutCode] = useState("24L");
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [marginTop, setMarginTop] = useState("");
  const [marginLeft, setMarginLeft] = useState("");
  const [saved, setSaved] = useState<any[]>([]);
  const [logos, setLogos] = useState<any[]>([]);
  const [company, setCompany] = useState("Hyundai");
  const [rawLines, setRawLines] = useState<{ text: string; bold?: boolean }[]>([]);
  const layout = useMemo(() => SHEET_LAYOUTS.find((l) => l.code === layoutCode)!, [layoutCode]);

  const loadSaved = useCallback(async () => {
    try { setSaved(await api.get<any[]>("/sticker-templates")); } catch {}
    try { setLogos(await api.get<any[]>("/logos")); } catch {}
  }, []);
  useEffect(() => { loadSaved(); }, [loadSaved]);

  const LOGO_BOX: Box = { x: 3, y: 2, w: 34, h: 15 };
  const applyLogo = (dataUrl: string | null) =>
    setTpl((prev) => {
      if (!prev) return prev;
      const logo = dataUrl ? { dataUrl, box: LOGO_BOX } : null;
      const lines = buildLines(rawLines, prev.aspect, !!prev.code, !!logo, prev.company);
      return { ...prev, logo, lines };
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
      const ct: "qr" | "barcode" = res.code?.type === "barcode" ? "barcode" : "qr";
      const hasCode = !!res.code;
      const pn = res.part_number || "";
      const rl = res.lines || [];
      // Auto-detect company from the sticker text.
      const joined = rl.map((l) => (l.text || "").toUpperCase()).join(" ");
      let comp = company;
      if (/HYUNDAI/.test(joined)) comp = "Hyundai";
      else if (/\bKIA\b/.test(joined)) comp = "Kia";
      setRawLines(rl);
      setCompany(comp);
      buildTpl(rl, aspect, hasCode, ct, pn, null, comp);
      setPartNumber(pn);
      setCodeType(ct);
      setSelected(new Set());
      show(`Sticker generated${FORMATTED_COMPANIES.includes(comp) ? ` (${comp} format)` : ""}`, "success");
    } catch (e: any) {
      show(e?.message || "Scan failed", "error");
    } finally {
      setBusy(false);
    }
  };

  const buildTpl = (rl: { text: string; bold?: boolean }[], aspect: number, hasCode: boolean, ct: "qr" | "barcode", pn: string, logo: StickerTemplate["logo"], comp: string) => {
    const lines = buildLines(rl, aspect, hasCode, !!logo, comp);
    setTpl({ aspect, lines, code: hasCode ? { type: ct, value: pn, box: codeBox(aspect, comp) } : null, logo, company: comp });
  };

  const applyCompany = (comp: string) => {
    setCompany(comp);
    setTpl((prev) => {
      if (!prev) return prev;
      const lines = buildLines(rawLines, prev.aspect, !!prev.code, !!prev.logo, comp);
      const code = prev.code ? { ...prev.code, box: codeBox(prev.aspect, comp) } : null;
      return { ...prev, company: comp, lines, code };
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
  const setCode = (t: "qr" | "barcode") => {
    setCodeType(t);
    setTpl((prev) => (prev && prev.code ? { ...prev, code: { ...prev.code, type: t } } : prev));
  };
  const editLine = (idx: number, text: string) =>
    setTpl((prev) => {
      if (!prev) return prev;
      const lines = prev.lines.slice();
      lines[idx] = { ...lines[idx], text };
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
  const openTemplate = (t: any) => {
    try {
      const parsed: StickerTemplate = JSON.parse(t.bg_data_url);
      setTpl(parsed);
      setPartNumber(t.part_number || parsed.code?.value || "");
      setCodeType(parsed.code?.type || "qr");
      setCompany(parsed.company || "Hyundai");
      setRawLines((parsed.lines || []).map((l) => ({ text: l.text, bold: l.bold })));
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
      await printHtml(generateRichStickerSheetHtml(tpl, { layout, cells: Array.from(selected), showBorder: false, marginTop: mt, marginLeft: ml }));
    } catch (e: any) { show(e?.message || "Print failed", "error"); }
  };

  const previewH = tpl ? PREVIEW_W / (tpl.aspect || 1.6) : 220;
  const codeSvg = useMemo(() => {
    if (!tpl?.code?.value) return "";
    return codeType === "qr" ? qrSvg(tpl.code.value, { margin: 1 }) : barcodeSvg(tpl.code.value, { height: 60, moduleWidth: 2, showText: false });
  }, [tpl?.code?.value, codeType]);

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
                codeType === "qr" ? (
                  (() => {
                    const qpx = Math.min(previewH - 4, (10 / layout.h) * previewH);
                    const left = PREVIEW_W - qpx - (2 / layout.w) * PREVIEW_W;
                    const top = Math.max(0, Math.min((tpl.code.box.y / 100) * previewH, previewH - qpx));
                    return (
                      <View style={{ position: "absolute", left, top, width: qpx, height: qpx }}>
                        <SvgXml xml={codeSvg} width="100%" height="100%" preserveAspectRatio="xMidYMid meet" />
                      </View>
                    );
                  })()
                ) : (
                  <View style={{ position: "absolute", left: (tpl.code.box.x / 100) * PREVIEW_W, top: (tpl.code.box.y / 100) * previewH, width: (tpl.code.box.w / 100) * PREVIEW_W, height: (tpl.code.box.h / 100) * previewH }}>
                    <SvgXml xml={codeSvg} width="100%" height="100%" preserveAspectRatio="none" />
                  </View>
                )
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
                ? `★ ${company} format: 2-column OEM layout (logo + P/N block, QR right).`
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

            <Text style={styles.flabel}>CODE TYPE</Text>
            <View style={styles.chipWrap}>
              <FilterChip label="QR Code" active={codeType === "qr"} onPress={() => setCode("qr")} testID="ct-qr" />
              <FilterChip label="Barcode" active={codeType === "barcode"} onPress={() => setCode("barcode")} testID="ct-barcode" />
            </View>

            <Text style={styles.flabel}>TEXT LINES (tap to edit)</Text>
            {tpl.lines.map((ln, i) => (
              <TextInput key={`e${i}`} style={styles.lineInput} value={ln.text} onChangeText={(t) => editLine(i, t)} testID={`line-${i}`} />
            ))}

            <Pressable style={styles.saveBtn} onPress={saveTemplate} testID="save-template">
              <Ionicons name="bookmark" size={18} color={colors.brand} /><Text style={styles.saveText}>Save this format{FORMATTED_COMPANIES.includes(company) ? ` (${company})` : ""}</Text>
            </Pressable>

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
