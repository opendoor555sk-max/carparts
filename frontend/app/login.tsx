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
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as LocalAuthentication from "expo-local-authentication";

import { useAuth } from "@/src/context/AuthContext";
import { Button, Field } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function Login() {
  const { login, biometricUnlock, hasStoredToken, user } = useAuth();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(false);
  const [bioAvailable, setBioAvailable] = useState(false);

  useEffect(() => {
    if (user) router.replace("/(tabs)");
  }, [user]);

  useEffect(() => {
    (async () => {
      if (Platform.OS === "web") return;
      const hw = await LocalAuthentication.hasHardwareAsync();
      const enrolled = await LocalAuthentication.isEnrolledAsync();
      setBioAvailable(hw && enrolled && hasStoredToken);
    })();
  }, [hasStoredToken]);

  const onLogin = async () => {
    setErr("");
    if (!username.trim() || !password) {
      setErr("Username and password are required");
      return;
    }
    setLoading(true);
    try {
      await login(username.trim(), password);
      router.replace("/(tabs)");
    } catch (e: any) {
      setErr(e?.message || "Login failed");
    } finally {
      setLoading(false);
    }
  };

  const onBio = async () => {
    const ok = await biometricUnlock();
    if (ok) router.replace("/(tabs)");
    else setErr("Biometric unlock failed — use password");
  };

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <ScrollView
        contentContainerStyle={[styles.container, { paddingTop: insets.top + spacing.xxxl }]}
        keyboardShouldPersistTaps="handled"
      >
        <View style={styles.logoWrap}>
          <View style={styles.logoBox}>
            <Ionicons name="hardware-chip" size={40} color={colors.brand} />
          </View>
          <Text style={styles.title}>Auto Parts Store</Text>
          <Text style={styles.subtitle}>Auto Electrical Scrap Parts ERP</Text>
        </View>

        <View style={styles.form}>
          <Field
            label="USERNAME"
            value={username}
            onChangeText={setUsername}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="username"
            testID="login-username"
          />
          <View>
            <Field
              label="PASSWORD"
              value={password}
              onChangeText={setPassword}
              secureTextEntry={!showPw}
              placeholder="password"
              testID="login-password"
              onSubmitEditing={onLogin}
              returnKeyType="go"
            />
            <Pressable
              style={styles.eye}
              onPress={() => setShowPw((s) => !s)}
              hitSlop={12}
              testID="toggle-password"
            >
              <Ionicons name={showPw ? "eye-off" : "eye"} size={20} color={colors.info} />
            </Pressable>
          </View>

          {err ? (
            <View style={styles.errBanner} testID="login-error">
              <Ionicons name="warning" size={16} color={colors.onError} />
              <Text style={styles.errText}>{err}</Text>
            </View>
          ) : null}

          <Button
            title="Sign In"
            onPress={onLogin}
            loading={loading}
            icon="log-in"
            testID="login-submit"
            style={{ marginTop: spacing.sm }}
          />

          {bioAvailable ? (
            <Button
              title="Unlock with Fingerprint"
              onPress={onBio}
              variant="secondary"
              icon="finger-print"
              testID="login-biometric"
              style={{ marginTop: spacing.md }}
            />
          ) : null}

          <Button
            title="Create New Store (Sign Up)"
            onPress={() => router.push("/signup" as any)}
            variant="secondary"
            icon="storefront"
            testID="go-signup"
            style={{ marginTop: spacing.md }}
          />
        </View>

        <Text style={styles.footer}>Every person who downloads can create their own separate store</Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  container: { paddingHorizontal: spacing.xl, paddingBottom: spacing.xxxl, minHeight: "100%" },
  logoWrap: { alignItems: "center", marginBottom: spacing.xxxl },
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
  },
  title: { color: colors.onSurface, fontSize: font.xxl, fontWeight: "800", textAlign: "center" },
  subtitle: { color: colors.brand, fontSize: font.sm, fontWeight: "700", marginTop: spacing.xs, letterSpacing: 0.5 },
  form: {
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: spacing.xl,
  },
  eye: { position: "absolute", right: spacing.md, top: 34 },
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
  footer: { color: colors.info, textAlign: "center", marginTop: spacing.xl, fontSize: font.sm },
});
