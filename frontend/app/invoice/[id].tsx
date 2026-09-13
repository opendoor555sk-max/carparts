import { useCallback, useState } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, EmptyState, Header, Loading } from "@/src/components/ui";
import { brandingFromUser, shareInvoicePdf } from "@/src/utils/print";
import { colors, font, spacing } from "@/src/theme";

function money(v?: number | null): string {
  return v != null ? `₹${v.toFixed(2)}` : "-";
}

export default function InvoiceDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const invoiceId = id as string;
  const router = useRouter();
  const { user } = useAuth();
  const { show } = useToast();

  const [invoice, setInvoice] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [sharing, setSharing] = useState(false);

  const load = useCallback(async () => {
    try {
      setInvoice(await api.get(`/invoices/${encodeURIComponent(invoiceId)}`));
    } catch (e: any) {
      show(e?.message || "Failed to load invoice", "error");
      setInvoice(null);
    } finally {
      setLoading(false);
    }
  }, [invoiceId, show]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const doShare = async () => {
    if (!invoice) return;
    setSharing(true);
    try {
      await shareInvoicePdf(await brandingFromUser(user), invoice);
    } catch (e: any) {
      show(e?.message || "Could not share invoice", "error");
    } finally {
      setSharing(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title="Invoice" onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  if (!invoice) {
    return (
      <View style={styles.flex}>
        <Header title="Invoice" onBack={() => router.back()} />
        <EmptyState icon="receipt-outline" title="Invoice not found" />
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <Header title={invoice.invoice_number} subtitle={new Date(invoice.at).toLocaleString()} onBack={() => router.back()} />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md }}>
        {invoice.customer_name ? (
          <Card>
            <Text style={styles.cardTitle}>CUSTOMER</Text>
            <Text style={styles.value}>{invoice.customer_name}</Text>
          </Card>
        ) : null}

        <Card>
          <Text style={styles.cardTitle}>ITEM</Text>
          <View style={styles.row}>
            <View style={{ flex: 1 }}>
              <Text style={styles.value}>{invoice.part_number}</Text>
              {invoice.description ? <Text style={styles.sub}>{invoice.description}</Text> : null}
            </View>
            <Text style={styles.value}>{money(invoice.price)}</Text>
          </View>
        </Card>

        <Card>
          <View style={styles.row}>
            <Text style={styles.label}>Taxable Amount</Text>
            <Text style={styles.value}>{money(invoice.price)}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>GST ({(invoice.gst_rate * 100).toFixed(0)}%)</Text>
            <Text style={styles.value}>{money(invoice.gst_amount)}</Text>
          </View>
          <View style={[styles.row, styles.totalRow]}>
            <Text style={styles.totalLabel}>Total</Text>
            <Text style={styles.totalValue}>{money(invoice.total)}</Text>
          </View>
        </Card>

        {invoice.price == null ? (
          <Text style={styles.hint}>No price was recorded for this sale, so GST could not be calculated.</Text>
        ) : null}

        <Button title="Share as PDF" onPress={doShare} loading={sharing} icon="share-social" testID="invoice-share" />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.sm },
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingVertical: spacing.xs },
  label: { color: colors.info, fontSize: font.base },
  value: { color: colors.onSurface, fontSize: font.base, fontWeight: "700" },
  sub: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  totalRow: { borderTopWidth: 1, borderTopColor: colors.divider, marginTop: spacing.sm, paddingTop: spacing.md },
  totalLabel: { color: colors.onSurface, fontSize: font.base, fontWeight: "800" },
  totalValue: { color: colors.brand, fontSize: font.lg, fontWeight: "900" },
  hint: { color: colors.info, fontSize: font.sm, textAlign: "center", marginTop: -spacing.sm },
});
