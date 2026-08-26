import { useCallback, useEffect, useState } from "react";
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  LayoutAnimation,
  Platform,
  UIManager,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

if (Platform.OS === "android" && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

type Group = { group: string; items: string[] };

const GROUP_ICON: Record<string, keyof typeof Ionicons.glyphMap> = {
  "Control Modules": "hardware-chip",
  Sensors: "radio",
  "Motors & Actuators": "cog",
  "Switches & Electrical": "flash",
  "Interior / Electronic": "tv",
};

export default function Categories() {
  const router = useRouter();
  const [groups, setGroups] = useState<Group[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState<Record<string, boolean>>({});

  useEffect(() => {
    (async () => {
      try {
        const data = await api.get<{ groups: Group[]; total: number }>("/categories");
        setGroups(data.groups);
        setTotal(data.total);
        setOpen({ [data.groups[0]?.group]: true });
      } catch {
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const toggle = (g: string) => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setOpen((o) => ({ ...o, [g]: !o[g] }));
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title="Category Master" />
        <Loading />
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <Header title="Category Master" subtitle={`${total} items • 5 groups`} />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: spacing.xxxl, gap: spacing.md }}>
        {groups.map((g) => {
          const isOpen = open[g.group];
          return (
            <View key={g.group} style={styles.group}>
              <Pressable style={styles.groupHead} onPress={() => toggle(g.group)} testID={`group-${g.group}`}>
                <View style={styles.groupIcon}>
                  <Ionicons name={GROUP_ICON[g.group] || "cube"} size={20} color={colors.brand} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.groupTitle}>{g.group}</Text>
                  <Text style={styles.groupCount}>{g.items.length} items</Text>
                </View>
                <Ionicons name={isOpen ? "chevron-up" : "chevron-down"} size={20} color={colors.info} />
              </Pressable>
              {isOpen ? (
                <View>
                  {g.items.map((item, i) => (
                    <Pressable
                      key={item}
                      style={[styles.item, i === g.items.length - 1 && { borderBottomWidth: 0 }]}
                      onPress={() =>
                        router.push(
                          `/parts?category=${encodeURIComponent(item)}&title=${encodeURIComponent(item)}` as any,
                        )
                      }
                      testID={`cat-item-${item}`}
                    >
                      <Text style={styles.itemText}>{item}</Text>
                      <Ionicons name="chevron-forward" size={16} color={colors.info} />
                    </Pressable>
                  ))}
                </View>
              ) : null}
            </View>
          );
        })}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  group: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, overflow: "hidden" },
  groupHead: { flexDirection: "row", alignItems: "center", gap: spacing.md, padding: spacing.lg },
  groupIcon: {
    width: 40,
    height: 40,
    borderRadius: radius.sm,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.brandFaint,
    alignItems: "center",
    justifyContent: "center",
  },
  groupTitle: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  groupCount: { color: colors.info, fontSize: font.sm },
  item: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    borderTopWidth: 1,
    borderTopColor: colors.divider,
  },
  itemText: { color: colors.onSurface2, fontSize: font.base, flex: 1 },
});
