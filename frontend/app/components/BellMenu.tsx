"use client";
import { useEffect, useRef, useState } from "react";
import { useAlertas } from "@/lib/alertas";

export function BellMenu() {
  const { alertas, toasts, noLeidas, marcarLeidas } = useAlertas();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (
      typeof Notification !== "undefined" &&
      Notification.permission === "default"
    ) {
      Notification.requestPermission();
    }
  }, []);

  useEffect(() => {
    function onOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onOutside);
    return () => document.removeEventListener("mousedown", onOutside);
  }, []);

  const toggle = () => {
    setOpen((v) => !v);
    marcarLeidas();
  };

  return (
    <>
      <div ref={ref} className="relative">
        <button
          onClick={toggle}
          aria-label="Alertas"
          className="relative flex items-center justify-center rounded-lg p-2 text-muted transition hover:bg-panel2 hover:text-white"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
            <path d="M13.73 21a2 2 0 0 1-3.46 0" />
          </svg>
          {noLeidas > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-accent text-xs font-bold text-ink">
              {noLeidas > 9 ? "9+" : noLeidas}
            </span>
          )}
        </button>

        {open && (
          <div className="absolute right-0 top-full z-50 mt-2 w-80 rounded-2xl border border-line bg-panel p-4 shadow-xl">
            <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted">
              Alertas recientes
            </p>
            {alertas.length === 0 ? (
              <p className="text-sm text-muted">Sin alertas en esta sesión.</p>
            ) : (
              <ul className="max-h-64 space-y-2 overflow-y-auto">
                {alertas.slice(0, 20).map((a) => (
                  <li
                    key={a.id}
                    className="border-t border-line pt-2 first:border-t-0 first:pt-0"
                  >
                    <span className="text-xs capitalize text-accent">
                      {a.tipo.replace(/_/g, " ")}
                    </span>
                    <p className="text-sm">{a.mensaje}</p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>

      <div className="pointer-events-none fixed bottom-5 right-5 z-50 flex flex-col items-end gap-2">
        {toasts.map((t) => (
          <div
            key={t.id}
            className="pointer-events-auto w-72 rounded-2xl border border-accent/30 bg-panel p-4 shadow-xl"
          >
            <p className="text-xs capitalize text-accent">
              {t.tipo.replace(/_/g, " ")}
            </p>
            <p className="mt-0.5 text-sm">{t.mensaje}</p>
          </div>
        ))}
      </div>
    </>
  );
}
