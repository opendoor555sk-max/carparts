import { useState } from "react";
import { Alert, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as FileSystem from "expo-file-system/legacy";
import * as Sharing from "expo-sharing";
import * as DocumentPicker from "expo-document-picker";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, Header } from "@/src/components/ui";
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
      show("Backup (JSON) તૈયાર ✓", "success");
    } catch (e: any) {
      show(e?.message || "Export નિષ્ફળ", "error");
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
      show("Excel export તૈયાર ✓", "success");
    } catch (e: any) {
      show(e?.message || "Excel export નિષ્ફળ", "error");
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
      Alert.alert(
        "Restore કરવું?",
        "આ backup ના data ને app માં પાછો ઉમેરાશે (merge/restore). ચાલુ રાખવું?",
        [
          { text: "રદ કરો", style: "cancel" },
          {
            text: "Restore",
            onPress: async () => {
              setBusy("import");
              try {
                const res = await api.post("/backup/import", { collections });
                const total = Object.values(res.imported || {}).reduce((a: number, b: any) => a + b, 0);
                show(`${total} records restore થયા ✓`, "success");
              } catch (e: any) {
                show(e?.message || "Import નિષ્ફળ", "error");
              } finally {
                setBusy(null);
              }
            },
          },
        ],
      );
    } catch {
      show("File વાંચવામાં ભૂલ — યોગ્ય backup file પસંદ કરો", "error");
    }
  };

  return (
    <View style={styles.flex}>
      <Header title="Backup & Restore" subtitle="Data export / import" onBack={() => router.back()} />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: insets.bottom + 40 }}>
        <View style={styles.info}>
          <Ionicons name="shield-checkmark" size={20} color={colors.success} />
          <Text style={styles.infoText}>
            તમારો બધો data <Text style={{ fontWeight: "800", color: colors.onSurface }}>secure cloud database</Text> પર
            save થાય છે. Mobile ખોવાય/crash થાય તોય data સલામત રહે. છતાં નીચેથી backup file download કરી રાખો.
          </Text>
        </View>

        <Card>
          <Text style={styles.cardTitle}>EXPORT (BACKUP)</Text>
          <Text style={styles.sub}>આખો data એક file માં download/share કરો.</Text>
          <Button
            title="Excel Backup (વાંચવા સહેલું)"
            icon="grid"
            onPress={exportExcel}
            loading={busy === "excel"}
            testID="export-excel"
            style={{ marginTop: spacing.md }}
          />
          <Button
            title="Full Backup (JSON — restore માટે)"
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
          <Text style={styles.sub}>અગાઉ ના JSON backup થી data પાછો લાવો.</Text>
          <Button
            title="Backup File પસંદ કરી Restore કરો"
            icon="cloud-upload"
            variant="secondary"
            onPress={runImport}
            loading={busy === "import"}
            testID="import-json"
            style={{ marginTop: spacing.md }}
          />
        </Card>
      </ScrollView>
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
