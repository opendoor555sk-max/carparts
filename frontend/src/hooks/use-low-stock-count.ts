import { useEffect, useState } from "react";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";

// Polls GET /inventory/low-stock and returns how many parts are currently at
// or below their configured threshold. Shared by the Inventory tab badge and
// the Home screen summary banner so both stay in sync off one fetch pattern.
export function useLowStockCount(pollMs = 60000): number {
  const { user } = useAuth();
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!user) {
      setCount(0);
      return;
    }
    let active = true;
    const load = async () => {
      try {
        const rows = await api.get<any[]>("/inventory/low-stock");
        if (active) setCount(rows.length);
      } catch {}
    };
    load();
    const timer = setInterval(load, pollMs);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [user, pollMs]);

  return count;
}
