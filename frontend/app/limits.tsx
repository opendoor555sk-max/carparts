import { useEffect, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Switch, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Button, Card, Field, Header, LimitBar } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function Limits() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { show } = useToast();
  const { t } = useLanguage();

  const [globalEnabled, setGlobalEnabled] = useState(false);
  const [globalDefault, setGlobalDefault] = useState("");
  const [savingGlobal, setSavingGlobal] = useState(false);

  const [partNumber, setPartNumber] = useState("");
  const [partLimit, setPartLimit] = useState("");
  const [partEnabled, setPartEnabled] = useState(true);
  const [savingPart, setSavingPart] = useState(false);
  const [computed, setComputed] = useState<any>(null);

  const [lowStockPn, setLowStockPn] = useState("");
  const [lowStockThreshold, setLowStockThreshold] = useState("");
  const [lowStockEnabled, setLowStockEnabled] = useState(true);
  const [savingLowStock, setSavingLowStock] = useState(false);
  const [lowStockComputed, setLowStockComputed] = useState<any>(null);

  useEffect(() => {
    (async () => {
      try {
        const g = await api.get("/limits/global");
        setGlobalEnabled(!!g.global_enabled);
        setGlobalDefault(g.global_default != null ? String(g.global_default) : "");
      } catch {}
    })();
  }, []);

  const saveGlobal = async () => {
    setSavingGlobal(true);
    try {
      await api.post("/limits/global", {
        global_enabled: globalEnabled,
        global_default: globalDefault ? parseInt(globalDefault, 10) : null,
      });
      show(t("limits.globalSaved"), "success");
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setSavingGlobal(false);
    }
  };

  const savePart = async () => {
    if (!partNumber.trim()) {
      show(t("common.errPartNumberRequired"), "error");
      return;
    }
    setSavingPart(true);
    try {
      const res = await api.post("/limits/part", {
        part_number: partNumber.trim(),
        limit: partLimit ? parseInt(partLimit, 10) : null,
        enabled: partEnabled,
      });
      setComputed(res);
      show(t("limits.partSaved"), "success");
    } catch (e: any) {
      show(e?.message || t("common.saveFailed"), "error");
    } finally {
      setSavingPart(false);
    }
  };

  const saveLowStock = async () => {
    if (!lowStockPn.trim()) {
      show(t("common.errPartNumberRequired"), "error");
      return;
    }
    setSavingLowStock(true);
    try {
      const res = await api.post("/limits/low-stock", {
        part_number: lowStockPn.trim(),
        threshold: lowStockThreshold ? parseInt(lowStockThreshold, 10) : null,
        enabled: lowStockEnabled,
      });
      setLowStockComputed(res);
      show(t("limits.lowStockSaved"), "success");
    } catch (e: any) {
      show(e?.message || t("common.saveFailed"), "error");
    } finally {
      setSavingLowStock(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title={t("limits.title")} subtitle={t("limits.subtitle")} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 40, gap: spacing.md }} keyboardShouldPersistTaps="handled">
          <View style={styles.info}>
            <Ionicons name="information-circle" size={18} color={colors.brand} />
            <Text style={styles.infoText}>{t("limits.infoText")}</Text>
          </View>

          <Card>
            <Text style={styles.title}>{t("limits.globalDefault").toUpperCase()}</Text>
            <View style={styles.rowBetween}>
              <Text style={styles.label}>{t("limits.enableGlobal")}</Text>
              <Switch value={globalEnabled} onValueChange={setGlobalEnabled} trackColor={{ true: colors.brand, false: colors.surface3 }} thumbColor={colors.onSurface} testID="global-enable" />
            </View>
            <Field label={t("limits.defaultMax")} value={globalDefault} onChangeText={setGlobalDefault} keyboardType="numeric" placeholder="e.g. 5" testID="global-default" />
            <Button title={t("limits.saveGlobal")} onPress={saveGlobal} loading={savingGlobal} icon="save" testID="save-global" />
          </Card>

          <Card>
            <Text style={styles.title}>{t("limits.perPartLimit").toUpperCase()}</Text>
            <Field label={t("common.partNumber")} value={partNumber} onChangeText={setPartNumber} autoCapitalize="characters" placeholder="e.g. 39100-2B000" testID="part-limit-pn" />
            <Field label={t("limits.limitBlank")} value={partLimit} onChangeText={setPartLimit} keyboardType="numeric" placeholder="e.g. 3" testID="part-limit-value" />
            <View style={styles.rowBetween}>
              <Text style={styles.label}>{t("limits.enableThisLimit")}</Text>
              <Switch value={partEnabled} onValueChange={setPartEnabled} trackColor={{ true: colors.brand, false: colors.surface3 }} thumbColor={colors.onSurface} testID="part-limit-enable" />
            </View>
            <Button title={t("limits.savePartLimit")} onPress={savePart} loading={savingPart} icon="save" variant="secondary" testID="save-part-limit" />
            {computed ? (
              <View style={{ marginTop: spacing.md }}>
                <LimitBar existing={computed.existing_stock ?? 0} allowed={computed.allowed_limit ?? null} />
                <Text style={styles.status}>{t("limits.status")}: {computed.status}</Text>
              </View>
            ) : null}
          </Card>

          <Card>
            <Text style={styles.title}>{t("limits.lowStockAlert").toUpperCase()}</Text>
            <Field label={t("common.partNumber")} value={lowStockPn} onChangeText={setLowStockPn} autoCapitalize="characters" placeholder="e.g. 39100-2B000" testID="lowstock-pn" />
            <Field label={t("limits.alertWhen")} value={lowStockThreshold} onChangeText={setLowStockThreshold} keyboardType="numeric" placeholder="e.g. 2" testID="lowstock-value" />
            <View style={styles.rowBetween}>
              <Text style={styles.label}>{t("limits.enableThisAlert")}</Text>
              <Switch value={lowStockEnabled} onValueChange={setLowStockEnabled} trackColor={{ true: colors.brand, false: colors.surface3 }} thumbColor={colors.onSurface} testID="lowstock-enable" />
            </View>
            <Button title={t("limits.saveLowStockAlert")} onPress={saveLowStock} loading={savingLowStock} icon="alert-circle" variant="secondary" testID="save-lowstock" />
            {lowStockComputed ? (
              <View style={{ marginTop: spacing.md }}>
                <Text style={[styles.status, lowStockComputed.low && styles.statusLow]}>
                  {lowStockComputed.stock_count} {t("storeDetail.inStock").toLowerCase()}
                  {lowStockComputed.low_stock_threshold != null ? ` • ${t("limits.alertAt")} ≤ ${lowStockComputed.low_stock_threshold}` : ""}
                  {lowStockComputed.low ? ` • ${t("limits.lowStockNow")}` : ""}
                </Text>
              </View>
            ) : null}
          </Card>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  info: { flexDirection: "row", gap: spacing.sm, backgroundColor: colors.brandFaint, borderRadius: radius.md, padding: spacing.md },
  infoText: { color: colors.onBrandFaint, fontSize: font.sm, flex: 1, lineHeight: 18 },
  title: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.md },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: spacing.md },
  label: { color: colors.onSurface2, fontSize: font.base, fontWeight: "600" },
  status: { color: colors.brand, fontWeight: "800", marginTop: spacing.sm },
  statusLow: { color: colors.error },
});
