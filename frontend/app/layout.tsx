import type { Metadata } from "next";
import "./globals.css";
import { Nav } from "./components/Nav";

export const metadata: Metadata = {
  title: "Solomillo · Predicciones",
  description: "Dashboard de predicciones de fútbol con Elo + ranking FIFA y Machine Learning",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es">
      <body>
        <div className="mx-auto max-w-6xl px-5 py-6">
          <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h1 className="text-xl font-bold tracking-tight">
                Solomillo<span className="text-accent"> · predicciones</span>
              </h1>
              <p className="text-sm text-muted">Elo + ranking FIFA, modelado con Weka</p>
            </div>
            <Nav />
          </header>
          {children}
          <footer className="mt-12 border-t border-line pt-5 text-xs text-muted">
            Modelos entrenados con datos históricos · probabilidades calibradas
          </footer>
        </div>
      </body>
    </html>
  );
}
