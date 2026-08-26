import { Redirect } from "expo-router";
import { View } from "react-native";

import { useAuth } from "@/src/context/AuthContext";
import { Loading } from "@/src/components/ui";
import { colors } from "@/src/theme";

export default function Index() {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.surface }}>
        <Loading />
      </View>
    );
  }
  return <Redirect href={user ? "/(tabs)" : "/login"} />;
}
