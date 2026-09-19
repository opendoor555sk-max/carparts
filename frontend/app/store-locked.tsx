import { Linking, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useAuth } from "@/src/context/AuthContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { useToast } from "@/src/context/ToastContext";
import { Button } from "@/src/components/ui";
import { shareTextOnWhatsApp } from "@/src/utils/print";
import { colors, font, radius, spacing } from "@/src/theme";

// Reached two ways, both from client.ts's global store_locked detection in
// request() (see src/api/client.ts): mid-session, any API call a locked
// store's user makes redirects here immediately; from the login screen, a
// blocked login lands here the same way since /auth/login runs through that
// same request() function. Either way this is a hard stop, not a toast —
// full-screen, no way back into the app's normal screens while locked.
export default function StoreLocked() {
  const { message, contact } = useLocalSearchParams<{ message?: string; contact?: string }>();
  const { logout } = useAuth();
  const { t } = useLanguage();
  const { show } = useToast();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const phone = (contact as string) || "";
  const displayMessage = (message as string) || t("storeLocked.defaultMessage");

  const call = () => {
    if (!phone) return;
    Linking.openURL(`tel:${phone}`).catch(() => show(t("common.failed"), "error"));
  };

  const whatsapp = async () => {
    try {
      await shareTextOnWhatsApp(t("storeLocked.whatsappText"), phone);
    } catch {
      show(t("common.failed"), "error");
    }
  };

  const backToLogin = async () => {
    try {
      await logout();
    } finally {
      router.replace("/login");
    }
  };

  return (
    <View style={[styles.flex, { paddingTop: insets.top + spacing.xxxl, paddingBottom: insets.bottom + spacing.xl }]}>
      <View style={styles.iconWrap}>
        <Ionicons name="lock-closed" size={56} color={colors.error} />
      </View>
      <Text style={styles.title}>{t("storeLocked.title")}</Text>
      <Text style={styles.message}>{displayMessage}</Text>

      <View style={styles.actions}>
        {phone ? (
          <Button title={`${t("storeLocked.call")} ${phone}`} onPress={call} icon="call" testID="store-locked-call" />
        ) : null}
        {phone ? (
          <Button
            title={t("storeLocked.whatsapp")}
            onPress={whatsapp}
            variant="secondary"
            icon="logo-whatsapp"
            testID="store-locked-whatsapp"
            style={{ marginTop: spacing.md }}
          />
        ) : null}
        <Button
          title={t("storeLocked.backToLogin")}
          onPress={backToLogin}
          variant="ghost"
          icon="log-out"
          testID="store-locked-back"
          style={{ marginTop: spacing.md }}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface, alignItems: "center", paddingHorizontal: spacing.xl },
  iconWrap: {
    width: 96,
    height: 96,
    borderRadius: radius.lg,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.error,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.xl,
  },
  title: { color: colors.onSurface, fontSize: font.xxl, fontWeight: "800", textAlign: "center" },
  message: { color: colors.info, fontSize: font.base, textAlign: "center", marginTop: spacing.md, lineHeight: 20 },
  actions: { width: "100%", marginTop: spacing.xxxl },
});
