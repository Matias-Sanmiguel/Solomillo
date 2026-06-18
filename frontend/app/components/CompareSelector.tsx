"use client";
import { useState } from "react";

type Props<T> = {
  items: T[];
  value: T | null;
  onChange: (item: T) => void;
  label: string;
  getKey: (item: T) => string | number;
  getLabel: (item: T) => string;
};

export function CompareSelector<T>({
  items,
  value,
  onChange,
  label,
  getKey,
  getLabel,
}: Props<T>) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);

  const filtered = query
    ? items.filter((i) =>
        getLabel(i).toLowerCase().includes(query.toLowerCase())
      )
    : items;

  return (
    <div className="relative">
      <p className="mb-1 text-xs text-muted">{label}</p>
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center justify-between rounded-xl border border-line bg-panel2 px-3 py-2 text-sm transition hover:border-accent/50"
      >
        <span>{value ? getLabel(value) : "Elegir…"}</span>
        <span className="text-muted">▾</span>
      </button>

      {open && (
        <div className="absolute left-0 top-full z-50 mt-1 w-full rounded-xl border border-line bg-panel shadow-xl">
          <div className="p-2">
            <input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar…"
              className="w-full rounded-lg bg-panel2 px-3 py-1.5 text-sm outline-none placeholder:text-muted"
            />
          </div>
          <ul className="max-h-52 overflow-y-auto">
            {filtered.map((item) => (
              <li key={getKey(item)}>
                <button
                  onClick={() => {
                    onChange(item);
                    setOpen(false);
                    setQuery("");
                  }}
                  className="w-full px-3 py-2 text-left text-sm transition hover:bg-panel2"
                >
                  {getLabel(item)}
                </button>
              </li>
            ))}
            {filtered.length === 0 && (
              <li className="px-3 py-2 text-sm text-muted">Sin resultados.</li>
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
