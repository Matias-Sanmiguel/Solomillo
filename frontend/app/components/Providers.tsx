"use client";
import { AlertProvider } from "@/lib/alertas";

export function Providers({ children }: { children: React.ReactNode }) {
  return <AlertProvider>{children}</AlertProvider>;
}
