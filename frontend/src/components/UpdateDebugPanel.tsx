import { useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";
import Constants from "expo-constants";
import * as Updates from "expo-updates";

import { colors, font, radius, spacing } from "@/src/theme";

// TEMP diagnostic panel for the OTA-not-applying investigation (URGENT,
// 2026-09-15) — reads expo-updates' runtime constants directly off the
// device, so "what channel/runtime version is THIS install actually on"
// stops being a guess from git history and becomes something visible on
// screen. Placed on the login screen because it renders before auth and
// is confirmed reachable on the affected device. Remove once the OTA
// delivery issue is confirmed fixed.
//
// The "Check For Update Now" button calls checkForUpdateAsync() directly,
// which is the most direct test possible: if it comes back
// isAvailable:false with reason "updateRejectedBySelectionPolicy", an
// update EXISTS on this channel but its runtimeVersion doesn't match this
// build's — a real, confirmed mismatch, not a guess. "noUpdateAvailableOnServer"
// means nothing newer is published to whatever channel this build is on
// (a different problem: wrong channel, or genuinely already current).
export function UpdateDebugPanel() {
  const [checking, setChecking] = useState(false);
  const [checkResult, setCheckResult] = useState<string | null>(null);

  const check = async () => {
    setChecking(true);
    setCheckResult(null);
    try {
      if (!Updates.isEnabled) {
        setCheckResult("expo-updates is DISABLED in this build (Expo Go or a dev client) — this check only works in a real preview/production build.");
        return;
      }
      const result = await Updates.checkForUpdateAsync();
      if (result.isAvailable) {
        const id = (result.manifest as any)?.id ?? "?";
        setCheckResult(`UPDATE AVAILABLE on server — manifest id ${id}`);
      } else if (result.isRollBackToEmbedded) {
        setCheckResult("Server says: roll back to the embedded (built-in) update.");
      } else {
        setCheckResult(`No update available — reason: ${result.reason ?? "unknown"}`);
      }
    } catch (e: any) {
      setCheckResult(`Check failed: ${e?.message || String(e)}`);
    } finally {
      setChecking(false);
    }
  };

  return (
    <View style={styles.box} testID="update-debug-panel">
      <Text style={styles.title}>DEBUG — BUILD INFO</Text>
      <Row label="app.json version" value={String(Constants.expoConfig?.version ?? "?")} />
      <Row label="Runtime version" value={Updates.runtimeVersion ?? "n/a"} />
      <Row label="Channel" value={Updates.channel ?? "n/a (dev build / Expo Go)"} />
      <Row label="Update ID" value={Updates.updateId ?? "n/a (embedded, no OTA applied)"} />
      <Row label="Embedded launch" value={String(Updates.isEmbeddedLaunch)} />
      <Row label="Published at" value={Updates.createdAt ? Updates.createdAt.toISOString() : "n/a"} />
      {Updates.isEmergencyLaunch ? (
        <Row label="⚠ EMERGENCY LAUNCH" value={Updates.emergencyLaunchReason ?? "unknown reason"} />
      ) : null}
      <Pressable style={styles.btn} onPress={check} disabled={checking} testID="update-debug-check">
        {checking ? <ActivityIndicator color={colors.onBrand} /> : <Text style={styles.btnText}>Check For Update Now</Text>}
      </Pressable>
      {checkResult ? (
        <Text style={styles.result} selectable testID="update-debug-result">
          {checkResult}
        </Text>
      ) : null}
    </View>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.value} selectable>
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  box: {
    marginTop: spacing.xl,
    backgroundColor: colors.warningFaint,
    borderWidth: 1,
    borderColor: colors.warning,
    borderRadius: radius.md,
    padding: spacing.md,
    gap: 4,
  },
  title: { color: colors.onWarningFaint, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: 4 },
  row: { flexDirection: "row", justifyContent: "space-between", gap: spacing.sm, flexWrap: "wrap" },
  label: { color: colors.onWarningFaint, fontSize: font.sm - 1, fontWeight: "700" },
  value: { color: colors.onWarningFaint, fontSize: font.sm - 1, flexShrink: 1, textAlign: "right" },
  btn: {
    marginTop: spacing.sm,
    backgroundColor: colors.warning,
    borderRadius: radius.sm,
    paddingVertical: spacing.sm,
    alignItems: "center",
    justifyContent: "center",
  },
  btnText: { color: colors.onWarning, fontWeight: "800", fontSize: font.sm },
  result: { color: colors.onWarningFaint, fontSize: font.sm - 1, marginTop: spacing.xs, fontWeight: "700" },
});
