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
          title: t("tabs.home"),
          tabBarIcon: ({ color, size }) => <Ionicons name="grid" size={size} color={color} />,
        }}
      />
      {/* Inventory kept as a real, navigable route (still linked from Home,
          the low-stock banner, and the Reports tab overview screen) but no
          longer its own bottom-tab button — same href: null pattern used
          for Requirements/Needs below, now redundant here too since
          Inventory is already one tap away from the Reports tab. */}
      <Tabs.Screen
        name="inventory"
        options={{
          title: t("tabs.inventory"),
          tabBarIcon: ({ color, size }) => <Ionicons name="cube" size={size} color={color} />,
          tabBarBadge: lowStockCount > 0 ? lowStockCount : undefined,
          tabBarBadgeStyle: { backgroundColor: colors.error },
          href: null,
        }}
      />
      <Tabs.Screen
        name="reports"
        options={{
          title: t("tabs.reports"),
          tabBarIcon: ({ color, size }) => <Ionicons name="bar-chart" size={size} color={color} />,
        }}
      />
      {/* Requirements/Needs kept as a real, navigable route (still linked
          from Home, admin.tsx, requirement-new.tsx, and the new Reports
          tab above) but no longer its own bottom-tab button — href: null
          hides it from the tab bar without removing the route, keeping
          the bar at 5 tabs instead of 6. */}
      <Tabs.Screen
        name="requirements"
        options={{
          title: t("tabs.needs"),
          tabBarIcon: ({ color, size }) => <Ionicons name="list-circle" size={size} color={color} />,
          href: null,
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
