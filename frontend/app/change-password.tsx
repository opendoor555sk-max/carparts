import { useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Haptics from "expo-haptics";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Button, Card, Field, Header } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function ChangePassword() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { user } = useAuth();
  const { show } = useToast();
  const { t } = useLanguage();

  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!current.trim()) return show(t("cp.errCurrent"), "error");
    if (next.length < 6) return show(t("cp.errLen"), "error");
    if (next !== confirm) return show(t("cp.errMismatch"), "error");
    if (next === current) return show(t("cp.errSame"), "error");
    setSaving(true);
    try {
      await api.post("/auth/change-password", { current_password: current, new_password: next });
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      show(t("cp.success"), "success");
      setCurrent("");
      setNext("");
      setConfirm("");
      router.back();
    } catch (e: any) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      show(e?.message || t("cp.errFailed"), "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title={t("cp.title")} subtitle={user?.username} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView
          contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 40, gap: spacing.md }}
          keyboardShouldPersistTaps="handled"
        >
          <View style={styles.info}>
            <Ionicons name="lock-closed" size={18} color={colors.brand} />
            <Text style={styles.infoText}>{t("cp.info")}</Text>
          </View>

          <Card>
            <Text style={styles.title}>{t("cp.title").toUpperCase()}</Text>
            <Field
              label={t("cp.current")}
              value={current}
              onChangeText={setCurrent}
              placeholder={t("cp.current")}
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              testID="cp-current"
            />
            <Field
              label={t("cp.new")}
              value={next}
              onChangeText={setNext}
              placeholder={t("cp.newPlaceholder")}
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              testID="cp-new"
            />
            <Field
              label={t("cp.reenter")}
              value={confirm}
              onChangeText={setConfirm}
              placeholder={t("cp.confirmPlaceholder")}
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              testID="cp-confirm"
            />
            <Button title={t("cp.submit")} onPress={submit} loading={saving} icon="save" testID="cp-save" />
          </Card>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  info: {
    flexDirection: "row",
    gap: spacing.sm,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.md,
  },
  infoText: { color: colors.onSurface3, fontSize: font.sm, flex: 1, lineHeight: 18 },
  title: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.md },
});
