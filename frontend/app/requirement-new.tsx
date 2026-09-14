import { useEffect, useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Location from "expo-location";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Button, Card, Field, Header } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";
import type { TranslationKey } from "@/src/i18n/translations";

const PRIORITIES = ["High", "Medium", "Low"];

export default function RequirementNew() {
  const { pn = "", company = "All", gps: gpsParam = "" } = useLocalSearchParams<{ pn: string; company: string; gps: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { show } = useToast();
  const { t } = useLanguage();

  const [partNumber, setPartNumber] = useState(decodeURIComponent(pn as string));
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [priority, setPriority] = useState("Medium");
  const [quantity, setQuantity] = useState("1");
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [gps, setGps] = useState((gpsParam as string) || "");

  useEffect(() => {
    if (gps) return; // already have GPS passed from scanner
    (async () => {
      try {
        const perm = await Location.getForegroundPermissionsAsync();
        let granted = perm.granted;
        if (!granted && perm.canAskAgain) {
          granted = (await Location.requestForegroundPermissionsAsync()).granted;
        }
        if (granted) {
          const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
          setGps(`${loc.coords.latitude.toFixed(6)},${loc.coords.longitude.toFixed(6)}`);
        }
      } catch {
        // location optional — ignore
      }
    })();
  }, []);

  const submit = async () => {
    if (!partNumber.trim()) {
      show(t("common.errPartNumberRequired"), "error");
      return;
    }
    setSubmitting(true);
    try {
      await api.post("/requirements", {
        part_number: partNumber.trim(),
        company,
        name,
        category,
        priority,
        quantity: parseInt(quantity || "1", 10),
        note,
        gps,
      });
      show(t("reqNew.added"), "success");
      router.replace("/(tabs)/requirements" as any);
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setSubmitting(false);
    }
  };

  const prColor: Record<string, string> = { High: colors.error, Medium: colors.warning, Low: colors.info };

  return (
    <View style={styles.flex}>
      <Header title={t("reqNew.title")} subtitle={t("reqNew.subtitle")} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView
          contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 100, gap: spacing.md }}
          keyboardShouldPersistTaps="handled"
        >
          <Card>
            <Field label={t("common.partNumber").toUpperCase()} value={partNumber} onChangeText={setPartNumber} autoCapitalize="characters" placeholder="e.g. 39100-2B000" testID="req-pn" />
            <Field label={t("common.name").toUpperCase()} value={name} onChangeText={setName} placeholder={t("common.partName")} testID="req-name" />
            <Field label={t("common.category").toUpperCase()} value={category} onChangeText={setCategory} placeholder={t("common.category")} testID="req-category" />
            <Field label={t("common.quantity").toUpperCase()} value={quantity} onChangeText={setQuantity} keyboardType="numeric" testID="req-qty" />
            <Field label={t("common.note").toUpperCase()} value={note} onChangeText={setNote} placeholder={t("common.optional")} multiline testID="req-note" />

            <Text style={styles.label}>{t("reqNew.priority").toUpperCase()}</Text>
            <View style={styles.prRow}>
              {PRIORITIES.map((p) => (
                <Pressable
                  key={p}
                  onPress={() => setPriority(p)}
                  style={[
                    styles.prChip,
                    { borderColor: priority === p ? prColor[p] : colors.border, backgroundColor: priority === p ? prColor[p] : colors.surface },
                  ]}
                  testID={`req-priority-${p}`}
                >
                  <Text style={{ color: priority === p ? colors.onError : colors.onSurface2, fontWeight: "800", fontSize: font.base }}>
                    {t(`common.priority.${p}` as TranslationKey)}
                  </Text>
                </Pressable>
              ))}
            </View>
          </Card>
        </ScrollView>
        <View style={[styles.bar, { paddingBottom: insets.bottom + spacing.md }]}>
          <Button title={t("reqNew.submit")} onPress={submit} loading={submitting} icon="add-circle" testID="submit-req" />
        </View>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  label: { color: colors.onSurface3, fontSize: font.sm, fontWeight: "700", letterSpacing: 0.3, marginBottom: spacing.sm },
  prRow: { flexDirection: "row", gap: spacing.sm },
  prChip: { flex: 1, alignItems: "center", paddingVertical: spacing.md, borderRadius: radius.md, borderWidth: 1 },
  bar: { backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border, padding: spacing.md },
});
