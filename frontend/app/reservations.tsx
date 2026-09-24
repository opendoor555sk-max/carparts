import { useCallback, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { useAuth } from "@/src/context/AuthContext";
import { Button, Card, EmptyState, Field, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type Reservation = {
  id: string;
  part_number: string;
  customer_name?: string;
  quantity: number;
  status: "active" | "fulfilled" | "cancelled";
  note?: string;
  by: string;
  at: string;
};

const statusColor = (s: string) =>
  s === "fulfilled" ? colors.success : s === "cancelled" ? colors.error : colors.warning;

export default function Reservations() {
  const router = useRouter();
  const { show } = useToast();
  const { t } = useLanguage();
  const { user } = useAuth();
  const isAdmin = user?.role === "admin" || user?.role === "super_admin";

  const [rows, setRows] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);

  const [partNumber, setPartNumber] = useState("");
  const [customerName, setCustomerName] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await api.get<Reservation[]>("/reservations");
      setRows(res);
    } catch (e: any) {
      show(e?.message || t("reservations.loadFailed"), "error");
    } finally {
      setLoading(false);
    }
  }, [show, t]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const createReservation = async () => {
    const pn = partNumber.trim();
    const qty = parseInt(quantity, 10) || 0;
    if (!pn || qty <= 0) {
      show(t("reservations.errPart"), "error");
      return;
    }
    setSaving(true);
    try {
      await api.post("/reservations", { part_number: pn, customer_name: customerName.trim(), quantity: qty, note: note.trim() });
      show(t("reservations.created"), "success");
      setPartNumber("");
      setCustomerName("");
      setQuantity("1");
      setNote("");
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("reservations.createFailed"), "error");
    } finally {
      setSaving(false);
    }
  };

  const setStatus = async (id: string, status: "fulfilled" | "cancelled") => {
    try {
      await api.patch(`/reservations/${encodeURIComponent(id)}/status`, { status });
      show(t("reservations.updated"), "success");
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("reservations.updateFailed"), "error");
    }
  };

  const deleteReservation = async (id: string) => {
    try {
      await api.del(`/reservations/${encodeURIComponent(id)}`);
      show(t("reservations.deleted"), "success");
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("reservations.deleteFailed"), "error");
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title={t("reservations.title")} onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <Header title={t("reservations.title")} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
          <Card testID="res-create-card">
            <Text style={styles.cardTitle}>{t("reservations.newReservation").toUpperCase()}</Text>
            <Field
              label={t("reservations.partNumber")}
              value={partNumber}
              onChangeText={setPartNumber}
              testID="res-part"
            />
            <Field
              label={t("reservations.customerName")}
              value={customerName}
              onChangeText={setCustomerName}
              placeholder={t("reservations.customerPlaceholder")}
              testID="res-customer"
            />
            <Field
              label={t("reservations.qty")}
              value={quantity}
              onChangeText={setQuantity}
              keyboardType="numeric"
              testID="res-qty"
            />
            <Field label={t("common.noteOptional")} value={note} onChangeText={setNote} testID="res-note" />
            <Button
              title={t("reservations.save")}
              onPress={createReservation}
              loading={saving}
              icon="lock-closed"
              testID="res-save"
            />
          </Card>

          <Card testID="res-list-card">
            <Text style={styles.cardTitle}>{t("reservations.history").toUpperCase()}</Text>
            {rows.length === 0 ? (
              <EmptyState icon="lock-closed-outline" title={t("reservations.noReservations")} />
            ) : (
              rows.map((r) => (
                <View key={r.id} style={styles.resCard} testID={`res-${r.id}`}>
                  <View style={styles.resHeader}>
                    <Text style={styles.resTitle}>
                      {r.part_number} × {r.quantity}
                    </Text>
                    <View style={[styles.statusPill, { backgroundColor: statusColor(r.status) + "22" }]}>
                      <Text style={[styles.statusText, { color: statusColor(r.status) }]}>
                        {t(`reservations.status.${r.status}` as any)}
                      </Text>
                    </View>
                  </View>
                  {r.customer_name ? <Text style={styles.resCustomer}>{r.customer_name}</Text> : null}
                  {r.note ? <Text style={styles.resNote}>{r.note}</Text> : null}
                  <Text style={styles.resMeta}>
                    {new Date(r.at).toLocaleString()} • {r.by}
                  </Text>
                  {r.status === "active" ? (
                    <View style={styles.resActions}>
                      <Button
                        title={t("reservations.fulfill")}
                        onPress={() => setStatus(r.id, "fulfilled")}
                        variant="primary"
                        icon="checkmark-done"
                        style={{ flex: 1 }}
                        testID={`res-fulfill-${r.id}`}
                      />
                      <Button
                        title={t("reservations.cancel")}
                        onPress={() => setStatus(r.id, "cancelled")}
                        variant="danger"
                        icon="close-circle"
                        style={{ flex: 1 }}
                        testID={`res-cancel-${r.id}`}
                      />
                    </View>
                  ) : null}
                  {isAdmin ? (
                    <Button
                      title={t("common.delete")}
                      onPress={() => deleteReservation(r.id)}
                      variant="ghost"
                      icon="trash"
                      testID={`res-delete-${r.id}`}
                    />
                  ) : null}
                </View>
              ))
            )}
          </Card>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.sm },
  resCard: { paddingVertical: spacing.md, borderTopWidth: 1, borderTopColor: colors.divider },
  resHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  resTitle: { color: colors.onSurface, fontSize: font.base, fontWeight: "800" },
  statusPill: { paddingHorizontal: spacing.sm, paddingVertical: 3, borderRadius: radius.sm },
  statusText: { fontSize: font.sm - 1, fontWeight: "800" },
  resCustomer: { color: colors.onSurface2, fontSize: font.sm, marginTop: 2 },
  resNote: { color: colors.onSurface2, fontSize: font.sm, marginTop: 2 },
  resMeta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
  resActions: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.sm },
});
