import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./lib/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#0a0e1a",
        panel: "#121829",
        panel2: "#1a2235",
        line: "#252e44",
        muted: "#8a97b4",
        local: "#22d3ee",
        empate: "#a3a3a3",
        visit: "#f472b6",
        accent: "#38bdf8",
        good: "#34d399",
        warn: "#fbbf24",
        bad: "#f87171",
      },
      fontFamily: {
        sans: ["ui-sans-serif", "system-ui", "Segoe UI", "Roboto", "sans-serif"],
      },
    },
  },
  plugins: [],
};

export default config;
