import { useCallback, useEffect, useState } from "react";
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import {
  Button,
  Card,
  Header,
  Loading,
  Meter,
  StatusChip,
  VerificationBadge,
  LimitBar,
} from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

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

  const load = useCallback(async () => {
    try {
      const res = await api.get(`/search?q=${encodeURIComponent(partNumber)}`);
      setData(res);
      if (res.part) {
        const full = await api.get(`/parts/${encodeURIComponent(partNumber)}`);
        setPart(full);
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

  const approveAI = async () => {
    try {
      await api.post(`/ai/research/${aiResult.id}/approve`);
      show("Approved & saved as Verified", "success");
      load();
    } catch (e: any) {
      show(e?.message || "Approve failed", "error");
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

  const addNewPart = async () => {
    try {
      await api.post("/parts", { part_number: partNumber, company: "All", source: "Manual" });
      show("NEW PART saved (Unverified)", "success");
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
        contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 120, gap: spacing.md }}
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
          </Card>
        ) : (
          <Card testID="new-part-card">
            <Text style={styles.cardTitle}>NEW PART</Text>
            <Text style={styles.dim}>
              આ part number library માં નથી. Save કરો — details AI research પછી Admin approve થાય તો Verified બને.
            </Text>
            {can("manage_parts") ? (
              <Button title="Save NEW PART (Unverified)" onPress={addNewPart} icon="add" testID="add-new-part" style={{ marginTop: spacing.md }} />
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
                Gemini part identify કરે → sources → confidence → Admin approval → Verified save.
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
              <Row label="Year" value={aiResult.result?.year} />
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
                <View style={styles.approveRow}>
                  <Button title="Approve" onPress={approveAI} icon="checkmark" testID="ai-approve" style={{ flex: 1 }} />
                  <Button title="Reject" onPress={rejectAI} variant="danger" icon="close" testID="ai-reject" style={{ flex: 1 }} />
                </View>
              ) : aiResult.approval_status === "Pending" ? (
                <View style={styles.pendingNote}>
                  <Ionicons name="time" size={14} color={colors.warning} />
                  <Text style={styles.pendingText}>Admin approval બાકી — verified તરીકે ન બતાવાય</Text>
                </View>
              ) : null}
              <Button title="Re-run AI" onPress={runAI} loading={aiLoading} variant="ghost" testID="rerun-ai" />
            </View>
          )}
        </Card>

        {/* Units */}
        {part?.units?.length ? (
          <Card>
            <Text style={styles.cardTitle}>STOCK UNITS ({part.units.length})</Text>
            {part.units.map((u: any) => (
              <View key={u.id} style={styles.unitRow}>
                <StatusChip status={u.condition} />
                <Text style={styles.unitLoc}>
                  {[u.location?.rack, u.location?.shelf, u.location?.box, u.location?.position]
                    .filter(Boolean)
                    .join(" → ") || "No location"}
                </Text>
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
  row: { flexDirection: "row", justifyContent: "space-between", gap: spacing.md, paddingVertical: 5 },
  rowLabel: { color: colors.info, fontSize: font.base },
  rowValue: { color: colors.onSurface, fontSize: font.base, fontWeight: "700", flexShrink: 1, textAlign: "right" },
  tech: { color: colors.onSurface2, fontSize: font.base, lineHeight: 20, marginTop: spacing.xs },
  sourceRow: { flexDirection: "row", alignItems: "center", gap: spacing.xs, marginTop: spacing.md },
  source: { color: colors.info, fontSize: font.sm },
  dim: { color: colors.info, fontSize: font.base, lineHeight: 20 },
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
  unitLoc: { color: colors.onSurface3, fontSize: font.base, flex: 1 },
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
});
