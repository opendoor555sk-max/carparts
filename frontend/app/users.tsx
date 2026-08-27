import { useCallback, useState } from "react";
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, Field, Header, Loading, StatusChip } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function Users() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { show } = useToast();
  const [users, setUsers] = useState<any[]>([]);
  const [allPerms, setAllPerms] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState(false);

  // create form
  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [perms, setPerms] = useState<string[]>([]);
  const [creating, setCreating] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState<any>(null);
  const [removing, setRemoving] = useState(false);

  // password reveal + edit
  const [revealed, setRevealed] = useState<Record<string, string>>({});
  const [editUser, setEditUser] = useState<any>(null);
  const [eName, setEName] = useState("");
  const [eUsername, setEUsername] = useState("");
  const [ePassword, setEPassword] = useState("");
  const [savingEdit, setSavingEdit] = useState(false);

  const revealPw = async (u: any) => {
    if (revealed[u.id]) {
      setRevealed((r) => {
        const c = { ...r };
        delete c[u.id];
        return c;
      });
      return;
    }
    try {
      const res = await api.get<{ password: string }>(`/admin/users/${u.id}/password`);
      setRevealed((r) => ({ ...r, [u.id]: res.password }));
    } catch (e: any) {
      show(e?.detail?.message || e?.message || "Password ન મળ્યો", "error");
    }
  };

  const openEdit = (u: any) => {
    setEditUser(u);
    setEName(u.name);
    setEUsername(u.username);
    setEPassword("");
  };

  const saveEdit = async () => {
    if (!editUser) return;
    const body: any = {};
    if (eName.trim() && eName.trim() !== editUser.name) body.name = eName.trim();
    if (eUsername.trim() && eUsername.trim() !== editUser.username) body.username = eUsername.trim();
    if (ePassword) {
      if (ePassword.length < 6) return show("Password ઓછામાં ઓછો 6 અક્ષર", "error");
      body.password = ePassword;
    }
    if (Object.keys(body).length === 0) {
      setEditUser(null);
      return;
    }
    setSavingEdit(true);
    try {
      await api.patch(`/admin/users/${editUser.id}`, body);
      show("Updated ✓", "success");
      setRevealed((r) => {
        const c = { ...r };
        delete c[editUser.id];
        return c;
      });
      setEditUser(null);
      load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    } finally {
      setSavingEdit(false);
    }
  };

  const load = useCallback(async () => {
    try {
      const [u, p] = await Promise.all([api.get("/admin/users"), api.get("/permissions")]);
      setUsers(u);
      setAllPerms(p.all);
      if (perms.length === 0) setPerms(p.staff_default);
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const togglePerm = (perm: string) => {
    setPerms((cur) => (cur.includes(perm) ? cur.filter((x) => x !== perm) : [...cur, perm]));
  };

  const create = async () => {
    if (!name.trim() || !username.trim() || !password) {
      show("બધા fields ભરો", "error");
      return;
    }
    setCreating(true);
    try {
      await api.post("/admin/users", { name, username, password, role: "staff", permissions: perms });
      show("Staff created", "success");
      setModal(false);
      setName("");
      setUsername("");
      setPassword("");
      load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    } finally {
      setCreating(false);
    }
  };

  const toggleDisable = async (u: any) => {
    try {
      await api.patch(`/admin/users/${u.id}`, { disabled: !u.disabled });
      load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    }
  };

  const removeUser = async () => {
    if (!confirmRemove) return;
    setRemoving(true);
    try {
      await api.del(`/admin/users/${confirmRemove.id}`);
      show(`${confirmRemove.name} removed`, "success");
      setConfirmRemove(null);
      load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    } finally {
      setRemoving(false);
    }
  };

  const toggleUserPerm = async (u: any, perm: string) => {
    const next = u.permissions.includes(perm) ? u.permissions.filter((x: string) => x !== perm) : [...u.permissions, perm];
    try {
      await api.patch(`/admin/users/${u.id}`, { permissions: next });
      load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    }
  };

  return (
    <View style={styles.flex}>
      <Header
        title="Manage Users"
        subtitle="Staff & permissions"
        onBack={() => router.back()}
        right={
          <Pressable onPress={() => setModal(true)} style={styles.addBtn} testID="add-user">
            <Ionicons name="person-add" size={18} color={colors.onBrand} />
          </Pressable>
        }
      />
      {loading ? (
        <Loading />
      ) : (
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: insets.bottom + 40 }}>
          {users.map((u) => (
            <Card key={u.id} testID={`user-${u.username}`}>
              <View style={styles.rowBetween}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.name}>{u.name}</Text>
                  <Text style={styles.username}>@{u.username}</Text>
                </View>
                <StatusChip status={u.role === "admin" ? "Verified" : u.disabled ? "Cancelled" : "Pending"} />
              </View>

              {/* Password reveal + edit (Admin) */}
              <View style={styles.pwRow}>
                <View style={styles.pwBox}>
                  <Ionicons name="key-outline" size={15} color={colors.info} />
                  <Text style={styles.pwText} selectable>
                    {revealed[u.id] ? revealed[u.id] : "••••••••"}
                  </Text>
                </View>
                <Pressable style={styles.pwBtn} onPress={() => revealPw(u)} testID={`reveal-${u.username}`}>
                  <Ionicons name={revealed[u.id] ? "eye-off" : "eye"} size={16} color={colors.brand} />
                  <Text style={styles.pwBtnText}>{revealed[u.id] ? "છુપાવો" : "જુઓ"}</Text>
                </Pressable>
                <Pressable style={styles.pwBtn} onPress={() => openEdit(u)} testID={`edit-${u.username}`}>
                  <Ionicons name="create-outline" size={16} color={colors.brand} />
                  <Text style={styles.pwBtnText}>બદલો</Text>
                </Pressable>
              </View>

              {u.role !== "admin" ? (
                <>
                  <Text style={styles.permLabel}>PERMISSIONS (tap to toggle)</Text>
                  <View style={styles.permGrid}>
                    {allPerms.map((perm) => {
                      const on = u.permissions.includes(perm);
                      return (
                        <Pressable
                          key={perm}
                          onPress={() => toggleUserPerm(u, perm)}
                          style={[styles.permChip, { backgroundColor: on ? colors.brandFaint : colors.surface, borderColor: on ? colors.brand : colors.border }]}
                          testID={`perm-${u.username}-${perm}`}
                        >
                          <Text style={{ color: on ? colors.brand : colors.info, fontSize: font.sm - 1, fontWeight: "700" }}>{perm}</Text>
                        </Pressable>
                      );
                    })}
                  </View>
                  <View style={[styles.rowBetween, { marginTop: spacing.md }]}>
                    <Text style={styles.label}>Disabled</Text>
                    <Switch
                      value={!!u.disabled}
                      onValueChange={() => toggleDisable(u)}
                      trackColor={{ true: colors.error, false: colors.surface3 }}
                      thumbColor={colors.onSurface}
                      testID={`disable-${u.username}`}
                    />
                  </View>
                  <Pressable style={styles.removeBtn} onPress={() => setConfirmRemove(u)} testID={`remove-${u.username}`}>
                    <Ionicons name="trash" size={16} color={colors.error} />
                    <Text style={styles.removeText}>Remove user</Text>
                  </Pressable>
                </>
              ) : (
                <Text style={styles.adminNote}>Main Admin — all permissions</Text>
              )}
            </Card>
          ))}
        </ScrollView>
      )}

      <Modal visible={modal} transparent animationType="slide" onRequestClose={() => setModal(false)}>
        <View style={styles.modalWrap}>
          <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined}>
            <View style={styles.modal}>
              <View style={styles.modalHead}>
                <Text style={styles.modalTitle}>New Staff</Text>
                <Pressable onPress={() => setModal(false)} testID="close-user-modal">
                  <Ionicons name="close" size={24} color={colors.onSurface} />
                </Pressable>
              </View>
              <ScrollView keyboardShouldPersistTaps="handled">
                <Field label="Name" value={name} onChangeText={setName} testID="new-user-name" />
                <Field label="Username" value={username} onChangeText={setUsername} autoCapitalize="none" testID="new-user-username" />
                <Field label="Password" value={password} onChangeText={setPassword} secureTextEntry testID="new-user-password" />
                <Text style={styles.permLabel}>PERMISSIONS</Text>
                <View style={styles.permGrid}>
                  {allPerms.map((perm) => {
                    const on = perms.includes(perm);
                    return (
                      <Pressable
                        key={perm}
                        onPress={() => togglePerm(perm)}
                        style={[styles.permChip, { backgroundColor: on ? colors.brandFaint : colors.surface, borderColor: on ? colors.brand : colors.border }]}
                        testID={`newperm-${perm}`}
                      >
                        <Text style={{ color: on ? colors.brand : colors.info, fontSize: font.sm - 1, fontWeight: "700" }}>{perm}</Text>
                      </Pressable>
                    );
                  })}
                </View>
                <Button title="Create Staff" onPress={create} loading={creating} icon="checkmark" testID="create-user" style={{ marginTop: spacing.lg }} />
              </ScrollView>
            </View>
          </KeyboardAvoidingView>
        </View>
      </Modal>

      {/* Edit user modal */}
      <Modal visible={!!editUser} transparent animationType="slide" onRequestClose={() => setEditUser(null)}>
        <View style={styles.modalWrap}>
          <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined}>
            <View style={styles.modal}>
              <View style={styles.modalHead}>
                <Text style={styles.modalTitle}>Username / Password બદલો</Text>
                <Pressable onPress={() => setEditUser(null)} testID="close-edit-modal">
                  <Ionicons name="close" size={24} color={colors.onSurface} />
                </Pressable>
              </View>
              <ScrollView keyboardShouldPersistTaps="handled">
                <Text style={styles.editWho}>{editUser?.name} (@{editUser?.username})</Text>
                <Field label="Name" value={eName} onChangeText={setEName} testID="edit-name" />
                <Field label="Username (login નામ)" value={eUsername} onChangeText={setEUsername} autoCapitalize="none" testID="edit-username" />
                <Field
                  label="New Password (ખાલી રાખો તો બદલાશે નહીં)"
                  value={ePassword}
                  onChangeText={setEPassword}
                  placeholder="નવો password"
                  autoCapitalize="none"
                  testID="edit-password"
                />
                <Button title="Save Changes" onPress={saveEdit} loading={savingEdit} icon="save" testID="save-edit" style={{ marginTop: spacing.lg }} />
              </ScrollView>
            </View>
          </KeyboardAvoidingView>
        </View>
      </Modal>

      {/* Confirm remove */}
      <Modal visible={!!confirmRemove} transparent animationType="fade" onRequestClose={() => setConfirmRemove(null)}>
        <View style={styles.confirmWrap}>
          <View style={styles.confirmBox}>
            <Ionicons name="warning" size={40} color={colors.error} />
            <Text style={styles.confirmTitle}>User remove કરવો છે?</Text>
            <Text style={styles.confirmSub}>
              {confirmRemove?.name} (@{confirmRemove?.username}) ને remove કરાશે. એ login નહીં કરી શકે.
            </Text>
            <View style={styles.confirmRow}>
              <Button title="Cancel" onPress={() => setConfirmRemove(null)} variant="secondary" style={{ flex: 1 }} testID="cancel-remove" />
              <Button title="Remove" onPress={removeUser} loading={removing} variant="danger" style={{ flex: 1 }} testID="confirm-remove" />
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  addBtn: { width: 36, height: 36, borderRadius: radius.sm, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center" },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  name: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  username: { color: colors.info, fontSize: font.sm },
  permLabel: { color: colors.info, fontSize: font.sm - 1, fontWeight: "800", letterSpacing: 1, marginTop: spacing.md, marginBottom: spacing.sm },
  permGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.xs },
  permChip: { paddingHorizontal: spacing.sm, paddingVertical: 6, borderRadius: radius.sm, borderWidth: 1 },
  label: { color: colors.onSurface2, fontSize: font.base, fontWeight: "600" },
  adminNote: { color: colors.brand, fontSize: font.sm, marginTop: spacing.sm, fontWeight: "700" },
  pwRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginTop: spacing.md },
  pwBox: { flex: 1, flexDirection: "row", alignItems: "center", gap: 6, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 8 },
  pwText: { color: colors.onSurface2, fontSize: font.base, fontWeight: "700", letterSpacing: 1 },
  pwBtn: { flexDirection: "row", alignItems: "center", gap: 4, borderWidth: 1, borderColor: colors.brand, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 8 },
  pwBtnText: { color: colors.brand, fontSize: font.sm, fontWeight: "700" },
  editWho: { color: colors.info, fontSize: font.base, fontWeight: "700", marginBottom: spacing.md },
  removeBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: spacing.xs, marginTop: spacing.md, paddingVertical: spacing.sm, borderRadius: radius.sm, borderWidth: 1, borderColor: colors.error },
  removeText: { color: colors.error, fontSize: font.base, fontWeight: "700" },
  confirmWrap: { flex: 1, backgroundColor: "rgba(0,0,0,0.7)", alignItems: "center", justifyContent: "center", padding: spacing.xl },
  confirmBox: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, borderRadius: radius.lg, padding: spacing.xl, alignItems: "center", gap: spacing.sm, width: "100%" },
  confirmTitle: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800", marginTop: spacing.sm },
  confirmSub: { color: colors.info, fontSize: font.base, textAlign: "center", lineHeight: 20 },
  confirmRow: { flexDirection: "row", gap: spacing.md, marginTop: spacing.md, width: "100%" },
  modalWrap: { flex: 1, backgroundColor: "rgba(0,0,0,0.7)", justifyContent: "flex-end" },
  modal: { backgroundColor: colors.surface, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, padding: spacing.lg, maxHeight: "88%", borderWidth: 1, borderColor: colors.border },
  modalHead: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: spacing.lg },
  modalTitle: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800" },
});
