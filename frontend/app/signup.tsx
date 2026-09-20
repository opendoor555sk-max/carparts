import { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Clipboard from "expo-clipboard";

import { useAuth } from "@/src/context/AuthContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { useToast } from "@/src/context/ToastContext";
import { Button, Field } from "@/src/components/ui";
import { colors, font, radius, shadow, spacing } from "@/src/theme";
import { OWNER_CONTACT } from "@/src/constants/owner";
import { shareTextOnWhatsApp } from "@/src/utils/print";

type Step = "form" | "otp" | "done";

export default function SignUp() {
  const { createStoreRequest, verifyStoreRequestOtp } = useAuth();
  const { t } = useLanguage();
  const { show } = useToast();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [step, setStep] = useState<Step>("form");
  const [storeName, setStoreName] = useState("");
  const [mobile, setMobile] = useState("");
  const [requestId, setRequestId] = useState("");
  const [otp, setOtp] = useState("");
  const [username, setUsername] = useState("");
  const [tempPassword, setTempPassword] = useState("");
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(false);

  const openWhatsApp = async (name: string, mob: string) => {
    try {
      await shareTextOnWhatsApp(`New store request: ${name}, ${mob}`, OWNER_CONTACT);
    } catch {
      // Non-fatal -- the request already exists server-side either way; the
      // owner can also be reached by phone using the helper text on step 2.
    }
  };

  const submitRequest = async () => {
    setErr("");
    if (!storeName.trim()) {
      setErr(t("signup.errStoreNameRequired"));
      return;
    }
    if (!mobile.trim()) {
      setErr(t("signup.errContact"));
      return;
    }
    setLoading(true);
    try {
      const req = await createStoreRequest(storeName.trim(), mobile.trim());
      setRequestId(req.id);
      await openWhatsApp(storeName.trim(), mobile.trim());
      setStep("otp");
    } catch (e: any) {
      setErr(e?.message || t("signup.errFailed"));
    } finally {
      setLoading(false);
    }
  };

  const resendWhatsApp = async () => {
    await openWhatsApp(storeName.trim(), mobile.trim());
    show(t("signup.otpResent"), "info");
  };

  const submitOtp = async () => {
    setErr("");
    if (!otp.trim()) {
      setErr(t("signup.errOtpRequired"));
      return;
    }
    setLoading(true);
    try {
      const res = await verifyStoreRequestOtp(requestId, otp.trim());
      setUsername(res.username);
      setTempPassword(res.tempPassword);
      setStep("done");
    } catch (e: any) {
      setErr(e?.message || t("signup.errOtpFailed"));
    } finally {
      setLoading(false);
    }
  };

  const copyPassword = async () => {
    await Clipboard.setStringAsync(tempPassword);
    show(t("signup.copied"), "success");
  };

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <ScrollView
        contentContainerStyle={[styles.container, { paddingTop: insets.top + spacing.xxl }]}
        keyboardShouldPersistTaps="handled"
      >
        <View style={styles.logoWrap}>
          <View style={styles.logoBox}>
            <Ionicons name="storefront" size={38} color={colors.brand} />
          </View>
          <Text style={styles.title}>{t("signup.title")}</Text>
          <Text style={styles.subtitle}>{t("signup.subtitle")}</Text>
        </View>

        {step === "form" ? (
          <View style={styles.form}>
            <Field
              label={t("signup.storeName").toUpperCase()}
              value={storeName}
              onChangeText={setStoreName}
              placeholder={t("signup.storeNamePlaceholder")}
              testID="signup-store"
            />
            <Field
              label={t("signup.contact").toUpperCase()}
              value={mobile}
              onChangeText={setMobile}
              placeholder="e.g. +91 98xxxxxxxx"
              keyboardType="phone-pad"
              testID="signup-mobile"
              onSubmitEditing={submitRequest}
              returnKeyType="go"
            />

            {err ? (
              <View style={styles.errBanner} testID="signup-error">
                <Ionicons name="warning" size={16} color={colors.onError} />
                <Text style={styles.errText}>{err}</Text>
              </View>
            ) : null}

            <Button
              title={t("signup.sendRequest")}
              onPress={submitRequest}
              loading={loading}
              icon="logo-whatsapp"
              testID="signup-submit"
              style={{ marginTop: spacing.sm }}
            />
          </View>
        ) : null}

        {step === "otp" ? (
          <View style={styles.form}>
            <Text style={styles.summary}>{storeName} · {mobile}</Text>
            <Text style={styles.helper}>{t("signup.otpHelper")}</Text>

            <Field
              label={t("signup.otpLabel").toUpperCase()}
              value={otp}
              onChangeText={setOtp}
              placeholder={t("signup.otpPlaceholder")}
              keyboardType="number-pad"
              maxLength={6}
              testID="signup-otp"
              onSubmitEditing={submitOtp}
              returnKeyType="go"
            />

            {err ? (
              <View style={styles.errBanner} testID="signup-otp-error">
                <Ionicons name="warning" size={16} color={colors.onError} />
                <Text style={styles.errText}>{err}</Text>
              </View>
            ) : null}

            <Button
              title={t("signup.verifySubmit")}
              onPress={submitOtp}
              loading={loading}
              icon="checkmark-circle"
              testID="signup-verify-otp"
              style={{ marginTop: spacing.sm }}
            />

            <Pressable onPress={resendWhatsApp} style={styles.linkRow} testID="signup-resend-whatsapp">
              <Ionicons name="logo-whatsapp" size={16} color={colors.brand} />
              <Text style={[styles.linkText, { color: colors.brand, fontWeight: "800", marginLeft: spacing.xs }]}>
                {t("signup.resendWhatsapp")}
              </Text>
            </Pressable>
            <Pressable onPress={() => { setStep("form"); setErr(""); setOtp(""); }} style={styles.linkRow} testID="signup-back">
              <Text style={styles.linkText}>{t("signup.editDetails")}</Text>
            </Pressable>
          </View>
        ) : null}

        {step === "done" ? (
          <View style={styles.form}>
            <Ionicons name="checkmark-circle" size={40} color={colors.success} style={{ alignSelf: "center", marginBottom: spacing.sm }} />
            <Text style={styles.summary}>{t("signup.storeReady")}</Text>
            <Text style={styles.helper}>{t("signup.tempCredsHelper")}</Text>

            <View style={styles.credRow}>
              <Text style={styles.credLabel}>{t("signup.usernameLabel")}</Text>
              <Text style={styles.credValue} selectable>{username}</Text>
            </View>
            <View style={styles.credRow}>
              <Text style={styles.credLabel}>{t("login.password")}</Text>
              <Text style={styles.credValue} selectable>{tempPassword}</Text>
              <Pressable onPress={copyPassword} hitSlop={10} testID="signup-copy-password">
                <Ionicons name="copy-outline" size={18} color={colors.brand} />
              </Pressable>
            </View>

            <Button
              title={t("signup.continueToApp")}
              onPress={() => router.replace("/(tabs)")}
              icon="arrow-forward-circle"
              testID="signup-continue"
              style={{ marginTop: spacing.lg }}
            />
          </View>
        ) : null}

        {step !== "done" ? (
          <Pressable onPress={() => router.replace("/login")} style={styles.linkRow} testID="go-login">
            <Text style={styles.linkText}>{t("signup.haveAccount")} </Text>
            <Text style={[styles.linkText, { color: colors.brand, fontWeight: "800" }]}>{t("login.signIn")}</Text>
          </Pressable>
        ) : null}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  container: { paddingHorizontal: spacing.xl, paddingBottom: spacing.xxxl, minHeight: "100%" },
  logoWrap: { alignItems: "center", marginBottom: spacing.xl },
  logoBox: {
    width: 84,
    height: 84,
    borderRadius: radius.lg,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.lg,
    ...shadow.sm,
  },
  title: { color: colors.onSurface, fontSize: font.xxl, fontWeight: "800", textAlign: "center" },
  subtitle: { color: colors.brand, fontSize: font.sm, fontWeight: "700", marginTop: spacing.xs },
  form: {
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: spacing.xl,
    ...shadow.md,
  },
  summary: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", textAlign: "center", marginBottom: spacing.xs },
  helper: { color: colors.info, fontSize: font.sm, textAlign: "center", marginBottom: spacing.lg, lineHeight: 18 },
  credRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: spacing.sm, marginBottom: spacing.sm },
  credLabel: { color: colors.info, fontSize: font.sm - 1, fontWeight: "700", width: 90 },
  credValue: { color: colors.onSurface, fontSize: font.base, fontWeight: "700", flex: 1 },
  errBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    backgroundColor: colors.error,
    borderRadius: radius.md,
    padding: spacing.md,
    marginBottom: spacing.sm,
  },
  errText: { color: colors.onError, fontSize: font.base, fontWeight: "700", flex: 1 },
  linkRow: { flexDirection: "row", justifyContent: "center", alignItems: "center", marginTop: spacing.xl },
  linkText: { color: colors.info, fontSize: font.base },
});
