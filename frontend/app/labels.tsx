import { useMemo, useRef, useState } from "react";
import { Linking, Modal, Platform, Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { SvgXml } from "react-native-svg";
import { CameraView, useCameraPermissions } from "expo-camera";

import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { FilterChip, Header } from "@/src/components/ui";
import { printHtml } from "@/src/utils/print";
import { qrSvg } from "@/src/utils/qr";
import { barcodeSvg } from "@/src/utils/barcode128";
import { CodeType, SHEET_LAYOUTS, generateSheetHtml } from "@/src/utils/labelSheet";
import { colors, font, radius, spacing } from "@/src/theme";

const CODE_OPTS: { key: CodeType; label: string }[] = [
  { key: "barcode", label: "Barcode" },
  { key: "qr", label: "QR Code" },
  { key: "both", label: "Both" },
  { key: "none", label: "Text only" },
];

export default function Labels() {
  const params = useLocalSearchParams<{ pn?: string; company?: string; name?: string }>();
  const router = useRouter();
  const { user } = useAuth();
  const { show } = useToast();

  const [partNumber, setPartNumber] = useState((params.pn as string) || "");
  const [line1, setLine1] = useState((params.company as string) || user?.store_name || "");
  const [line2, setLine2] = useState((params.name as string) || "");
  const [code, setCode] = useState<CodeType>("both");
  const [layoutCode, setLayoutCode] = useState("24L");
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [showBorder, setShowBorder] = useState(true);
  const [scannerOpen, setScannerOpen] = useState(false);
  const [permission, requestPermission] = useCameraPermissions();
  const scannedRef = useRef(false);

  const openScanner = async () => {
    let perm = permission;
    if (!perm?.granted) {
      perm = await requestPermission();
    }
    if (perm?.granted) {
      scannedRef.current = false;
      setScannerOpen(true);
    } else if (perm && !perm.canAskAgain) {
      show("Camera blocked — enable it in Settings", "error");
      Linking.openSettings();
    } else {
      show("Camera permission needed to scan", "error");
    }
  };

  const onScanned = ({ data }: { data: string }) => {
    if (scannedRef.current || !data) return;
    scannedRef.current = true;
    setPartNumber(data.trim());
    setScannerOpen(false);
    show(`Scanned: ${data.trim()}`, "success");
  };

  const layout = useMemo(() => SHEET_LAYOUTS.find((l) => l.code === layoutCode)!, [layoutCode]);

  const toggleCell = (num: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(num)) next.delete(num);
      else next.add(num);
      return next;
    });
  };
  const selectAll = () => setSelected(new Set(Array.from({ length: layout.total }, (_, i) => i + 1)));
  const clearAll = () => setSelected(new Set());
  const selectedCount = selected.size;

  const previewSvg = useMemo(() => {
    const pn = partNumber.trim() || "PART-0000";
    if (code === "qr") return qrSvg(pn, { margin: 1 });
    if (code === "barcode" || code === "both") return barcodeSvg(pn, { height: 60, moduleWidth: 2, showText: code === "barcode" });
    return "";
  }, [partNumber, code]);

  const onPrint = async () => {
    if (!partNumber.trim() && code !== "none") {
      show("Enter a Part Number", "error");
      return;
    }
    if (selectedCount === 0) {
      show("Tap the blocks you want to print on", "error");
      return;
    }
    try {
      const html = generateSheetHtml(
        { partNumber: partNumber.trim(), line1: line1.trim(), line2: line2.trim(), code },
        { layout, cells: Array.from(selected), showBorder },
      );
      await printHtml(html);
    } catch (e: any) {
      show(e?.message || "Print failed", "error");
    }
  };

  // Grid preview sizing
  const gridMaxW = 340;
  const aspect = layout.w / layout.h;
  const cellW = Math.min(gridMaxW / layout.cols, 60);
  const cellH = Math.max(10, cellW / aspect);

  return (
    <View style={styles.flex}>
      <Header title="Sticker Sheet Print" subtitle="A4 label sheets — anti-wastage" onBack={() => router.back()} />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
        {/* Content */}
        <Text style={styles.section}>LABEL CONTENT</Text>
        <View style={styles.pnRow}>
          <TextInput style={[styles.input, { flex: 1 }]} value={partNumber} onChangeText={setPartNumber} placeholder="Part Number" placeholderTextColor={colors.info} autoCapitalize="characters" testID="lbl-pn" />
          <Pressable style={styles.scanBtn} onPress={openScanner} testID="lbl-scan">
            <Ionicons name="barcode-outline" size={20} color={colors.onBrand} />
            <Text style={styles.scanBtnText}>Scan</Text>
          </Pressable>
        </View>
        <TextInput style={styles.input} value={line1} onChangeText={setLine1} placeholder="Line 1 (company / store)" placeholderTextColor={colors.info} testID="lbl-l1" />
        <TextInput style={styles.input} value={line2} onChangeText={setLine2} placeholder="Line 2 (name / note) — optional" placeholderTextColor={colors.info} testID="lbl-l2" />

        <Text style={styles.flabel}>CODE</Text>
        <View style={styles.chipWrap}>
          {CODE_OPTS.map((c) => (
            <FilterChip key={c.key} label={c.label} active={code === c.key} onPress={() => setCode(c.key)} testID={`code-${c.key}`} />
          ))}
        </View>

        {/* Single-label preview */}
        {previewSvg ? (
          <View style={styles.preview}>
            <SvgXml xml={previewSvg} width={code === "qr" ? 90 : 200} height={90} />
          </View>
        ) : null}

        {/* Sheet layout */}
        <Text style={styles.section}>A4 SHEET LAYOUT</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
          {SHEET_LAYOUTS.map((l) => (
            <FilterChip
              key={l.code}
              label={`${l.code} (${l.total})`}
              active={layoutCode === l.code}
              onPress={() => {
                setLayoutCode(l.code);
                setSelected(new Set());
              }}
              testID={`layout-${l.code}`}
            />
          ))}
        </ScrollView>
        <Text style={styles.dim}>
          {layout.total} labels • {layout.w} × {layout.h} mm • {layout.rows} rows × {layout.cols} cols
        </Text>

        {/* Interactive grid */}
        <View style={styles.gridHead}>
          <Text style={styles.flabel}>TAP ANY BLOCKS TO PRINT ({selectedCount} selected)</Text>
          <View style={styles.gridActions}>
            <Pressable style={styles.miniBtn} onPress={selectAll} testID="lbl-selectall">
              <Text style={styles.miniText}>All</Text>
            </Pressable>
            <Pressable style={styles.miniBtn} onPress={clearAll} testID="lbl-clear">
              <Text style={styles.miniText}>Clear</Text>
            </Pressable>
          </View>
        </View>
        <View style={styles.gridCard}>
          <View style={[styles.grid, { width: cellW * layout.cols + 2 }]}>
            {Array.from({ length: layout.total }).map((_, i) => {
              const num = i + 1;
              const filled = selected.has(num);
              return (
                <Pressable
                  key={i}
                  onPress={() => toggleCell(num)}
                  style={[styles.cell, { width: cellW, height: cellH }, filled && styles.cellFilled]}
                  testID={`cell-${num}`}
                >
                  <Text style={[styles.cellText, filled && styles.cellTextOn]}>{num}</Text>
                </Pressable>
              );
            })}
          </View>
          <View style={styles.legend}>
            <View style={styles.legRow}><View style={[styles.dot, styles.cell]} /><Text style={styles.legText}>Empty (skip)</Text></View>
            <View style={styles.legRow}><View style={[styles.dot, styles.cellFilled]} /><Text style={styles.legText}>Will print</Text></View>
          </View>
        </View>

        <View style={styles.borderRow}>
          <Text style={styles.borderLabel}>Show cut guide lines</Text>
          <Switch value={showBorder} onValueChange={setShowBorder} trackColor={{ true: colors.brand }} testID="lbl-border" />
        </View>

        <Text style={styles.dim}>Printing {selectedCount} label{selectedCount === 1 ? "" : "s"} on selected blocks</Text>

        <Pressable style={styles.printBtn} onPress={onPrint} testID="lbl-print">
          <Ionicons name="print" size={20} color={colors.onBrand} />
          <Text style={styles.printText}>Print A4 Sheet</Text>
        </Pressable>
        {Platform.OS === "web" ? <Text style={styles.dim}>Tip: on web this opens the browser print dialog — choose Save as PDF.</Text> : null}
      </ScrollView>

      <Modal visible={scannerOpen} animationType="slide" onRequestClose={() => setScannerOpen(false)}>
        <View style={styles.scanModal}>
          <CameraView
            style={StyleSheet.absoluteFill}
            facing="back"
            barcodeScannerSettings={{
              barcodeTypes: ["qr", "ean13", "ean8", "code128", "code39", "code93", "upc_a", "upc_e", "codabar", "itf14", "datamatrix", "pdf417", "aztec"],
            }}
            onBarcodeScanned={onScanned}
          />
          <View style={styles.scanOverlay} pointerEvents="none">
            <View style={styles.scanBracket} />
            <Text style={styles.scanHint}>Point the camera at any Barcode or QR code</Text>
          </View>
          <Pressable style={styles.scanClose} onPress={() => setScannerOpen(false)} testID="scan-close">
            <Ionicons name="close" size={26} color="#fff" />
          </Pressable>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  section: { color: colors.brand, fontSize: font.sm, fontWeight: "800", letterSpacing: 0.5, marginTop: spacing.sm },
  flabel: { color: colors.info, fontSize: font.sm - 1, fontWeight: "800", letterSpacing: 0.5, marginTop: spacing.xs },
  input: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.md, color: colors.onSurface, fontSize: font.base },
  chipWrap: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  chipRow: { gap: spacing.sm, paddingVertical: spacing.xs },
  preview: { backgroundColor: "#fff", borderRadius: radius.sm, padding: spacing.md, alignItems: "center", justifyContent: "center" },
  dim: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  gridCard: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md, alignItems: "center", gap: spacing.sm },
  gridHead: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: spacing.xs },
  gridActions: { flexDirection: "row", gap: spacing.sm },
  miniBtn: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: 6 },
  miniText: { color: colors.brand, fontWeight: "800", fontSize: font.sm },
  grid: { flexDirection: "row", flexWrap: "wrap", alignSelf: "center" },
  cell: { borderWidth: 0.5, borderColor: colors.border, alignItems: "center", justifyContent: "center", backgroundColor: colors.surface },
  cellUsed: { backgroundColor: "#3a2a10" },
  cellFilled: { backgroundColor: colors.brand },
  cellText: { color: colors.info, fontSize: 9, fontWeight: "700" },
  cellTextOn: { color: colors.onBrand },
  legend: { flexDirection: "row", gap: spacing.lg, marginTop: spacing.xs },
  legRow: { flexDirection: "row", alignItems: "center", gap: 6 },
  dot: { width: 14, height: 14, borderRadius: 3, borderWidth: 0.5, borderColor: colors.border },
  legText: { color: colors.onSurface3, fontSize: font.sm - 1 },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  resetBtn: { flexDirection: "row", alignItems: "center", gap: 4, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.md },
  resetText: { color: colors.brand, fontWeight: "700", fontSize: font.sm },
  borderRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: spacing.xs },
  borderLabel: { color: colors.onSurface, fontSize: font.base },
  printBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: spacing.sm, backgroundColor: colors.brand, borderRadius: radius.md, paddingVertical: spacing.md, marginTop: spacing.md },
  printText: { color: colors.onBrand, fontSize: font.base, fontWeight: "800", letterSpacing: 0.5 },
  pnRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  scanBtn: { flexDirection: "row", alignItems: "center", gap: 4, backgroundColor: colors.brand, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.md },
  scanBtnText: { color: colors.onBrand, fontWeight: "800", fontSize: font.sm },
  scanModal: { flex: 1, backgroundColor: "#000" },
  scanOverlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center" },
  scanBracket: { width: 240, height: 160, borderWidth: 3, borderColor: colors.brand, borderRadius: radius.md },
  scanHint: { color: "#fff", fontSize: font.base, marginTop: spacing.lg, textAlign: "center", paddingHorizontal: spacing.xl },
  scanClose: { position: "absolute", top: 48, right: 20, width: 44, height: 44, borderRadius: 22, backgroundColor: "rgba(0,0,0,0.5)", alignItems: "center", justifyContent: "center" },
});
