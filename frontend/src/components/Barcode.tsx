import { View } from "react-native";
import { SvgXml } from "react-native-svg";

import { barcodeSvg } from "@/src/utils/barcode128";

export function Barcode({ value, height = 70 }: { value: string; height?: number }) {
  if (!value) return null;
  return (
    <View style={{ backgroundColor: "#fff", padding: 8, borderRadius: 8, alignItems: "center" }}>
      <SvgXml xml={barcodeSvg(value, { height })} />
    </View>
  );
}
