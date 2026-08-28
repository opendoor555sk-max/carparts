import { useCallback, useEffect, useMemo, useState } from "react";
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
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

type ScanResult = { aspect: number; part_number: string; lines: { text: string; bold?: boolean }[]; code: { type: string } | null };

const PREVIEW_W = 320;

// Clean vertical layout: no overlap, font shrinks to fit width, code in right column.
function layoutLines(raw: { text: string; bold?: boolean }[], aspect: number, hasCode: boolean): TplLine[] {
  const lines = raw.filter((l) => (l.text || "").trim());
  if (!lines.length) return [];
  const frac = hasCode ? 0.66 : 0.96;
  const widthUnits = aspect * 100 * frac;
  const top = 4, bottom = 4;
  const slot = Math.max(4, (100 - top - bottom) / lines.length);
  return lines.map((ln, i) => {
    const len = Math.max(1, (ln.text || "").length);
    const fs = Math.max(2.2, Math.min(slot * 0.8, widthUnits / (len * 0.55), 9));
    return { text: ln.text, x: 3, y: top + i * slot + (slot - fs) / 2, size: fs, bold: ln.bold };
  });
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
  const [saved, setSaved] = useState<any[]>([]);
  const layout = useMemo(() => SHEET_LAYOUTS.find((l) => l.code === layoutCode)!, [layoutCode]);

  const loadSaved = useCallback(async () => {
    try { setSaved(await api.get<any[]>("/sticker-templates")); } catch {}
  }, []);
  useEffect(() => { loadSaved(); }, [loadSaved]);

  const codeBox = (aspect: number): Box => {
    const h = 34, w = Math.min(40, h / aspect);
    return { x: 100 - w - 3, y: (100 - h) / 2, w, h };
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
      buildTpl(res.lines || [], aspect, hasCode, ct, pn);
      setPartNumber(pn);
      setCodeType(ct);
      setSelected(new Set());
      show("Sticker generated", "success");
    } catch (e: any) {
      show(e?.message || "Scan failed", "error");
    } finally {
      setBusy(false);
    }
  };

  const buildTpl = (rawLines: { text: string; bold?: boolean }[], aspect: number, hasCode: boolean, ct: "qr" | "barcode", pn: string) => {
    const lines = layoutLines(rawLines, aspect, hasCode);
    setTpl({ aspect, lines, code: hasCode ? { type: ct, value: pn, box: codeBox(aspect) } : null });
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
      await printHtml(generateRichStickerSheetHtml(tpl, { layout, cells: Array.from(selected), showBorder: false }));
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
              {tpl.code && codeSvg ? (
                <View style={{ position: "absolute", left: (tpl.code.box.x / 100) * PREVIEW_W, top: (tpl.code.box.y / 100) * previewH, width: (tpl.code.box.w / 100) * PREVIEW_W, height: (tpl.code.box.h / 100) * previewH }}>
                  <SvgXml xml={codeSvg} width="100%" height="100%" preserveAspectRatio={codeType === "qr" ? "xMidYMid meet" : "none"} />
                </View>
              ) : null}
              {tpl.lines.map((ln, i) => (
                <Text key={i} numberOfLines={1} style={{ position: "absolute", left: (ln.x / 100) * PREVIEW_W, top: (ln.y / 100) * previewH, fontSize: Math.max(6, (ln.size / 100) * previewH), fontWeight: ln.bold ? "800" : "500", color: "#000" }}>{ln.text}</Text>
              ))}
            </View>

            <Text style={styles.section}>PART NUMBER</Text>
            <TextInput style={styles.input} value={partNumber} onChangeText={applyPn} autoCapitalize="characters" testID="scan-pn" />

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
              <Ionicons name="bookmark" size={18} color={colors.brand} /><Text style={styles.saveText}>Save this sticker for reuse</Text>
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
});
