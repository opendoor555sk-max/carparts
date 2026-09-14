import { useCallback, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Button, Card, EmptyState, Field, Header, Loading } from "@/src/components/ui";
import { exportExcel } from "@/src/utils/excelExport";
import { colors, font, spacing } from "@/src/theme";

type LedgerEntry = {
  id: string;
  type: "sale" | "payment";
  amount: number;
  note?: string;
  part_number?: string;
  running_balance: number;
  by: string;
  at: string;
};

export default function CustomerDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const customerId = id as string;
  const router = useRouter();
  const { show } = useToast();
  const { t } = useLanguage();

  const [customer, setCustomer] = useState<any>(null);
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [balance, setBalance] = useState(0);
  const [loading, setLoading] = useState(true);

  const [editing, setEditing] = useState(false);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [savingEdit, setSavingEdit] = useState(false);

  const [payAmount, setPayAmount] = useState("");
  const [payNote, setPayNote] = useState("");
  const [showPayForm, setShowPayForm] = useState(false);
  const [recordingPayment, setRecordingPayment] = useState(false);
  const [exporting, setExporting] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await api.get(`/customers/${encodeURIComponent(customerId)}/ledger`);
      setCustomer(res.customer);
      setEntries(res.entries);
      setBalance(res.balance);
      setName(res.customer.name);
      setPhone(res.customer.phone);
      setAddress(res.customer.address || "");
    } catch (e: any) {
      show(e?.message || t("customers.loadFailed"), "error");
    } finally {
      setLoading(false);
    }
  }, [customerId, show, t]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const saveEdit = async () => {
    if (!name.trim() || !phone.trim()) {
      show(t("customers.errRequired"), "error");
      return;
    }
    setSavingEdit(true);
    try {
      await api.patch(`/customers/${encodeURIComponent(customerId)}`, {
        name: name.trim(),
        phone: phone.trim(),
        address: address.trim(),
      });
      show(t("customers.updated"), "success");
      setEditing(false);
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("common.updateFailed"), "error");
    } finally {
      setSavingEdit(false);
    }
  };

  const recordPayment = async () => {
    const amt = parseFloat(payAmount);
    if (!amt || amt <= 0) {
      show(t("customers.errPaymentAmount"), "error");
      return;
    }
    setRecordingPayment(true);
    try {
      await api.post(`/customers/${encodeURIComponent(customerId)}/payments`, { amount: amt, note: payNote.trim() });
      show(t("customers.paymentRecorded"), "success");
      setPayAmount("");
      setPayNote("");
      setShowPayForm(false);
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("customers.paymentFailed"), "error");
    } finally {
      setRecordingPayment(false);
    }
  };

  const doExport = async () => {
    setExporting(true);
    try {
      await exportExcel(`/customers/${encodeURIComponent(customerId)}/ledger/excel`,
        `ledger_${customer.name.replace(/\s+/g, "_")}.xlsx`);
    } catch (e: any) {
      show(e?.message || t("common.exportFailed"), "error");
    } finally {
      setExporting(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title={t("customers.detailTitle")} onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  if (!customer) {
    return (
      <View style={styles.flex}>
        <Header title={t("customers.detailTitle")} onBack={() => router.back()} />
        <EmptyState icon="person-remove-outline" title={t("customers.notFound")} />
      </View>
    );
  }

  const owesMoney = balance > 0;
  const inAdvance = balance < 0;

  return (
    <View style={styles.flex}>
      <Header title={customer.name} subtitle={customer.phone} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
          <Card testID="customer-balance-card">
            <View style={styles.rowBetween}>
              <Text style={styles.cardTitle}>{t("customers.balance").toUpperCase()}</Text>
              <Button
                title={editing ? t("ui.cancel") : t("common.edit")}
                onPress={() => setEditing((e) => !e)}
                variant="ghost"
                icon={editing ? "close" : "pencil"}
                testID="customer-edit-toggle"
              />
            </View>
            <Text style={[styles.balanceBig, { color: owesMoney ? colors.error : inAdvance ? colors.success : colors.onSurface }]}>
              ₹{Math.abs(balance).toFixed(2)}
            </Text>
            <Text style={styles.balanceSub}>
              {owesMoney ? t("customers.owedToStore") : inAdvance ? t("customers.storeOwes") : t("customers.settledNoDues")}
            </Text>
          </Card>

          {editing ? (
            <Card>
              <Text style={styles.cardTitle}>{t("customers.editCustomer").toUpperCase()}</Text>
              <Field label={t("common.name")} value={name} onChangeText={setName} placeholder={t("customers.namePlaceholder")} testID="edit-name" />
              <Field label={t("common.phone")} value={phone} onChangeText={setPhone} placeholder={t("common.phoneNumber")} keyboardType="phone-pad" testID="edit-phone" />
              <Field label={t("common.address")} value={address} onChangeText={setAddress} placeholder={t("common.address")} multiline testID="edit-address" />
              <Button title={t("common.saveChanges")} onPress={saveEdit} loading={savingEdit} icon="checkmark-circle" testID="edit-save" />
            </Card>
          ) : customer.address ? (
            <Card>
              <Text style={styles.cardTitle}>{t("common.address").toUpperCase()}</Text>
              <Text style={styles.addressText}>{customer.address}</Text>
            </Card>
          ) : null}

          <Card>
            <View style={styles.rowBetween}>
              <Text style={styles.cardTitle}>{t("customers.recordPayment").toUpperCase()}</Text>
              {!showPayForm ? (
                <Button title={t("customers.recordPayment")} onPress={() => setShowPayForm(true)} icon="cash" testID="show-payment-form" />
              ) : null}
            </View>
            {showPayForm ? (
              <View style={{ marginTop: spacing.sm }}>
                <Field label={t("customers.amountReceived")} value={payAmount} onChangeText={setPayAmount} placeholder="₹ 0" keyboardType="numeric" testID="payment-amount" />
                <Field label={t("common.noteOptional")} value={payNote} onChangeText={setPayNote} placeholder="e.g. Cash, UPI" testID="payment-note" />
                <Button title={t("customers.confirmPayment")} onPress={recordPayment} loading={recordingPayment} icon="checkmark-circle" testID="confirm-payment" />
              </View>
            ) : null}
          </Card>

          <Card testID="customer-ledger-card">
            <View style={styles.rowBetween}>
              <Text style={styles.cardTitle}>{t("customers.transactionHistory").toUpperCase()}</Text>
              {entries.length > 0 ? (
                <Button
                  title={t("common.export")}
                  onPress={doExport}
                  loading={exporting}
                  variant="ghost"
                  icon="download"
                  testID="customer-export-excel"
                />
              ) : null}
            </View>
            {entries.length === 0 ? (
              <Text style={styles.emptyLedger}>{t("customers.noTransactions")}</Text>
            ) : (
              entries.map((e) => (
                <View key={e.id} style={styles.entryRow} testID={`ledger-entry-${e.id}`}>
                  <Ionicons
                    name={e.type === "sale" ? "arrow-up-circle" : "arrow-down-circle"}
                    size={22}
                    color={e.type === "sale" ? colors.error : colors.success}
                  />
                  <View style={{ flex: 1 }}>
                    <Text style={styles.entryTitle}>
                      {e.type === "sale" ? `${t("customers.sale")}${e.part_number ? ` — ${e.part_number}` : ""}` : t("customers.paymentReceived")}
                    </Text>
                    {e.note ? <Text style={styles.entryNote}>{e.note}</Text> : null}
                    <Text style={styles.entryMeta}>{new Date(e.at).toLocaleString()} • {e.by}</Text>
                  </View>
                  <View style={{ alignItems: "flex-end" }}>
                    <Text style={[styles.entryAmount, { color: e.type === "sale" ? colors.error : colors.success }]}>
                      {e.type === "sale" ? "+" : "−"}₹{e.amount.toFixed(2)}
                    </Text>
                    <Text style={styles.entryRunning}>{t("customers.bal")}: ₹{e.running_balance.toFixed(2)}</Text>
                  </View>
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
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.sm },
  balanceBig: { fontSize: 40, fontWeight: "900", marginTop: spacing.sm },
  balanceSub: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  addressText: { color: colors.onSurface2, fontSize: font.base },
  emptyLedger: { color: colors.info, textAlign: "center", paddingVertical: spacing.lg },
  entryRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, paddingVertical: spacing.md, borderTopWidth: 1, borderTopColor: colors.divider },
  entryTitle: { color: colors.onSurface, fontSize: font.base, fontWeight: "700" },
  entryNote: { color: colors.onSurface2, fontSize: font.sm, marginTop: 1 },
  entryMeta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
  entryAmount: { fontSize: font.base, fontWeight: "900" },
  entryRunning: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
});
