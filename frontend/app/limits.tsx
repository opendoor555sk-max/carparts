import { useCallback, useEffect, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Switch, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, Field, Header, LimitBar } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function Limits() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { show } = useToast();

  const [globalEnabled, setGlobalEnabled] = useState(false);
  const [globalDefault, setGlobalDefault] = useState("");
  const [savingGlobal, setSavingGlobal] = useState(false);

  const [partNumber, setPartNumber] = useState("");
  const [partLimit, setPartLimit] = useState("");
  const [partEnabled, setPartEnabled] = useState(true);
  const [savingPart, setSavingPart] = useState(false);
  const [computed, setComputed] = useState<any>(null);

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
      show("Global limit saved", "success");
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    } finally {
      setSavingGlobal(false);
    }
  };

  const savePart = async () => {
    if (!partNumber.trim()) {
      show("Part number જરૂરી", "error");
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
      show("Part limit saved", "success");
    } catch (e: any) {
      show(e?.message || "Part not found — first Buy/Save part", "error");
    } finally {
      setSavingPart(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title="Purchase Limits" subtitle="100% Admin configurable" onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 40, gap: spacing.md }} keyboardShouldPersistTaps="handled">
          <View style={styles.info}>
            <Ionicons name="information-circle" size={18} color={colors.brand} />
            <Text style={styles.infoText}>કોઈ number hard-coded નથી. Admin જ set/change/disable/override કરે.</Text>
          </View>

          <Card>
            <Text style={styles.title}>GLOBAL DEFAULT LIMIT</Text>
            <View style={styles.rowBetween}>
              <Text style={styles.label}>Enable global limit</Text>
              <Switch value={globalEnabled} onValueChange={setGlobalEnabled} trackColor={{ true: colors.brand, false: colors.surface3 }} thumbColor={colors.onSurface} testID="global-enable" />
            </View>
            <Field label="Default max stock per part" value={globalDefault} onChangeText={setGlobalDefault} keyboardType="numeric" placeholder="e.g. 5" testID="global-default" />
            <Button title="Save Global" onPress={saveGlobal} loading={savingGlobal} icon="save" testID="save-global" />
          </Card>

          <Card>
            <Text style={styles.title}>PER-PART LIMIT (override)</Text>
            <Field label="Part number" value={partNumber} onChangeText={setPartNumber} autoCapitalize="characters" placeholder="e.g. 39100-2B000" testID="part-limit-pn" />
            <Field label="Limit (blank = unlimited)" value={partLimit} onChangeText={setPartLimit} keyboardType="numeric" placeholder="e.g. 3" testID="part-limit-value" />
            <View style={styles.rowBetween}>
              <Text style={styles.label}>Enable this limit</Text>
              <Switch value={partEnabled} onValueChange={setPartEnabled} trackColor={{ true: colors.brand, false: colors.surface3 }} thumbColor={colors.onSurface} testID="part-limit-enable" />
            </View>
            <Button title="Save Part Limit" onPress={savePart} loading={savingPart} icon="save" variant="secondary" testID="save-part-limit" />
            {computed ? (
              <View style={{ marginTop: spacing.md }}>
                <LimitBar existing={computed.existing_stock ?? 0} allowed={computed.allowed_limit ?? null} />
                <Text style={styles.status}>Status: {computed.status}</Text>
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
  info: { flexDirection: "row", gap: spacing.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md },
  infoText: { color: colors.onSurface3, fontSize: font.sm, flex: 1, lineHeight: 18 },
  title: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.md },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: spacing.md },
  label: { color: colors.onSurface2, fontSize: font.base, fontWeight: "600" },
  status: { color: colors.brand, fontWeight: "800", marginTop: spacing.sm },
});
