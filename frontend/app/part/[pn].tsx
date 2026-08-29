import { useCallback, useEffect, useState } from "react";
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Image } from "expo-image";
import * as Haptics from "expo-haptics";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { api, fileUrl } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { Barcode } from "@/src/components/Barcode";
import { SvgXml } from "react-native-svg";
import { codeSvg } from "@/src/utils/codegen";
import { brandingFromUser, printBarcodeLabel } from "@/src/utils/print";
import {
  Button,
  Card,
  ConfirmModal,
  Field,
  Header,
  Loading,
  Meter,
  StatusChip,
  VerificationBadge,
  LimitBar,
} from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type EditMode = "ai-approve" | "edit-part" | "new-part";
type EditData = {
  name: string;
  category: string;
  company: string;
  compatible_vehicles: string;
  variant: string;
  year: string;
  technical_info: string;
};
const EMPTY_EDIT: EditData = {
  name: "", category: "", company: "All", compatible_vehicles: "", variant: "", year: "", technical_info: "",
};

export default function PartDetail() {
  const { pn } = useLocalSearchParams<{ pn: string }>();
  const partNumber = decodeURIComponent(pn as string);
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { can, user } = useAuth();
  const { show } = useToast();

  const [data, setData] = useState<any>(null);
  const [part, setPart] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [aiResult, setAiResult] = useState<any>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [editModal, setEditModal] = useState(false);
  const [editMode, setEditMode] = useState<EditMode>("edit-part");
  const [editData, setEditData] = useState<EditData>(EMPTY_EDIT);
  const [savingEdit, setSavingEdit] = useState(false);
  const [photoUrls, setPhotoUrls] = useState<Record<string, string>>({});
  const isAdmin = user?.role === "admin";
  const [pendingUnit, setPendingUnit] = useState<string | null>(null);
  const [deletingUnit, setDeletingUnit] = useState(false);

  const adjustStock = async (delta: number) => {
    try {
      await api.post("/stock/adjust", { part_number: partNumber, delta });
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    }
  };

  const deleteUnit = (unitId: string) => setPendingUnit(unitId);

  const performDeleteUnit = async () => {
    if (!pendingUnit) return;
    setDeletingUnit(true);
    try {
      await api.del(`/stock/unit/${pendingUnit}`);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
      show("Unit deleted", "success");
      setPendingUnit(null);
      load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    } finally {
      setDeletingUnit(false);
    }
  };

  const load = useCallback(async () => {
    try {
      const res = await api.get(`/search?q=${encodeURIComponent(partNumber)}`);
      setData(res);
      if (res.part) {
        const full = await api.get(`/parts/${encodeURIComponent(partNumber)}`);
        setPart(full);
        // resolve displayable URLs for all unit photos
        const paths: string[] = [];
        (full.units || []).forEach((u: any) => (u.photos || []).forEach((p: string) => paths.push(p)));
        if (paths.length) {
          const entries = await Promise.all(paths.map(async (p) => [p, await fileUrl(p)] as const));
          setPhotoUrls(Object.fromEntries(entries));
        }
      } else {
        setPart(null);
      }
      // load latest AI research for this part
      try {
        const ai = await api.get(`/ai/research?part_number=${encodeURIComponent(partNumber)}`);
        if (ai && ai.length) setAiResult(ai[0]);
      } catch {}
    } catch (e: any) {
      show(e?.message || "Load failed", "error");
    } finally {
      setLoading(false);
    }
  }, [partNumber]);

  useEffect(() => {
    load();
  }, [load]);

  const runAI = async () => {
    setAiLoading(true);
    try {
      const company = data?.part?.company || "All";
      const res = await api.post("/ai/research", { part_number: partNumber, company });
      setAiResult(res);
      show("AI research complete", "success");
    } catch (e: any) {
      show(e?.message || "AI research failed", "error");
    } finally {
      setAiLoading(false);
    }
  };

  const openEdit = (mode: EditMode) => {
    setEditMode(mode);
    if (mode === "ai-approve" && aiResult?.result) {
      const r = aiResult.result;
      setEditData({
        name: r.name || "",
        category: r.category || "",
        company: r.company || aiResult.company || "All",
        compatible_vehicles: (r.compatible_vehicles || []).join(", "),
        variant: r.variant || "",
        year: r.year || "",
        technical_info: r.technical_info || "",
      });
    } else if (mode === "edit-part" && part) {
      setEditData({
        name: part.name || "",
        category: part.category || "",
        company: part.company || "All",
        compatible_vehicles: (part.compatible_vehicles || []).join(", "),
        variant: part.variant || "",
        year: part.year || "",
        technical_info: part.technical_info || "",
      });
    } else {
      const cat = data?.catalog;
      setEditData({
        ...EMPTY_EDIT,
        name: cat?.name || "",
        category: cat?.category || "",
        company: cat?.company || data?.part?.company || "All",
        compatible_vehicles: (cat?.compatible_vehicles || []).join(", "),
        variant: cat?.variant || "",
        year: cat?.year || "",
      });
    }
    setEditModal(true);
  };

  const saveEdit = async () => {
    setSavingEdit(true);
    const payload = {
      name: editData.name,
      category: editData.category,
      company: editData.company,
      compatible_vehicles: editData.compatible_vehicles
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean),
      variant: editData.variant,
      year: editData.year,
      technical_info: editData.technical_info,
    };
    try {
      if (editMode === "ai-approve") {
        await api.post(`/ai/research/${aiResult.id}/approve`, payload);
        show("Edited & saved as Verified", "success");
      } else if (editMode === "edit-part") {
        await api.patch(`/parts/${encodeURIComponent(partNumber)}`, payload);
        show("Part details updated", "success");
      } else {
        await api.post("/parts", { part_number: partNumber, source: "Manual", ...payload });
        show("NEW PART saved (Unverified)", "success");
      }
      setEditModal(false);
      load();
    } catch (e: any) {
      show(e?.message || "Save failed", "error");
    } finally {
      setSavingEdit(false);
    }
  };

  const rejectAI = async () => {
    try {
      await api.post(`/ai/research/${aiResult.id}/reject`);
      show("Rejected", "info");
      load();
    } catch (e: any) {
      show(e?.message || "Reject failed", "error");
    }
  };

  const saveKnown = async () => {
    try {
      await api.post("/known-parts", { part_number: partNumber, company: data?.part?.company || "All" });
      show("Saved as Known Part", "success");
      load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title="Part" onBack={() => router.back()} />
        <Loading text="Loading part…" />
      </View>
    );
  }

  const status = data?.status;
  const p = part || data?.part;
  const limit = part?.limit || data?.limit;

  return (
    <View style={styles.flex}>
      <Header title={status || "Part"} subtitle="Part Master" onBack={() => router.back()} />
      <ScrollView
        contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 180, gap: spacing.md }}
      >
        {/* Part number hero */}
        <Card testID="part-hero">
          <Text style={styles.pnLabel}>PART NUMBER</Text>
          <Text style={styles.pn} selectable testID="part-number">
            {partNumber}
          </Text>
          <View style={styles.chipRow}>
            <StatusChip status={status} testID="part-status" />
            {p ? <VerificationBadge status={p.verification_status} /> : null}
          </View>
          <Text style={styles.stockLine}>
            In stock: <Text style={{ color: colors.brand, fontWeight: "800" }}>{data?.stock_count ?? 0}</Text> units
          </Text>
        </Card>

        {/* Barcode + QR Code */}
        <Card testID="part-barcode">
          <Text style={styles.cardTitle}>BARCODE & QR CODE</Text>
          <Barcode value={partNumber} height={64} />
          <View style={styles.qrWrap}>
            <SvgXml xml={codeSvg("qr", partNumber)} width={120} height={120} />
            <Text style={styles.qrCaption}>{partNumber}</Text>
          </View>
          <Button
            title="Print Barcode Label"
            onPress={async () => printBarcodeLabel(await brandingFromUser(user), partNumber, p?.company)}
            variant="secondary"
            icon="print"
            testID="print-barcode"
            style={{ marginTop: spacing.md }}
          />
        </Card>

        {/* Part master details */}
        {p ? (
          <Card>
            <Text style={styles.cardTitle}>DETAILS</Text>
            <Row label="Name" value={p.name} />
            <Row label="Company" value={p.company} />
            <Row label="Category" value={p.category} />
            <Row label="Variant" value={p.variant} />
            <Row label="Year" value={p.year} />
            <Row label="Old No." value={p.old_number} />
            <Row label="New No." value={p.new_number} />
            <Row label="Sticker Color" value={p.sticker_color} />
            <Row
              label="Compatible"
              value={(p.compatible_vehicles || []).join(", ")}
            />
            {p.technical_info ? (
              <View style={{ marginTop: spacing.sm }}>
                <Text style={styles.rowLabel}>Technical Info</Text>
                <Text style={styles.tech}>{p.technical_info}</Text>
              </View>
            ) : null}
            <View style={styles.sourceRow}>
              <Ionicons name="git-branch" size={13} color={colors.info} />
              <Text style={styles.source}>Source: {p.source || "Manual"}</Text>
            </View>
            {can("manage_parts") ? (
              <Button
                title="Edit Details"
                onPress={() => openEdit("edit-part")}
                icon="create"
                variant="secondary"
                testID="edit-part"
                style={{ marginTop: spacing.md }}
              />
            ) : null}
          </Card>
        ) : (
          <Card testID="new-part-card">
            <Text style={styles.cardTitle}>{data?.catalog ? "NEW TO YOUR STORE" : "NEW PART"}</Text>
            {data?.catalog ? (
              <>
                <View style={styles.catalogBanner}>
                  <Ionicons name="globe" size={14} color={colors.brand} />
                  <Text style={styles.catalogBannerText}>Found in Common Catalog — details shared across stores</Text>
                </View>
                <Row label="Name" value={data.catalog.name} />
                <Row label="Company" value={data.catalog.company} />
                <Row label="Category" value={data.catalog.category} />
                <Row label="Variant" value={data.catalog.variant} />
                <Row label="Compatible" value={(data.catalog.compatible_vehicles || []).join(", ")} />
                <Text style={styles.dim}>Add it to your store — the catalog details are pre-filled.</Text>
              </>
            ) : (
              <Text style={styles.dim}>
                This part number is not in the library. Save it — after AI research and Admin approval it becomes Verified.
              </Text>
            )}
            {can("manage_parts") ? (
              <Button title={data?.catalog ? "Add to My Store" : "Add Details & Save NEW PART"} onPress={() => openEdit("new-part")} icon="add" testID="add-new-part" style={{ marginTop: spacing.md }} />
            ) : null}
          </Card>
        )}

        {/* Purchase limit */}
        {limit ? (
          <Card>
            <Text style={styles.cardTitle}>PURCHASE LIMIT</Text>
            <LimitBar existing={limit.existing_stock ?? 0} allowed={limit.allowed_limit ?? null} />
            {limit.status === "STOP" ? (
              <View style={styles.stopBanner}>
                <Ionicons name="hand-left" size={16} color={colors.onError} />
                <Text style={styles.stopText}>LIMIT REACHED — DO NOT BUY</Text>
              </View>
            ) : null}
          </Card>
        ) : null}

        {/* AI Research */}
        <Card testID="ai-card">
          <View style={styles.aiHead}>
            <Ionicons name="sparkles" size={18} color={colors.brand} />
            <Text style={styles.cardTitle}>AI RESEARCH (Gemini)</Text>
          </View>
          {!aiResult ? (
            <>
              <Text style={styles.dim}>
                Gemini identifies the part → sources → confidence → Admin approval → saved as Verified.
              </Text>
              <Button
                title="Run AI Research"
                onPress={runAI}
                loading={aiLoading}
                icon="search"
                variant="secondary"
                testID="run-ai"
                style={{ marginTop: spacing.md }}
              />
            </>
          ) : (
            <View style={{ gap: spacing.md }}>
              <View style={styles.chipRow}>
                <StatusChip status={aiResult.verification} />
                <StatusChip status={aiResult.approval_status} />
                {aiResult.conflict ? (
                  <View style={styles.conflict}>
                    <Ionicons name="warning" size={13} color={colors.onWarning} />
                    <Text style={styles.conflictText}>Information Conflict</Text>
                  </View>
                ) : null}
                {aiResult.grounded ? (
                  <View style={styles.grounded}>
                    <Ionicons name="globe" size={13} color={colors.onSuccess} />
                    <Text style={styles.groundedText}>Live web sources</Text>
                  </View>
                ) : null}
              </View>
              <Meter
                value={aiResult.confidence || 0}
                color={
                  aiResult.confidence >= 70 ? colors.success : aiResult.confidence >= 40 ? colors.warning : colors.error
                }
                label="Confidence"
              />
              <Row label="Name" value={aiResult.result?.name} />
              <Row label="Category" value={aiResult.result?.category} />
              <Row label="Vehicles" value={(aiResult.result?.compatible_vehicles || []).join(", ")} />
              <Row label="Variant" value={aiResult.result?.variant} />
              <Row label="Year" value={aiResult.result?.year || aiResult.result?.model_years} />
              <Row label="Cross-ref" value={(aiResult.result?.cross_reference || []).join(", ")} />
              {aiResult.result?.status === "NOT_FOUND" ? (
                <View style={styles.notFound}>
                  <Ionicons name="help-circle" size={14} color={colors.warning} />
                  <Text style={styles.notFoundText}>AI could not identify this part number — add details manually</Text>
                </View>
              ) : null}
              {aiResult.from_database ? (
                <View style={styles.dbTag}>
                  <Ionicons name="shield-checkmark" size={13} color={colors.success} />
                  <Text style={styles.dbTagText}>From your Verified Library (100%)</Text>
                </View>
              ) : null}
              {aiResult.result?.technical_info ? (
                <Text style={styles.tech}>{aiResult.result.technical_info}</Text>
              ) : null}
              {(aiResult.sources || []).length ? (
                <View>
                  <Text style={styles.rowLabel}>Sources</Text>
                  {aiResult.sources.map((s: string, i: number) => (
                    <Text key={i} style={styles.sourceItem}>
                      • {s}
                    </Text>
                  ))}
                </View>
              ) : null}
              {aiResult.result?.notes ? <Text style={styles.dim}>{aiResult.result.notes}</Text> : null}

              {aiResult.approval_status === "Pending" && can("ai_approve") ? (
                <View style={{ gap: spacing.sm }}>
                  <Button title="Review, Edit & Approve" onPress={() => openEdit("ai-approve")} icon="create" testID="ai-edit-approve" />
                  <Button title="Reject" onPress={rejectAI} variant="danger" icon="close" testID="ai-reject" />
                </View>
              ) : aiResult.approval_status === "Pending" ? (
                <View style={styles.pendingNote}>
                  <Ionicons name="time" size={14} color={colors.warning} />
                  <Text style={styles.pendingText}>Awaiting Admin approval — not shown as verified</Text>
                </View>
              ) : null}
              <Button title="Re-run AI" onPress={runAI} loading={aiLoading} variant="ghost" testID="rerun-ai" />
            </View>
          )}
        </Card>

        {/* Units */}
        {part?.units?.length ? (
          <Card>
            <View style={styles.stockHead}>
              <Text style={styles.cardTitle}>STOCK UNITS ({part.units.length})</Text>
              {isAdmin ? (
                <View style={styles.qtyCtrl}>
                  <Pressable style={styles.qtyBtn} onPress={() => adjustStock(-1)} testID="pd-dec">
                    <Ionicons name="remove" size={18} color={colors.warning} />
                  </Pressable>
                  <Text style={styles.qtyVal}>{part.units.length}</Text>
                  <Pressable style={styles.qtyBtn} onPress={() => adjustStock(1)} testID="pd-inc">
                    <Ionicons name="add" size={18} color={colors.success} />
                  </Pressable>
                </View>
              ) : null}
            </View>
            {part.units.map((u: any) => (
              <View key={u.id} style={styles.unitRow}>
                <StatusChip status={u.condition} />
                <View style={{ flex: 1 }}>
                  <Text style={styles.unitLoc}>
                    {[u.location?.rack, u.location?.shelf, u.location?.box, u.location?.position]
                      .filter(Boolean)
                      .join(" → ") || "No location"}
                  </Text>
                  {u.location?.gps ? (
                    <View style={styles.unitGps}>
                      <Ionicons name="location" size={12} color={colors.success} />
                      <Text style={styles.unitGpsText} selectable>{u.location.gps}</Text>
                    </View>
                  ) : null}
                  {u.photos?.length ? (
                    <View style={styles.unitPhotos}>
                      {u.photos.map((p: string) =>
                        photoUrls[p] ? (
                          <Image key={p} source={{ uri: photoUrls[p] }} style={styles.unitThumb} contentFit="cover" />
                        ) : null,
                      )}
                    </View>
                  ) : null}
                </View>
                {isAdmin ? (
                  <Pressable onPress={() => deleteUnit(u.id)} hitSlop={10} testID={`pd-del-${u.id}`}>
                    <Ionicons name="trash-outline" size={18} color={colors.error} />
                  </Pressable>
                ) : null}
              </View>
            ))}
          </Card>
        ) : null}
      </ScrollView>

      {/* Sticky actions */}
      <View style={[styles.actionBar, { paddingBottom: insets.bottom + spacing.md }]}>
        <View style={styles.actionRow}>
          {can("buy") ? (
            <Button
              title="Buy"
              onPress={() => router.push(`/buy?pn=${encodeURIComponent(partNumber)}&company=${p?.company || "All"}` as any)}
              icon="download"
              variant="secondary"
              style={{ flex: 1 }}
              testID="goto-buy"
            />
          ) : null}
          {can("sell") ? (
            <Button
              title="Sell"
              onPress={() => router.push(`/sell?pn=${encodeURIComponent(partNumber)}` as any)}
              icon="cash"
              style={{ flex: 1 }}
              testID="goto-sell"
            />
          ) : null}
        </View>
        <View style={styles.actionRow}>
          {can("requirement") ? (
            <Button
              title="Requirement"
              onPress={() => router.push(`/requirement-new?pn=${encodeURIComponent(partNumber)}&company=${p?.company || "All"}` as any)}
              icon="add-circle"
              variant="ghost"
              style={{ flex: 1 }}
              testID="goto-req"
            />
          ) : null}
          {p ? (
            <Button title="Save Known" onPress={saveKnown} icon="bookmark" variant="ghost" style={{ flex: 1 }} testID="save-known" />
          ) : null}
        </View>
      </View>

      <ConfirmModal
        visible={!!pendingUnit}
        title="Delete this unit?"
        message="This one physical unit will be permanently deleted."
        confirmText="Delete"
        danger
        loading={deletingUnit}
        onConfirm={performDeleteUnit}
        onCancel={() => setPendingUnit(null)}
      />

      {/* Edit / Approve details modal */}
      <Modal visible={editModal} transparent animationType="slide" onRequestClose={() => setEditModal(false)}>
        <View style={styles.modalWrap}>
          <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined}>
            <View style={styles.modal}>
              <View style={styles.modalHead}>
                <Text style={styles.modalTitle}>
                  {editMode === "ai-approve" ? "Review & Approve" : editMode === "edit-part" ? "Edit Details" : "Add Part Details"}
                </Text>
                <Pressable onPress={() => setEditModal(false)} testID="close-edit-modal">
                  <Ionicons name="close" size={24} color={colors.onSurface} />
                </Pressable>
              </View>
              <Text style={styles.modalPn}>{partNumber}</Text>
              {editMode === "ai-approve" ? (
                <View style={styles.aiNote}>
                  <Ionicons name="information-circle" size={14} color={colors.brand} />
                  <Text style={styles.aiNoteText}>
                    This is an AI suggestion — if wrong, correct it, then it saves as Verified.
                  </Text>
                </View>
              ) : null}
              <ScrollView keyboardShouldPersistTaps="handled" style={{ maxHeight: 460 }}>
                <Field label="NAME" value={editData.name} onChangeText={(t) => setEditData((d) => ({ ...d, name: t }))} testID="edit-name" />
                <Field label="CATEGORY" value={editData.category} onChangeText={(t) => setEditData((d) => ({ ...d, category: t }))} testID="edit-category" />
                <Field label="COMPANY" value={editData.company} onChangeText={(t) => setEditData((d) => ({ ...d, company: t }))} testID="edit-company" />
                <Field
                  label="COMPATIBLE VEHICLES (comma separated)"
                  value={editData.compatible_vehicles}
                  onChangeText={(t) => setEditData((d) => ({ ...d, compatible_vehicles: t }))}
                  placeholder="Hyundai Creta, Kia Seltos"
                  testID="edit-vehicles"
                />
                <Field label="VARIANT" value={editData.variant} onChangeText={(t) => setEditData((d) => ({ ...d, variant: t }))} testID="edit-variant" />
                <Field label="YEAR" value={editData.year} onChangeText={(t) => setEditData((d) => ({ ...d, year: t }))} testID="edit-year" />
                <Field
                  label="TECHNICAL INFO"
                  value={editData.technical_info}
                  onChangeText={(t) => setEditData((d) => ({ ...d, technical_info: t }))}
                  multiline
                  testID="edit-technical"
                />
                <Button
                  title={editMode === "ai-approve" ? "Approve & Save Verified" : editMode === "edit-part" ? "Save Changes" : "Save Part"}
                  onPress={saveEdit}
                  loading={savingEdit}
                  icon="checkmark"
                  testID="save-edit"
                  style={{ marginTop: spacing.md, marginBottom: spacing.md }}
                />
              </ScrollView>
            </View>
          </KeyboardAvoidingView>
        </View>
      </Modal>
    </View>
  );
}

