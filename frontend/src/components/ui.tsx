import React from "react";
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  TextInputProps,
  View,
  ViewStyle,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { colors, font, radius, spacing, statusColor } from "@/src/theme";

// ---------- Screen header (sticky, safe-area aware) ----------
export function Header({
  title,
  subtitle,
  onBack,
  right,
}: {
  title: string;
  subtitle?: string;
  onBack?: () => void;
  right?: React.ReactNode;
}) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.header, { paddingTop: insets.top + spacing.sm }]}>
      <View style={styles.headerRow}>
        {onBack ? (
          <Pressable onPress={onBack} hitSlop={12} style={styles.backBtn} testID="header-back">
            <Ionicons name="chevron-back" size={26} color={colors.onSurface} />
          </Pressable>
        ) : (
          <View style={{ width: 26 }} />
        )}
        <View style={{ flex: 1 }}>
          <Text style={styles.headerTitle} numberOfLines={1}>
            {title}
          </Text>
          {subtitle ? <Text style={styles.headerSub}>{subtitle}</Text> : null}
        </View>
        {right ?? null}
      </View>
    </View>
  );
}

// ---------- Status chip ----------
export function StatusChip({ status, testID }: { status: string; testID?: string }) {
  const c = statusColor(status);
  return (
    <View style={[styles.chip, { backgroundColor: c.bg, borderColor: c.border }]} testID={testID}>
      <Text style={[styles.chipText, { color: c.fg }]}>{status}</Text>
    </View>
  );
}

// ---------- Selectable chip (company gate / filters) ----------
export function FilterChip({
  label,
  active,
  onPress,
  testID,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
  testID?: string;
}) {
  return (
    <Pressable
      onPress={onPress}
      testID={testID}
      style={[
        styles.filterChip,
        {
          backgroundColor: active ? colors.brand : colors.surface2,
          borderColor: active ? colors.brand : colors.border,
        },
      ]}
    >
      <Text
        style={{
          color: active ? colors.onBrand : colors.onSurface2,
          fontWeight: active ? "800" : "600",
          fontSize: font.base,
        }}
      >
        {label}
      </Text>
    </Pressable>
  );
}

// ---------- Button ----------
export function Button({
  title,
  onPress,
  variant = "primary",
  loading,
  disabled,
  icon,
  testID,
  style,
}: {
  title: string;
  onPress: () => void;
  variant?: "primary" | "secondary" | "danger" | "ghost";
  loading?: boolean;
  disabled?: boolean;
  icon?: keyof typeof Ionicons.glyphMap;
  testID?: string;
  style?: ViewStyle;
}) {
  const map: Record<string, { bg: string; fg: string; border: string }> = {
    primary: { bg: colors.brand, fg: colors.onBrand, border: colors.brand },
    secondary: { bg: colors.surface2, fg: colors.onSurface, border: colors.borderStrong },
    danger: { bg: colors.error, fg: colors.onError, border: colors.error },
    ghost: { bg: "transparent", fg: colors.brand, border: "transparent" },
  };
  const c = map[variant];
  const isOff = disabled || loading;
  return (
    <Pressable
      onPress={onPress}
      disabled={isOff}
      testID={testID}
      style={[
        styles.btn,
        { backgroundColor: c.bg, borderColor: c.border, opacity: isOff ? 0.5 : 1 },
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={c.fg} />
      ) : (
        <View style={styles.btnInner}>
          {icon ? <Ionicons name={icon} size={18} color={c.fg} /> : null}
          <Text style={[styles.btnText, { color: c.fg }]}>{title}</Text>
        </View>
      )}
    </Pressable>
  );
}

// ---------- Labeled input ----------
export function Field({
  label,
  ...props
}: { label?: string } & TextInputProps) {
  return (
    <View style={{ gap: spacing.xs, marginBottom: spacing.md }}>
      {label ? <Text style={styles.label}>{label}</Text> : null}
      <TextInput
        placeholderTextColor={colors.info}
        style={styles.input}
        {...props}
      />
    </View>
  );
}

// ---------- Card ----------
export function Card({ children, style, testID }: { children: React.ReactNode; style?: ViewStyle; testID?: string }) {
  return (
    <View style={[styles.card, style]} testID={testID}>
      {children}
    </View>
  );
}

// ---------- Verification badge ----------
export function VerificationBadge({ status }: { status: string }) {
  const verified = status === "Verified";
  return (
    <View style={styles.verifyRow}>
      <Ionicons
        name={verified ? "shield-checkmark" : "alert-circle"}
        size={16}
        color={verified ? colors.success : colors.warning}
      />
      <Text style={{ color: verified ? colors.success : colors.warning, fontWeight: "700", fontSize: font.sm }}>
        {verified ? "Verified" : "Unverified"}
      </Text>
    </View>
  );
}

