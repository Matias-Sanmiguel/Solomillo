#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Parsea /tmp/squads.txt (pdftotext -layout -enc UTF-8 del PDF de convocatorias)
y genera planteles2026.json: por seleccion, jugadores (numero, posicion, nombre,
apellido, club) + DT."""
import json, re, sys, unicodedata

POS = {"PO": "Arquero", "DF": "Defensor", "MC": "Mediocampista", "DC": "Delantero"}

# code (FIFA, como aparece en el PDF) -> nombre en selecciones.json
CODE2ES = {
    "ALG": "Argelia", "ARG": "Argentina", "AUS": "Australia", "AUT": "Austria",
    "BEL": "Bélgica", "BIH": "Bosnia y Herzegovina", "BRA": "Brasil", "CAN": "Canadá",
    "CPV": "Cabo Verde", "COL": "Colombia", "COD": "RD Congo", "CRO": "Croacia", "CUW": "Curazao",
    "CUR": "Curazao", "CZE": "República Checa", "ECU": "Ecuador", "EGY": "Egipto",
    "ENG": "Inglaterra", "FRA": "Francia", "GER": "Alemania", "GHA": "Ghana",
    "HAI": "Haití", "IRN": "Irán", "IRQ": "Irak", "CIV": "Costa de Marfil",
    "JPN": "Japón", "JOR": "Jordania", "KOR": "Corea del Sur", "MEX": "México",
    "MAR": "Marruecos", "NED": "Países Bajos", "NZL": "Nueva Zelanda", "NOR": "Noruega",
    "PAN": "Panamá", "PAR": "Paraguay", "POR": "Portugal", "QAT": "Catar",
    "KSA": "Arabia Saudita", "SCO": "Escocia", "SEN": "Senegal", "RSA": "Sudáfrica",
    "ESP": "España", "SWE": "Suecia", "SUI": "Suiza", "TUN": "Túnez",
    "TUR": "Turquía", "USA": "Estados Unidos", "URU": "Uruguay", "UZB": "Uzbekistán",
}

DATE_CLUB = re.compile(r"(\d{1,2}/\d{1,2}/\d{4})\s+(.+?\([A-Z]{3}\))\s+\d")
# El renglon numerado termina en el hueco (2+ espacios) que lo separa del bloque derecho.
NUM_LINE = re.compile(r"^\s*(\d{1,2})\s+(PO|DF|MC|DC)\s+(.+?)(?:\s{2,}|\s*$)")
HEADER = re.compile(r"NOMBRE DEL JUGADOR.*?([A-Za-zÀ-ÿ' .&-]+?)\s*\(([A-Z]{3})\)\s+FN")
# Linea que es SOLO el nombre del equipo + codigo (algunos equipos lo traen en linea aparte).
SOLO_EQUIPO = re.compile(r"^\s*([A-Za-zÀ-ÿ'./&\- ]+?)\s+\(([A-Z]{3})\)\s*$")
DT_LINE = re.compile(r"^Entrenador\s{2,}(.+?)\s{3,}")


def detectar_codigo(pg):
    for ln in pg.split("\n"):
        m = SOLO_EQUIPO.match(ln)
        if m and m.group(2) in CODE2ES:
            return m.group(2)
    m = HEADER.search(pg)
    return m.group(2) if m and m.group(2) in CODE2ES else None


def parse_dt(pg):
    lines = pg.split("\n")
    for i, ln in enumerate(lines):
        if not ln.startswith("Entrenador"):
            continue
        rest = ln[len("Entrenador"):].strip()
        if not rest:  # el nombre quedo en la(s) linea(s) siguiente(s)
            for j in range(i + 1, min(i + 3, len(lines))):
                if lines[j].strip():
                    rest = lines[j].strip(); break
        m = re.match(r"(.+?)(?:\s{3,}|$)", rest)   # primer chunk = "APELLIDO(S) Nombre"
        chunk = (m.group(1) if m else rest).strip()
        toks = chunk.split()
        if len(toks) < 2:
            return titlecase(chunk)
        return titlecase(toks[-1] + " " + " ".join(toks[:-1]))  # Nombre Apellido(s)
    return ""


def titlecase(s):
    return " ".join(w.capitalize() for w in s.split())


def first_upper_group(rightpart):
    """Primer grupo de palabras en MAYUSCULAS = APELLIDO(S) del bloque derecho."""
    toks = rightpart.split()
    out = []
    started = False
    for t in toks:
        letters = [c for c in t if c.isalpha()]
        is_upper = letters and all(c.isupper() for c in letters)
        if is_upper:
            out.append(t); started = True
        elif started:
            break
    return " ".join(out)


def split_num_rest(rest):
    """'HADJ MOUSSA Anis' -> (apellido='HADJ MOUSSA', nombre='Anis')."""
    toks = rest.split()
    ape = []
    i = 0
    while i < len(toks):
        letters = [c for c in toks[i] if c.isalpha()]
        if letters and all(c.isupper() for c in letters):
            ape.append(toks[i]); i += 1
        else:
            break
    nombre = " ".join(toks[i:])
    return " ".join(ape), nombre


def main():
    import os
    raw = os.path.join(os.path.dirname(__file__), "squads_raw.txt")
    text = open(raw, encoding="utf-8").read()
    pages = re.split(r"Page \d+ / 48", text)
    equipos = []
    for pg in pages:
        code = detectar_codigo(pg)
        if not code:
            continue
        nombre_es = CODE2ES[code]

        lines = pg.split("\n")
        # Jugadores numerados (orden 1..26)
        jugadores = []
        for ln in lines:
            m = NUM_LINE.match(ln)
            if not m:
                continue
            num = int(m.group(1))
            pos = POS[m.group(2)]
            ape, nom = split_num_rest(m.group(3))
            jugadores.append({"numero": num, "posicion": pos,
                              "apellido": titlecase(ape), "nombre": titlecase(nom),
                              "_ape_raw": ape.upper()})
        jugadores.sort(key=lambda j: j["numero"])

        # Clubes (orden de aparicion = orden de jugador)
        clubs = []
        for ln in lines:
            mc = DATE_CLUB.search(ln)
            if not mc:
                continue
            rightpart = ln[40:]
            ape = first_upper_group(rightpart)
            clubs.append((ape.upper(), mc.group(2).strip()))

        # Asigna club por indice; verifica apellido como sanity check
        warns = 0
        for i, jug in enumerate(jugadores):
            if i < len(clubs):
                ape_club, club = clubs[i]
                jug["club"] = club
                if ape_club[:4] != jug["_ape_raw"][:4]:
                    warns += 1
            else:
                jug["club"] = ""
            del jug["_ape_raw"]

        dt = parse_dt(pg)

        equipos.append({"nombre": nombre_es, "codigo": code, "dt": dt,
                        "jugadores": jugadores})
        print(f"{code:4} {nombre_es:22} jugadores={len(jugadores):2} clubs={len(clubs):2} "
              f"dt='{dt}' warns={warns}", file=sys.stderr)

    print(f"\nTOTAL equipos={len(equipos)}", file=sys.stderr)
    falt = set(CODE2ES.values()) - {e["nombre"] for e in equipos}
    if falt:
        print("FALTAN:", falt, file=sys.stderr)
    json.dump({"equipos": equipos}, open(sys.argv[1], "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
