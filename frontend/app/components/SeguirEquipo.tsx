"use client";
import { useAlertas } from "@/lib/alertas";

export function SeguirEquipo({ equipoId }: { equipoId: number }) {
  const { sigueA, seguir, dejarDeSeguir } = useAlertas();
  const sigue = sigueA(equipoId);

  return (
    <button
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        sigue ? dejarDeSeguir(equipoId) : seguir(equipoId);
      }}
      className={`chip transition ${
        sigue
          ? "border-accent/60 text-accent"
          : "hover:border-accent/40 hover:text-white"
      }`}
    >
      {sigue ? "★ Siguiendo" : "☆ Seguir"}
    </button>
  );
}
