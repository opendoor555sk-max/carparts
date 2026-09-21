import { useEffect, useRef } from "react";
import * as Location from "expo-location";
import { Platform } from "react-native";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";

// Sends this device's current GPS position to the backend every
// `intervalMs` while a user is logged in and the app is open -- a single
// "where is this company phone right now" ping, upserted server-side
// (backend/server.py's /device/ping-location) into ONE row per user, never
// a movement history. Piggybacks on the location permission LocationGate
// already requires for the whole app, so this never triggers a separate
// permission prompt of its own. Owner-side visibility (who can see these
// pings, and that they exist at all) is a policy the business owner sets
// and discloses to staff -- this hook only does the sending.
//
// Silently no-ops on web (no native GPS) and on any single failed fetch --
// a missed ping just means a slightly stale "last seen" for that phone,
// never something the staff-facing UI should surface as an error.
export function useLocationPing(intervalMs = 10 * 60 * 1000) {
  const { user } = useAuth();
  const inFlight = useRef(false);

  useEffect(() => {
    if (!user || Platform.OS === "web") return;

    const ping = async () => {
      if (inFlight.current) return;
      inFlight.current = true;
      try {
        const perm = await Location.getForegroundPermissionsAsync();
        if (!perm.granted) return;
        const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        await api.post("/device/ping-location", {
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
        });
      } catch {
        // Best-effort -- a store with GPS off or a dead network shouldn't
        // interrupt anything else the app is doing.
      } finally {
        inFlight.current = false;
      }
    };

    ping();
    const timer = setInterval(ping, intervalMs);
    return () => clearInterval(timer);
  }, [user, intervalMs]);
}
