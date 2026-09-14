import { Tabs } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { Platform } from "react-native";
import { colors, font } from "@/src/theme";
import { useLowStockCount } from "@/src/hooks/use-low-stock-count";
import { useLanguage } from "@/src/context/LanguageContext";

export default function TabsLayout() {
  const lowStockCount = useLowStockCount();
  const { t } = useLanguage();

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.brand,
        tabBarInactiveTintColor: colors.info,
        tabBarStyle: {
          backgroundColor: colors.surface2,
          borderTopColor: colors.border,
          borderTopWidth: 1,
          height: Platform.OS === "ios" ? 88 : 64,
          paddingBottom: Platform.OS === "ios" ? 28 : 8,
          paddingTop: 8,
        },
        tabBarLabelStyle: { fontSize: font.sm - 1, fontWeight: "700" },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: `${t("tabs.home")} ✅ OTA TEST`,
          tabBarIcon: ({ color, size }) => <Ionicons name="grid" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="inventory"
        options={{
          title: t("tabs.inventory"),
          tabBarIcon: ({ color, size }) => <Ionicons name="cube" size={size} color={color} />,
          tabBarBadge: lowStockCount > 0 ? lowStockCount : undefined,
          tabBarBadgeStyle: { backgroundColor: colors.error },
        }}
      />
      <Tabs.Screen
        name="requirements"
        options={{
          title: t("tabs.needs"),
          tabBarIcon: ({ color, size }) => <Ionicons name="list-circle" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="categories"
        options={{
          title: t("tabs.catalog"),
          tabBarIcon: ({ color, size }) => <Ionicons name="albums" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="admin"
        options={{
          title: t("tabs.admin"),
          tabBarIcon: ({ color, size }) => <Ionicons name="shield" size={size} color={color} />,
        }}
      />
    </Tabs>
  );
}
