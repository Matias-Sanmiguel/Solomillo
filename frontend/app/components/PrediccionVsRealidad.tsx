import { PrediccionAcierto, fmtFecha } from "@/lib/api";
import { ProbBar } from "./ProbBar";
import { Crest } from "./Crest";

export function PrediccionVsRealidad({ items }: { items: PrediccionAcierto[] }) {
  if (items.length === 0) {
    return (
      <p className="text-sm text-muted">
        Aún no hay predicciones con resultado. Entrená el modelo y regenerá el feed para ver los aciertos del modelo.
      </p>
    );
  }
  const aciertos = items.filter((i) => i.acerto).length;
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted">
        El modelo acertó <span className="font-semibold text-good">{aciertos}</span> de{" "}
        <span className="font-semibold text-white">{items.length}</span> resultados.
      </p>
      <div className="grid gap-3 md:grid-cols-2">
        {items.map((i) => (
          <div key={i.partido_id} className="card space-y-3 py-4">
            <div className="flex items-center justify-between">
              <Equipo m={i.local} />
              <div className="px-2 text-center">
                <div className="text-lg font-black tabular-nums">{i.marcador.replace("-", " : ")}</div>
                <div className="text-[10px] uppercase tracking-wide text-muted">{fmtFecha(i.fecha)}</div>
              </div>
              <Equipo m={i.visitante} alineadoDerecha />
            </div>
            <ProbBar p={i.probabilidades} />
            <div
              className={`flex items-center justify-between rounded-lg px-3 py-2 text-sm font-semibold ${
                i.acerto ? "bg-good/15 text-good" : "bg-visit/15 text-visit"
              }`}
            >
              <span>{i.resultado_label}</span>
              <span>{i.acerto ? "✅ El modelo acertó" : "❌ Sorpresa del torneo"}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Equipo({
  m,
  alineadoDerecha = false,
}: {
  m: { nombre: string; escudo: string };
  alineadoDerecha?: boolean;
}) {
  return (
    <div className={`flex min-w-0 flex-1 items-center gap-2 ${alineadoDerecha ? "flex-row-reverse text-right" : ""}`}>
      <Crest src={m.escudo} size={28} />
      <span className="truncate text-sm font-semibold">{m.nombre}</span>
    </div>
  );
}
