import { useCallback, useEffect, useMemo, useState } from "react";
import { ActivityIndicator, Image, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import * as ImagePicker from "expo-image-picker";
import { ImageManipulator, SaveFormat } from "expo-image-manipulator";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { FilterChip, Header } from "@/src/components/ui";
import { printHtml } from "@/src/utils/print";
import { Box, SHEET_LAYOUTS, StickerTemplate, generateRichStickerSheetHtml } from "@/src/utils/labelSheet";
import { colors, font, radius, spacing } from "@/src/theme";

type ScanResult = {
  rotation: number;
  sticker: Box;
  part_number: string;
  part_number_box: Box | null;
  code: { type: string } & Box | null;
};

const PREVIEW_W = 320;

export default function ScanSticker() {
  const router = useRouter();
  const { show } = useToast();

  const [busy, setBusy] = useState(false);
  const [tpl, setTpl] = useState<StickerTemplate | null>(null);
  const [partNumber, setPartNumber] = useState("");

  const [layoutCode, setLayoutCode] = useState("24L");
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [saved, setSaved] = useState<any[]>([]);
  const layout = useMemo(() => SHEET_LAYOUTS.find((l) => l.code === layoutCode)!, [layoutCode]);

  const loadSaved = useCallback(async () => {
    try {
      setSaved(await api.get<any[]>("/sticker-templates"));
    } catch {}
  }, []);
  useEffect(() => {
    loadSaved();
  }, [loadSaved]);

  const saveTemplate = async () => {
    if (!tpl) return;
    try {
      await api.post("/sticker-templates", {
        name: (partNumber || "Sticker").trim(),
        bg_data_url: tpl.bgDataUrl,
        aspect: tpl.aspect,
        pn_box: tpl.pnBox,
        part_number: partNumber,
      });
      show("Template saved", "success");
      loadSaved();
    } catch (e: any) {
      show(e?.message || "Save failed", "error");
    }
  };

  const openTemplate = (t: any) => {
    setTpl({ bgDataUrl: t.bg_data_url, aspect: t.aspect || 1.4, pnBox: t.pn_box || null, pnText: t.part_number || "", code: null });
    setPartNumber(t.part_number || "");
    setSelected(new Set());
  };

  const deleteTemplate = async (id: string) => {
    try {
      await api.del(`/sticker-templates/${id}`);
      loadSaved();
    } catch {}
  };

  const processImage = async (asset: ImagePicker.ImagePickerAsset) => {
    setBusy(true);
    try {
      // small copy for fast AI
      let sendB64 = asset.base64!;
      try {
        const rs = ImageManipulator.manipulate(asset.uri);
        rs.resize({ width: 800 });
        const rimg = await rs.renderAsync();
        const rout = await rimg.saveAsync({ format: SaveFormat.JPEG, base64: true });
        if (rout.base64) sendB64 = rout.base64;
      } catch {}

      const res = await api.post<ScanResult>("/scan-sticker", { image_base64: sendB64 });

      // crop the real label from the original photo, then rotate upright
      const rot = [90, 180, 270].includes(res.rotation) ? res.rotation : 0;
      const s = res.sticker || { x: 0, y: 0, w: 100, h: 100 };
      const ow = asset.width || 1000;
      const oh = asset.height || 1000;
      const ox = Math.max(0, Math.round((s.x / 100) * ow));
      const oy = Math.max(0, Math.round((s.y / 100) * oh));
      const cw = Math.max(8, Math.min(ow - ox, Math.round((s.w / 100) * ow)));
      const ch = Math.max(8, Math.min(oh - oy, Math.round((s.h / 100) * oh)));

      const ctx = ImageManipulator.manipulate(asset.uri);
      ctx.crop({ originX: ox, originY: oy, width: cw, height: ch });
      if (rot) ctx.rotate(rot);
      const rendered = await ctx.renderAsync();
      const outImg = await rendered.saveAsync({ format: SaveFormat.JPEG, base64: true });
      const bgDataUrl = `data:image/jpeg;base64,${outImg.base64}`;
      const finalW = outImg.width || cw;
      const finalH = outImg.height || ch;
      const aspect = finalW / Math.max(1, finalH);

      const pn = res.part_number || "";

      setTpl({
        bgDataUrl,
        aspect,
        pnBox: res.part_number_box || null,
        pnText: pn,
        code: null, // keep the ORIGINAL code from the photo as-is (do NOT regenerate)
      });
      setPartNumber(pn);
      setSelected(new Set());
      show("Sticker captured", "success");
    } catch (e: any) {
      show(e?.message || "Scan failed", "error");
    } finally {
      setBusy(false);
    }
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
    setTpl((prev) => (prev ? { ...prev, pnText: value } : prev));
  };

  const toggleCell = (num: number) =>
    setSelected((p) => {
      const n = new Set(p);
      if (n.has(num)) n.delete(num);
      else n.add(num);
      return n;
    });

  const onPrint = async () => {
    if (!tpl) return;
    if (selected.size === 0) return show("Tap blocks to print on", "error");
    try {
      await printHtml(generateRichStickerSheetHtml(tpl, { layout, cells: Array.from(selected), showBorder: false }));
    } catch (e: any) {
      show(e?.message || "Print failed", "error");
    }
  };

  const previewH = tpl ? PREVIEW_W / (tpl.aspect || 1.4) : 220;

  const cellW = Math.min(320 / layout.cols, 60);
  const cellH = Math.max(10, cellW / (layout.w / layout.h));

  return (
    <View style={styles.flex}>
      <Header title="AI Sticker Scanner" subtitle="Copy any sticker → change part no → print" onBack={() => router.back()} />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
        <View style={styles.pickRow}>
          <Pressable style={styles.pickBtn} onPress={pickGallery} disabled={busy} testID="pick-gallery">
            <Ionicons name="images" size={20} color={colors.onBrand} />
            <Text style={styles.pickText}>Gallery</Text>
          </Pressable>
          <Pressable style={styles.pickBtn} onPress={takePhoto} disabled={busy} testID="take-photo">
            <Ionicons name="camera" size={20} color={colors.onBrand} />
            <Text style={styles.pickText}>Camera</Text>
          </Pressable>
        </View>

        {saved.length ? (
          <>
            <Text style={styles.section}>SAVED STICKERS (tap to reuse)</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.savedRow}>
              {saved.map((t) => (
                <View key={t.id} style={styles.savedCard}>
                  <Pressable onPress={() => openTemplate(t)} testID={`saved-${t.id}`}>
                    <Image source={{ uri: t.bg_data_url }} style={styles.savedThumb} resizeMode="cover" />
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
          <View style={styles.busy}>
            <ActivityIndicator size="large" color={colors.brand} />
            <Text style={styles.dim}>Reading sticker… (a few seconds)</Text>
          </View>
        ) : null}

        {tpl ? (
          <>
            <Text style={styles.section}>PREVIEW (real sticker, only part no changes)</Text>
            <View style={[styles.preview, { width: PREVIEW_W, height: previewH }]}>
              <Image source={{ uri: tpl.bgDataUrl }} style={{ position: "absolute", left: 0, top: 0, width: PREVIEW_W, height: previewH }} resizeMode="stretch" />
              {tpl.pnBox ? (
                <View style={{ position: "absolute", left: (tpl.pnBox.x / 100) * PREVIEW_W, top: (tpl.pnBox.y / 100) * previewH, width: (tpl.pnBox.w / 100) * PREVIEW_W, height: (tpl.pnBox.h / 100) * previewH, backgroundColor: "#fff", justifyContent: "center", overflow: "hidden" }}>
                  <Text numberOfLines={1} style={{ fontSize: Math.max(7, (tpl.pnBox.h / 100) * previewH * 0.72), fontWeight: "800", color: "#000" }}>{partNumber}</Text>
                </View>
              ) : null}
            </View>

            <Text style={styles.section}>PART NUMBER (only this changes)</Text>
            <TextInput style={styles.input} value={partNumber} onChangeText={applyPn} autoCapitalize="characters" testID="scan-pn" />
            <Text style={styles.dim}>The original code stays exactly as in the photo — only the part number is replaced.</Text>
            <Pressable style={styles.saveBtn} onPress={saveTemplate} testID="save-template">
              <Ionicons name="bookmark" size={18} color={colors.brand} />
              <Text style={styles.saveText}>Save this sticker for reuse</Text>
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
              <Ionicons name="print" size={20} color={colors.onBrand} />
              <Text style={styles.printText}>Print A4 Sheet</Text>
            </Pressable>
          </>
        ) : !busy ? (
          <View style={styles.empty}>
            <Ionicons name="scan-outline" size={48} color={colors.info} />
            <Text style={styles.dim}>Pick a clear, straight sticker photo. The app keeps the original design/logo exactly and lets you change only the part number, then print.</Text>
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
  savedCard: { width: 96 },
  savedThumb: { width: 96, height: 60, borderRadius: radius.sm, backgroundColor: "#fff", borderWidth: 1, borderColor: colors.border },
  savedName: { color: colors.onSurface3, fontSize: font.sm - 1, marginTop: 2 },
  savedDel: { position: "absolute", top: -6, right: -6, backgroundColor: colors.surface, borderRadius: 10 },
});
