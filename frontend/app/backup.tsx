import { useState } from "react";
import { Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as FileSystem from "expo-file-system/legacy";
import * as Sharing from "expo-sharing";
import * as DocumentPicker from "expo-document-picker";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, ConfirmModal, Header } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

function webDownload(data: Blob | string, filename: string, mime: string) {
  const blob = typeof data === "string" ? new Blob([data], { type: mime }) : data;
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

export default function Backup() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { show } = useToast();
  const [busy, setBusy] = useState<string | null>(null);
  const [pendingImport, setPendingImport] = useState<any | null>(null);

  const stamp = () => new Date().toISOString().slice(0, 10);

  const exportJson = async () => {
    setBusy("json");
    try {
      const data = await api.get("/backup/export");
      const str = JSON.stringify(data, null, 2);
      const filename = `kabadi_backup_${stamp()}.json`;
      if (Platform.OS === "web") {
        webDownload(str, filename, "application/json");
      } else {
        const uri = FileSystem.documentDirectory + filename;
        await FileSystem.writeAsStringAsync(uri, str);
        if (await Sharing.isAvailableAsync()) await Sharing.shareAsync(uri, { mimeType: "application/json" });
      }
      show("Backup (JSON) ready ✓", "success");
    } catch (e: any) {
      show(e?.message || "Export failed", "error");
    } finally {
      setBusy(null);
    }
  };

  const exportExcel = async () => {
    setBusy("excel");
    try {
      const token = await api.getToken();
      const url = `${api.base}/api/backup/excel`;
      const filename = `kabadi_backup_${stamp()}.xlsx`;
      if (Platform.OS === "web") {
        const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
        const blob = await res.blob();
        webDownload(blob, filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      } else {
        const uri = FileSystem.documentDirectory + filename;
        const dl = await FileSystem.downloadAsync(url, uri, { headers: { Authorization: `Bearer ${token}` } });
        if (await Sharing.isAvailableAsync()) await Sharing.shareAsync(dl.uri);
      }
      show("Excel export ready ✓", "success");
    } catch (e: any) {
      show(e?.message || "Excel export failed", "error");
    } finally {
      setBusy(null);
    }
  };

  const runImport = async () => {
    try {
      const picked = await DocumentPicker.getDocumentAsync({ type: "application/json", copyToCacheDirectory: true });
      if (picked.canceled || !picked.assets?.[0]) return;
      const asset = picked.assets[0];
      let content = "";
      if (Platform.OS === "web") {
        content = await (await fetch(asset.uri)).text();
      } else {
        content = await FileSystem.readAsStringAsync(asset.uri);
      }
      const parsed = JSON.parse(content);
      const collections = parsed.collections || parsed;
      setPendingImport(collections);
    } catch {
      show("Error reading file — pick a valid backup file", "error");
    }
  };

  const confirmImport = async () => {
    if (!pendingImport) return;
    setBusy("import");
    try {
      const res = await api.post("/backup/import", { collections: pendingImport });
      const total = Object.values(res.imported || {}).reduce((a: number, b: any) => a + b, 0);
      show(`${total} records restored ✓`, "success");
      setPendingImport(null);
    } catch (e: any) {
      show(e?.message || "Import failed", "error");
    } finally {
      setBusy(null);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title="Backup & Restore" subtitle="Data export / import" onBack={() => router.back()} />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: insets.bottom + 40 }}>
        <View style={styles.info}>
          <Ionicons name="shield-checkmark" size={20} color={colors.success} />
          <Text style={styles.infoText}>
            All your data is saved on a <Text style={{ fontWeight: "800", color: colors.onSurface }}>secure cloud database</Text>.
            Even if your mobile is lost/crashes, the data stays safe. Still, download a backup file below to be sure.
          </Text>
        </View>

        <Card>
          <Text style={styles.cardTitle}>EXPORT (BACKUP)</Text>
          <Text style={styles.sub}>Download/share all data in one file.</Text>
          <Button
            title="Excel Backup (easy to read)"
            icon="grid"
            onPress={exportExcel}
            loading={busy === "excel"}
            testID="export-excel"
            style={{ marginTop: spacing.md }}
          />
          <Button
            title="Full Backup (JSON — for restore)"
            icon="download"
            variant="secondary"
            onPress={exportJson}
            loading={busy === "json"}
            testID="export-json"
            style={{ marginTop: spacing.sm }}
          />
        </Card>

        <Card>
          <Text style={styles.cardTitle}>IMPORT (RESTORE)</Text>
          <Text style={styles.sub}>Restore data from a previous JSON backup.</Text>
          <Button
            title="Pick Backup File & Restore"
            icon="cloud-upload"
            variant="secondary"
            onPress={runImport}
            loading={busy === "import"}
            testID="import-json"
            style={{ marginTop: spacing.md }}
          />
        </Card>
      </ScrollView>

      <ConfirmModal
        visible={!!pendingImport}
        title="Restore?"
        message="This backup data will be added back into the app (merge/restore). Continue?"
        confirmText="Restore"
        loading={busy === "import"}
        onConfirm={confirmImport}
        onCancel={() => setPendingImport(null)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  info: { flexDirection: "row", gap: spacing.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md },
  infoText: { color: colors.onSurface3, fontSize: font.sm, flex: 1, lineHeight: 19 },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1 },
  sub: { color: colors.onSurface3, fontSize: font.sm, marginTop: spacing.xs },
});
