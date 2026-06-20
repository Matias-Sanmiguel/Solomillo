import { CategoriaNoticia } from "@/lib/api";

export function CategoriaFiltros({
  categorias,
  value,
  total,
  onChange,
}: {
  categorias: CategoriaNoticia[];
  value: string | null;
  total: number;
  onChange: (v: string | null) => void;
}) {
  return (
    <div className="flex flex-wrap gap-2">
      <Chip activo={value === null} onClick={() => onChange(null)} label="Todas" cantidad={total} />
      {categorias
        .filter((c) => c.cantidad > 0)
        .map((c) => (
          <Chip
            key={c.nombre}
            activo={value === c.nombre}
            onClick={() => onChange(c.nombre)}
            label={c.label}
            cantidad={c.cantidad}
          />
        ))}
    </div>
  );
}

function Chip({
  activo,
  onClick,
  label,
  cantidad,
}: {
  activo: boolean;
  onClick: () => void;
  label: string;
  cantidad: number;
}) {
  return (
    <button
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs transition ${
        activo
          ? "border-accent bg-accent/15 text-white"
          : "border-line bg-panel2 text-muted hover:text-white"
      }`}
    >
      {label}
      <span className="tabular-nums opacity-60">{cantidad}</span>
    </button>
  );
}
