"use client";
import { useEffect, useState } from "react";
import { login, register, me, logout, getToken, ROLES, Sesion } from "@/lib/api";

type Modo = "login" | "registro";

export default function LoginPage() {
  const [sesion, setSesion] = useState<Sesion | null>(null);
  const [cargando, setCargando] = useState(true);
  const [modo, setModo] = useState<Modo>("login");

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [nombre, setNombre] = useState("");
  const [rol, setRol] = useState<string>("usuario_final");

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function refrescar() {
    if (!getToken()) {
      setSesion(null);
      setCargando(false);
      return;
    }
    me()
      .then(setSesion)
      .catch(() => {
        logout();
        setSesion(null);
      })
      .finally(() => setCargando(false));
  }

  useEffect(refrescar, []);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (modo === "login") {
        await login(email, password);
      } else {
        await register(email, nombre, password, rol);
      }
      setEmail("");
      setPassword("");
      setNombre("");
      setCargando(true);
      refrescar();
    } catch (err) {
      const m = String((err as Error).message ?? "");
      setError(mensajeError(m, modo));
    } finally {
      setBusy(false);
    }
  }

  function cerrarSesion() {
    logout();
    setSesion(null);
  }

  if (cargando) return <p className="text-muted">Cargando…</p>;

  if (sesion) {
    return (
      <div className="mx-auto max-w-md">
        <div className="card space-y-4">
          <div>
            <h1 className="text-xl font-bold">Sesión iniciada</h1>
            <p className="text-sm text-muted">Ya estás autenticado en Solomillo.</p>
          </div>
          <dl className="space-y-2 text-sm">
            <Fila k="Nombre" v={sesion.nombre} />
            <Fila k="Email" v={sesion.email} />
            <Fila k="Rol" v={sesion.rol} />
          </dl>
          <button onClick={cerrarSesion} className="btn-ghost w-full">
            Cerrar sesión
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md">
      <div className="card space-y-5">
        <div className="flex gap-1 rounded-xl border border-line bg-panel/60 p-1">
          <Tab activo={modo === "login"} onClick={() => setModo("login")} label="Ingresar" />
          <Tab activo={modo === "registro"} onClick={() => setModo("registro")} label="Crear cuenta" />
        </div>

        <form onSubmit={onSubmit} className="space-y-3">
          {modo === "registro" && (
            <Campo label="Nombre">
              <input
                className="input"
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                placeholder="Tu nombre"
                required
              />
            </Campo>
          )}

          <Campo label="Email">
            <input
              className="input"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="vos@ejemplo.com"
              required
            />
          </Campo>

          <Campo label="Contraseña">
            <input
              className="input"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
              minLength={6}
            />
          </Campo>

          {modo === "registro" && (
            <Campo label="Rol">
              <select className="input" value={rol} onChange={(e) => setRol(e.target.value)}>
                {ROLES.map((r) => (
                  <option key={r} value={r}>
                    {r.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
            </Campo>
          )}

          {error && <p className="text-sm text-visit">{error}</p>}

          <button type="submit" disabled={busy} className="btn w-full">
            {busy ? "Procesando…" : modo === "login" ? "Ingresar" : "Crear cuenta"}
          </button>
        </form>
      </div>
    </div>
  );
}

function mensajeError(m: string, modo: Modo): string {
  if (m.startsWith("401")) return "Credenciales inválidas.";
  if (m.startsWith("409")) return "Ese email ya está registrado.";
  if (m.startsWith("400")) return "Revisá los datos ingresados.";
  return modo === "login" ? "No se pudo iniciar sesión." : "No se pudo crear la cuenta.";
}

function Tab({ activo, onClick, label }: { activo: boolean; onClick: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex-1 rounded-lg px-3 py-1.5 text-sm transition ${
        activo ? "bg-panel2 text-white" : "text-muted hover:text-white"
      }`}
    >
      {label}
    </button>
  );
}

function Campo({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1">
      <span className="text-xs font-medium text-muted">{label}</span>
      {children}
    </label>
  );
}

function Fila({ k, v }: { k: string; v: string }) {
  return (
    <div className="flex justify-between gap-4 border-t border-line pt-2 first:border-t-0 first:pt-0">
      <dt className="text-muted">{k}</dt>
      <dd className="font-semibold">{v}</dd>
    </div>
  );
}
