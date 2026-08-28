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
import { SHEET_LAYOUTS, StickerTemplate, TplLine, generateRichStickerSheetHtml } from "@/src/utils/labelSheet";
import { colors, font, radius, spacing } from "@/src/theme";

type ScanResult = {
  aspect: number;
  part_number: string;
  lines: TplLine[];
  logos: { label?: string; x: number; y: number; w: number; h: number }[];
  codes: { type: string; value: string; x: number; y: number; w: number; h: number }[];
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
      const res = await api.post<ScanResult>("/scan-sticker", { image_base64: asset.base64 });
      const imgW = asset.width || 1000;
      const imgH = asset.height || 1000;

      // crop logos from the original image using detected bounding boxes
      const logos: StickerTemplate["logos"] = [];
      for (const lg of (res.logos || []).slice(0, 6)) {
        try {
          const ox = Math.max(0, Math.round((lg.x / 100) * imgW));
          const oy = Math.max(0, Math.round((lg.y / 100) * imgH));
          const cw = Math.min(imgW - ox, Math.round((lg.w / 100) * imgW));
          const ch = Math.min(imgH - oy, Math.round((lg.h / 100) * imgH));
          if (cw < 4 || ch < 4) continue;
          const ctx = ImageManipulator.manipulate(asset.uri);
          ctx.crop({ originX: ox, originY: oy, width: cw, height: ch });
          const rendered = await ctx.renderAsync();
          const out = await rendered.saveAsync({ format: SaveFormat.PNG, base64: true });
          logos.push({ dataUrl: `data:image/png;base64,${out.base64}`, x: lg.x, y: lg.y, w: lg.w, h: lg.h });
        } catch {
          // skip a logo that fails to crop
        }
      }

      const first = (res.codes || [])[0];
      const ct: "qr" | "barcode" = first?.type === "barcode" ? "barcode" : "qr";
      const pn = res.part_number || "";
      const code: StickerTemplate["code"] = first
        ? { type: ct, value: pn, x: first.x, y: first.y, w: first.w, h: first.h }
        : { type: "qr", value: pn, x: 68, y: 55, w: 26, h: 30 };

      setTpl({ aspect: res.aspect || 1.4, lines: res.lines || [], logos, code });
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
    if (!perm.granted) {
      show("Gallery permission needed", "error");
      return;
    }
    const r = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ["images"], base64: true, quality: 0.8 });
    if (!r.canceled && r.assets?.[0]?.base64) processImage(r.assets[0]);
  };

  const takePhoto = async () => {
    const perm = await ImagePicker.requestCameraPermissionsAsync();
    if (!perm.granted) {
      show("Camera permission needed", "error");
      return;
    }
    const r = await ImagePicker.launchCameraAsync({ base64: true, quality: 0.8 });
    if (!r.canceled && r.assets?.[0]?.base64) processImage(r.assets[0]);
  };

  // keep template code + part-number line text in sync with edits
  const applyPn = (value: string) => {
    setPartNumber(value);
    setTpl((prev) => (prev && prev.code ? { ...prev, code: { ...prev.code, value } } : prev));
  };
  const setCode = (t: "qr" | "barcode") => {
    setCodeType(t);
    setTpl((prev) => (prev && prev.code ? { ...prev, code: { ...prev.code, type: t } } : prev));
  };
  const editLine = (idx: number, text: string) => {
    setTpl((prev) => {
      if (!prev) return prev;
      const lines = prev.lines.slice();
      lines[idx] = { ...lines[idx], text };
      return { ...prev, lines };
    });
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
    if (selected.size === 0) {
      show("Tap blocks to print on", "error");
      return;
    }
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
      <Header title="AI Sticker Scanner" subtitle="Capture any sticker → edit → print" onBack={() => router.back()} />
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
            <Text style={styles.dim}>Reading sticker with AI… (10–30s)</Text>
          </View>
        ) : null}

        {tpl ? (
          <>
            <Text style={styles.section}>PREVIEW (rebuilt)</Text>
            <View style={[styles.preview, { width: PREVIEW_W, height: previewH }]}>
              {tpl.logos.map((lg, i) => (
                <Image
                  key={`lg${i}`}
                  source={{ uri: lg.dataUrl }}
                  style={{ position: "absolute", left: (lg.x / 100) * PREVIEW_W, top: (lg.y / 100) * previewH, width: (lg.w / 100) * PREVIEW_W, height: (lg.h / 100) * previewH }}
                  resizeMode="contain"
                />
              ))}
              {tpl.code && codeSvg ? (
                <View style={{ position: "absolute", left: (tpl.code.x / 100) * PREVIEW_W, top: (tpl.code.y / 100) * previewH, width: (tpl.code.w / 100) * PREVIEW_W, height: (tpl.code.h / 100) * previewH }}>
                  <SvgXml xml={codeSvg} width="100%" height="100%" preserveAspectRatio={codeType === "qr" ? "xMidYMid meet" : "none"} />
                </View>
              ) : null}
              {tpl.lines.map((ln, i) => (
                <Text
                  key={`ln${i}`}
                  numberOfLines={1}
                  style={{ position: "absolute", left: (ln.x / 100) * PREVIEW_W, top: (ln.y / 100) * previewH, fontSize: Math.max(6, (ln.size / 100) * previewH), fontWeight: ln.bold ? "800" : "500", color: "#000" }}
                >
                  {ln.text}
                </Text>
              ))}
            </View>

            <Text style={styles.section}>PART NUMBER (encoded in the code)</Text>
            <TextInput style={styles.input} value={partNumber} onChangeText={applyPn} autoCapitalize="characters" testID="scan-pn" />

            <Text style={styles.flabel}>CODE TYPE</Text>
            <View style={styles.chipWrap}>
              <FilterChip label="QR Code" active={codeType === "qr"} onPress={() => setCode("qr")} testID="ct-qr" />
              <FilterChip label="Barcode" active={codeType === "barcode"} onPress={() => setCode("barcode")} testID="ct-barcode" />
            </View>

            <Text style={styles.flabel}>TEXT LINES (tap to edit)</Text>
            {tpl.lines.map((ln, i) => (
              <TextInput key={`edit${i}`} style={styles.lineInput} value={ln.text} onChangeText={(t) => editLine(i, t)} testID={`line-${i}`} />
            ))}

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
            <Text style={styles.dim}>Pick a sticker photo from Gallery or take a new photo. AI will capture the text, logo and code so you can reprint it with a new part number.</Text>
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
});
