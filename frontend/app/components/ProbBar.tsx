import { Probabilidades, pct } from "@/lib/api";

export function ProbBar({ p }: { p: Probabilidades }) {
  const seg = [
    { k: "L", v: p.local, color: "#22d3ee" },
    { k: "E", v: p.empate, color: "#6b7280" },
    { k: "V", v: p.visitante, color: "#f472b6" },
  ];
  return (
    <div>
      <div className="flex h-2.5 w-full overflow-hidden rounded-full bg-panel2">
        {seg.map((s) => (
          <div
            key={s.k}
            style={{ width: `${Math.max(0, s.v * 100)}%`, background: s.color }}
            className="h-full transition-all duration-500"
          />
        ))}
      </div>
      <div className="mt-1.5 flex justify-between text-xs tabular-nums">
        <span className="text-local">{pct(p.local)} local</span>
        <span className="text-muted">{pct(p.empate)} empate</span>
        <span className="text-visit">{pct(p.visitante)} visita</span>
      </div>
    </div>
  );
}

export function favorito(p: Probabilidades): { label: string; conf: number } {
  const opts = [
    { label: "Local", conf: p.local },
    { label: "Empate", conf: p.empate },
    { label: "Visita", conf: p.visitante },
  ];
  return opts.reduce((a, b) => (b.conf > a.conf ? b : a));
}