function Row({ label, value }: { label: string; value?: string }) {
  if (!value) return null;
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  pnLabel: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1 },
  pn: { color: colors.onSurface, fontSize: font.huge, fontWeight: "800", letterSpacing: 1, marginVertical: spacing.sm },
  chipRow: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: spacing.sm },
  stockLine: { color: colors.onSurface2, fontSize: font.base, marginTop: spacing.sm },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.md },
  qrWrap: { alignItems: "center", backgroundColor: "#fff", borderRadius: radius.md, paddingVertical: spacing.md, marginTop: spacing.md },
  qrCaption: { color: "#000", fontSize: font.sm, fontWeight: "700", marginTop: spacing.xs, letterSpacing: 0.5 },
  row: { flexDirection: "row", justifyContent: "space-between", gap: spacing.md, paddingVertical: 5 },
  rowLabel: { color: colors.info, fontSize: font.base },
  rowValue: { color: colors.onSurface, fontSize: font.base, fontWeight: "700", flexShrink: 1, textAlign: "right" },
  tech: { color: colors.onSurface2, fontSize: font.base, lineHeight: 20, marginTop: spacing.xs },
  sourceRow: { flexDirection: "row", alignItems: "center", gap: spacing.xs, marginTop: spacing.md },
  source: { color: colors.info, fontSize: font.sm },
  dim: { color: colors.info, fontSize: font.base, lineHeight: 20 },
  catalogBanner: { flexDirection: "row", alignItems: "center", gap: spacing.xs, backgroundColor: colors.brandFaint, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: spacing.xs, marginBottom: spacing.sm },
  catalogBannerText: { color: colors.brand, fontSize: font.sm - 1, fontWeight: "700", flex: 1 },
  stopBanner: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.sm,
    backgroundColor: colors.error,
    borderRadius: radius.md,
    padding: spacing.md,
    marginTop: spacing.md,
  },
  stopText: { color: colors.onError, fontWeight: "800", fontSize: font.base, letterSpacing: 0.5 },
  aiHead: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginBottom: spacing.sm },
  conflict: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: colors.warning,
    borderRadius: radius.sm,
    paddingHorizontal: spacing.sm,
    paddingVertical: 4,
  },
  conflictText: { color: colors.onWarning, fontWeight: "800", fontSize: font.sm },
  sourceItem: { color: colors.onSurface3, fontSize: font.sm, marginTop: 2 },
  approveRow: { flexDirection: "row", gap: spacing.md },
  pendingNote: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  pendingText: { color: colors.warning, fontSize: font.sm, flex: 1 },
  unitRow: { flexDirection: "row", alignItems: "center", gap: spacing.md, paddingVertical: spacing.sm, borderTopWidth: 1, borderTopColor: colors.divider },
  unitLoc: { color: colors.onSurface3, fontSize: font.base },
  unitGps: { flexDirection: "row", alignItems: "center", gap: 3, marginTop: 2 },
  unitGpsText: { color: colors.success, fontSize: font.sm - 1, fontWeight: "700" },
  unitPhotos: { flexDirection: "row", flexWrap: "wrap", gap: 6, marginTop: 6 },
  unitThumb: { width: 54, height: 54, borderRadius: radius.sm, backgroundColor: colors.surface3 },
  stockHead: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: spacing.xs },
  qtyCtrl: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  qtyBtn: { width: 34, height: 34, borderRadius: radius.sm, borderWidth: 1, borderColor: colors.border, alignItems: "center", justifyContent: "center", backgroundColor: colors.surface },
  qtyVal: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", minWidth: 24, textAlign: "center" },
  actionBar: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    padding: spacing.md,
    gap: spacing.sm,
  },
  actionRow: { flexDirection: "row", gap: spacing.sm },
  modalWrap: { flex: 1, backgroundColor: "rgba(0,0,0,0.7)", justifyContent: "flex-end" },
  modal: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
  },
  modalHead: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  modalTitle: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800" },
  modalPn: { color: colors.brand, fontSize: font.lg, fontWeight: "800", marginTop: 2, marginBottom: spacing.md },
  aiNote: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    backgroundColor: colors.brandFaint,
    borderRadius: radius.sm,
    padding: spacing.sm,
    marginBottom: spacing.md,
  },
  aiNoteText: { color: colors.onBrandFaint, fontSize: font.sm, flex: 1 },
  notFound: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: "#3a3300", borderRadius: radius.sm, padding: spacing.sm, marginTop: spacing.xs },
  notFoundText: { color: colors.warning, fontSize: font.sm, flex: 1 },
  dbTag: { flexDirection: "row", alignItems: "center", gap: spacing.xs, marginTop: spacing.sm },
  dbTagText: { color: colors.success, fontSize: font.sm, fontWeight: "700" },
  grounded: { flexDirection: "row", alignItems: "center", gap: 4, backgroundColor: colors.success, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 4 },
  groundedText: { color: colors.onSuccess, fontWeight: "800", fontSize: font.sm },
});
