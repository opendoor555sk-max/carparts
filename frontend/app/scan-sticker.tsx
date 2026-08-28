import { useMemo, useState } from "react";
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
  const [codeType, setCodeType] = useState<"qr" | "barcode">("qr");

  const [layoutCode, setLayoutCode] = useState("24L");
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const layout = useMemo(() => SHEET_LAYOUTS.find((l) => l.code === layoutCode)!, [layoutCode]);

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
      const ct: "qr" | "barcode" = res.code?.type === "barcode" ? "barcode" : "qr";
      const codeBox = res.code ? { x: res.code.x, y: res.code.y, w: res.code.w, h: res.code.h } : null;

      setTpl({
        bgDataUrl,
        aspect,
        pnBox: res.part_number_box || null,
        pnText: pn,
        code: codeBox ? { type: ct, value: pn, box: codeBox } : null,
      });
      setPartNumber(pn);
      setCodeType(ct);
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
    setTpl((prev) => (prev ? { ...prev, pnText: value, code: prev.code ? { ...prev.code, value } : null } : prev));
  };
  const setCode = (t: "qr" | "barcode") => {
    setCodeType(t);
    setTpl((prev) => (prev && prev.code ? { ...prev, code: { ...prev.code, type: t } } : prev));
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
      await printHtml(generateRichStickerSheetHtml(tpl, { layout, cells: Array.from(selected), showBorder: true }));
    } catch (e: any) {
      show(e?.message || "Print failed", "error");
    }
  };

  const previewH = tpl ? PREVIEW_W / (tpl.aspect || 1.4) : 220;
  const codeSvg = useMemo(() => {
    if (!tpl?.code?.value) return "";
    return codeType === "qr" ? qrSvg(tpl.code.value, { margin: 1 }) : barcodeSvg(tpl.code.value, { height: 60, moduleWidth: 2, showText: false });
  }, [tpl?.code?.value, codeType]);

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
              {tpl.code && codeSvg ? (
                <View style={{ position: "absolute", left: (tpl.code.box.x / 100) * PREVIEW_W, top: (tpl.code.box.y / 100) * previewH, width: (tpl.code.box.w / 100) * PREVIEW_W, height: (tpl.code.box.h / 100) * previewH, backgroundColor: "#fff", padding: 1 }}>
                  <SvgXml xml={codeSvg} width="100%" height="100%" preserveAspectRatio={codeType === "qr" ? "xMidYMid meet" : "none"} />
                </View>
              ) : null}
            </View>

            <Text style={styles.section}>PART NUMBER (only this changes)</Text>
            <TextInput style={styles.input} value={partNumber} onChangeText={applyPn} autoCapitalize="characters" testID="scan-pn" />

            <Text style={styles.flabel}>CODE TYPE</Text>
            <View style={styles.chipWrap}>
              <FilterChip label="QR Code" active={codeType === "qr"} onPress={() => setCode("qr")} testID="ct-qr" />
              <FilterChip label="Barcode" active={codeType === "barcode"} onPress={() => setCode("barcode")} testID="ct-barcode" />
            </View>

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
});