// ---------- Meter (0-100) ----------
export function Meter({ value, color, label }: { value: number; color: string; label?: string }) {
  const pct = Math.max(0, Math.min(100, value));
  return (
    <View style={{ gap: spacing.xs }}>
      {label ? (
        <View style={styles.meterLabelRow}>
          <Text style={styles.meterLabel}>{label}</Text>
          <Text style={[styles.meterLabel, { color }]}>{pct}%</Text>
        </View>
      ) : null}
      <View style={styles.meterTrack}>
        <View style={[styles.meterFill, { width: `${pct}%`, backgroundColor: color }]} />
      </View>
    </View>
  );
}

// ---------- Purchase limit stacked bar ----------
export function LimitBar({
  existing,
  allowed,
}: {
  existing: number;
  allowed: number | null;
}) {
  if (allowed === null) {
    return (
      <View style={styles.limitOff}>
        <Ionicons name="infinite" size={16} color={colors.info} />
        <Text style={{ color: colors.info, fontSize: font.sm }}>No purchase limit set</Text>
      </View>
    );
  }
  const total = Math.max(allowed, existing, 1);
  const existPct = Math.min(100, (existing / total) * 100);
  const over = existing >= allowed;
  return (
    <View style={{ gap: spacing.xs }}>
      <View style={styles.meterTrack}>
        <View
          style={[
            styles.meterFill,
            { width: `${existPct}%`, backgroundColor: over ? colors.error : colors.brand },
          ]}
        />
      </View>
      <View style={styles.meterLabelRow}>
        <Text style={styles.meterLabel}>Stock: {existing}</Text>
        <Text style={[styles.meterLabel, { color: over ? colors.error : colors.brand }]}>
          Limit: {allowed}
        </Text>
      </View>
    </View>
  );
}

// ---------- Loading / Empty ----------
export function Loading({ text }: { text?: string }) {
  return (
    <View style={styles.center}>
      <ActivityIndicator color={colors.brand} size="large" />
      {text ? <Text style={styles.dim}>{text}</Text> : null}
    </View>
  );
}

export function EmptyState({
  icon = "cube-outline",
  title,
  subtitle,
  action,
}: {
  icon?: keyof typeof Ionicons.glyphMap;
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}) {
  return (
    <View style={styles.center}>
      <Ionicons name={icon} size={56} color={colors.borderStrong} />
      <Text style={styles.emptyTitle}>{title}</Text>
      {subtitle ? <Text style={styles.dim}>{subtitle}</Text> : null}
      {action ? <View style={{ marginTop: spacing.lg }}>{action}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
  },
  headerRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  backBtn: { width: 26 },
  headerTitle: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800", letterSpacing: 0.3 },
  headerSub: { color: colors.info, fontSize: font.sm, marginTop: 1 },
  chip: {
    paddingHorizontal: spacing.md,
    paddingVertical: 5,
    borderRadius: radius.sm,
    borderWidth: 1,
    alignSelf: "flex-start",
  },
  chipText: { fontSize: font.sm, fontWeight: "800", letterSpacing: 0.5 },
  filterChip: {
    height: 36,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.pill,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  btn: {
    height: 52,
    borderRadius: radius.md,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: spacing.lg,
  },
  btnInner: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  btnText: { fontSize: font.lg, fontWeight: "800", letterSpacing: 0.3 },
  label: { color: colors.onSurface3, fontSize: font.sm, fontWeight: "700", letterSpacing: 0.3 },
  input: {
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: 14,
    color: colors.onSurface,
    fontSize: font.lg,
  },
  card: {
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.lg,
  },
  verifyRow: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  meterTrack: {
    height: 12,
    backgroundColor: colors.surface3,
    borderRadius: radius.sm,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: colors.border,
  },
  meterFill: { height: "100%", borderRadius: radius.sm },
  meterLabelRow: { flexDirection: "row", justifyContent: "space-between" },
  meterLabel: { color: colors.onSurface3, fontSize: font.sm, fontWeight: "700" },
  limitOff: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  center: { flex: 1, alignItems: "center", justifyContent: "center", padding: spacing.xl, gap: spacing.sm },
  emptyTitle: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", marginTop: spacing.sm },
  dim: { color: colors.info, fontSize: font.base, textAlign: "center" },
});
