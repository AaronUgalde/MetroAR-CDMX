#!/usr/bin/env node
// Genera app/src/main/assets/stations.json a partir del GTFS estático de la CDMX.
//
// Uso:
//   node tools/build-stations.mjs                 # descarga el GTFS oficial
//   node tools/build-stations.mjs ruta/al/gtfs    # usa una carpeta ya descomprimida
//
// Toma los sistemas de transporte por estación (Metro, Metrobús, Tren Ligero, Cablebús,
// Tren Suburbano, Tren El Insurgente y Trolebús). Dentro de cada sistema une las paradas
// con el mismo nombre (correspondencias entre líneas); entre sistemas distintos marca como
// conexión las estaciones cercanas. Guarda el orden de estaciones de cada línea.

import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const GTFS_URL =
  "https://datos.cdmx.gob.mx/dataset/75538d96-3ade-4bc5-ae7d-d85595e4522d/resource/32ed1b6b-41cd-49b3-b7f0-b57acb0eb819/download/32ed1b6b-41cd-49b3-b7f0-b57acb0eb819.zip";
// Cada sistema: qué rutas le pertenecen, prefijo de IDs y cómo nombrar sus líneas.
// mergeRadiusM: paradas con el mismo nombre a más de esta distancia son estaciones distintas.
// Las estaciones del Metro conservan IDs sin prefijo para no romper favoritos guardados.
const SYSTEMS = [
  {
    system: "Metro",
    match: (r) => r.agency_id === "METRO",
    prefix: "",
    linePrefix: "metro",
    lineName: (n) => `Línea ${n}`,
    mergeRadiusM: 800, // Atlalilco (L8–L12) tiene ~640 m entre andenes
  },
  {
    system: "Metrobus",
    // Las rutas "SL" son servicios de apoyo temporales, no líneas.
    match: (r) => r.agency_id === "MB" && !/^SL/i.test(r.route_short_name),
    prefix: "mb",
    linePrefix: "mb",
    lineName: (n) => `Metrobús Línea ${n}`,
    mergeRadiusM: 500,
  },
  {
    system: "TrenLigero",
    match: (r) => r.agency_id === "TL",
    prefix: "tl",
    linePrefix: "tl",
    shortName: () => "TL",
    lineName: () => "Tren Ligero",
    mergeRadiusM: 500,
  },
  {
    system: "Cablebus",
    match: (r) => r.agency_id === "CBB",
    prefix: "cb",
    linePrefix: "cb",
    lineName: (n) => `Cablebús Línea ${n}`,
    mergeRadiusM: 500,
  },
  {
    system: "Suburbano",
    match: (r) => r.agency_id === "SUB",
    prefix: "ts",
    linePrefix: "ts",
    shortName: () => "S1",
    lineName: () => "Tren Suburbano",
    mergeRadiusM: 500,
  },
  {
    system: "Interurbano",
    match: (r) => r.agency_id === "INTERURBANO",
    prefix: "ti",
    linePrefix: "ti",
    shortName: () => "EI",
    lineName: () => "Tren El Insurgente",
    mergeRadiusM: 500,
  },
  {
    system: "Trolebus",
    // La Línea 13 viene publicada por SEMOVI con route_type 11 (trolebús).
    match: (r) => r.agency_id === "TROLE" || (r.agency_id === "SEMOVI" && r.route_type === "11"),
    prefix: "tb",
    linePrefix: "tb",
    lineName: (n) => `Trolebús Línea ${n}`,
    mergeRadiusM: 400,
  },
];
// Estaciones de sistemas distintos a menos de esta distancia se marcan como conexión.
const CONNECTION_RADIUS_M = 350;

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const outFile = join(root, "app/src/main/assets/stations.json");

async function obtainGtfsDir() {
  const arg = process.argv[2];
  if (arg) return arg;
  const dir = mkdtempSync(join(tmpdir(), "gtfs-cdmx-"));
  const zip = join(dir, "gtfs.zip");
  console.log(`Descargando ${GTFS_URL}`);
  const res = await fetch(GTFS_URL);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  writeFileSync(zip, Buffer.from(await res.arrayBuffer()));
  execFileSync("unzip", ["-o", "-q", zip, "-d", join(dir, "x")]);
  return join(dir, "x");
}

function parseCsv(text) {
  const rows = [];
  let row = [], field = "", quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') quoted = false;
      else field += c;
    } else if (c === '"') quoted = true;
    else if (c === ",") { row.push(field); field = ""; }
    else if (c === "\n" || c === "\r") {
      if (c === "\r" && text[i + 1] === "\n") i++;
      row.push(field); field = "";
      if (row.length > 1 || row[0] !== "") rows.push(row);
      row = [];
    } else field += c;
  }
  if (field !== "" || row.length) { row.push(field); rows.push(row); }
  const header = rows.shift().map((h) => h.replace(/^﻿/, "").trim());
  return rows.map((r) => Object.fromEntries(header.map((h, i) => [h, (r[i] ?? "").trim()])));
}

const readCsv = (dir, name) => parseCsv(readFileSync(join(dir, name), "utf8"));

function haversine(lat1, lon1, lat2, lon2) {
  const R = 6371000, rad = Math.PI / 180;
  const dPhi = (lat2 - lat1) * rad, dLam = (lon2 - lon1) * rad;
  const a = Math.sin(dPhi / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLam / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

const normalizeName = (s) =>
  s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();

const slug = (s) => normalizeName(s).replace(/ /g, "-");

// "Indios verdes" -> "Indios Verdes", sin tocar preposiciones cortas.
const SMALL = new Set(["de", "del", "la", "las", "los", "y", "el"]);
const tidyName = (s) =>
  s.split(" ").map((w, i) => (i > 0 && SMALL.has(w.toLowerCase()) ? w.toLowerCase() : w.charAt(0).toUpperCase() + w.slice(1))).join(" ");

const sortKey = (shortName) => (/^\d+$/.test(shortName) ? shortName.padStart(3, "0") : `z${shortName}`);

async function main() {
  const dir = await obtainGtfsDir();
  if (!existsSync(join(dir, "stops.txt"))) throw new Error(`No hay stops.txt en ${dir}`);

  const allRoutes = readCsv(dir, "routes.txt");
  const systemIndex = new Map(SYSTEMS.map((c, i) => [c.system, i]));

  // Una línea por (sistema, número). Si un número tiene varias rutas, se juntan.
  const linesById = new Map();
  const lineOfRoute = new Map();
  for (const r of allRoutes) {
    const cfg = SYSTEMS.find((c) => c.match(r));
    if (!cfg) continue;
    const number = r.route_short_name.replace(/^L/i, "");
    const shortName = cfg.shortName ? cfg.shortName(number) : number;
    const id = `${cfg.linePrefix}-${shortName.toLowerCase()}`;
    if (!linesById.has(id)) {
      linesById.set(id, {
        id,
        name: cfg.lineName(number),
        shortName,
        color: `#${(r.route_color || "777777").toUpperCase()}`,
        textColor: `#${(r.route_text_color || "FFFFFF").toUpperCase()}`,
        system: cfg.system,
        terminals: r.route_long_name.replace(/\s+/g, " ").trim(),
        cfg,
        routeIds: [],
      });
    }
    linesById.get(id).routeIds.push(r.route_id);
    lineOfRoute.set(r.route_id, linesById.get(id));
  }
  const lines = [...linesById.values()].sort(
    (a, b) =>
      systemIndex.get(a.system) - systemIndex.get(b.system) || sortKey(a.shortName).localeCompare(sortKey(b.shortName)),
  );

  // Un viaje representativo por línea (el que tenga más paradas) define el orden de estaciones.
  const trips = readCsv(dir, "trips.txt").filter((t) => lineOfRoute.has(t.route_id));
  const tripLine = new Map(trips.map((t) => [t.trip_id, lineOfRoute.get(t.route_id)]));
  const stopsByTrip = new Map();
  for (const st of readCsv(dir, "stop_times.txt")) {
    if (!tripLine.has(st.trip_id)) continue;
    if (!stopsByTrip.has(st.trip_id)) stopsByTrip.set(st.trip_id, []);
    stopsByTrip.get(st.trip_id).push(st);
  }
  const bestTrip = new Map();
  for (const [tripId, sts] of stopsByTrip) {
    const line = tripLine.get(tripId);
    if (!bestTrip.has(line.id) || sts.length > bestTrip.get(line.id).length) bestTrip.set(line.id, sts);
  }
  // Paradas de todos los viajes de la línea: los viajes de ida y vuelta usan paradas distintas.
  const lineStops = new Map();
  for (const [tripId, sts] of stopsByTrip) {
    const line = tripLine.get(tripId);
    if (!lineStops.has(line.id)) lineStops.set(line.id, new Set());
    sts.forEach((x) => lineStops.get(line.id).add(x.stop_id));
  }

  const stops = new Map(readCsv(dir, "stops.txt").map((s) => [s.stop_id, s]));

  // Agrupa paradas del mismo sistema por nombre normalizado y cercanía.
  const stations = [];
  const stationFor = (stop, cfg) => {
    const key = normalizeName(stop.stop_name);
    const lat = +stop.stop_lat, lon = +stop.stop_lon;
    let st = stations.find(
      (s) =>
        s.system === cfg.system &&
        s.key === key &&
        s.points.some(([pLat, pLon]) => haversine(pLat, pLon, lat, lon) < cfg.mergeRadiusM),
    );
    if (!st) {
      st = { key, system: cfg.system, cfg, name: tidyName(stop.stop_name), points: [], lineIds: [], accessible: false, gtfsStopIds: [] };
      stations.push(st);
    }
    return st;
  };
  const addStop = (st, stop, line) => {
    if (!st.gtfsStopIds.includes(stop.stop_id)) {
      st.gtfsStopIds.push(stop.stop_id);
      st.points.push([+stop.stop_lat, +stop.stop_lon]);
    }
    if (!st.lineIds.includes(line.id)) st.lineIds.push(line.id);
    if (stop.wheelchair_boarding === "1") st.accessible = true;
  };

  const lineOrder = {};
  for (const line of lines) {
    const sts = (bestTrip.get(line.id) || []).slice().sort((a, b) => +a.stop_sequence - +b.stop_sequence);
    const order = [];
    for (const s of sts) {
      const stop = stops.get(s.stop_id);
      if (!stop) continue;
      const st = stationFor(stop, line.cfg);
      addStop(st, stop, line);
      if (order[order.length - 1] !== st) order.push(st);
    }
    // Paradas que solo aparecen en el sentido contrario.
    for (const stopId of lineStops.get(line.id) || []) {
      const stop = stops.get(stopId);
      if (stop) addStop(stationFor(stop, line.cfg), stop, line);
    }
    lineOrder[line.id] = order;
  }

  // IDs estables y legibles; coordenadas = promedio de sus andenes.
  const used = new Set();
  for (const st of stations) {
    const base = st.cfg.prefix ? `${st.cfg.prefix}-${slug(st.name)}` : slug(st.name);
    let id = base;
    for (let n = 2; used.has(id); n++) id = `${base}-${n}`;
    used.add(id);
    st.id = id;
    st.lat = +(st.points.reduce((a, p) => a + p[0], 0) / st.points.length).toFixed(6);
    st.lon = +(st.points.reduce((a, p) => a + p[1], 0) / st.points.length).toFixed(6);
  }

  // Conexiones entre sistemas: estaciones cercanas de otro sistema.
  for (const st of stations) {
    st.connections = stations
      .filter((o) => o.system !== st.system && haversine(st.lat, st.lon, o.lat, o.lon) < CONNECTION_RADIUS_M)
      .sort((a, b) => haversine(st.lat, st.lon, a.lat, a.lon) - haversine(st.lat, st.lon, b.lat, b.lon))
      .map((o) => o.id);
  }

  const lineIndex = new Map(lines.map((l, i) => [l.id, i]));
  const out = {
    version: new Date().toISOString().slice(0, 10),
    source: "GTFS estático, Portal de Datos Abiertos de la Ciudad de México (datos.cdmx.gob.mx)",
    // Líneas sin paradas publicadas (p. ej. Trolebús 13 en el GTFS actual) se omiten.
    lines: lines
      .filter((l) => lineOrder[l.id].length > 0)
      .map(({ routeIds, cfg, ...l }) => ({ ...l, stationIds: lineOrder[l.id].map((s) => s.id) })),
    stations: stations
      .filter((s) => s.lineIds.length > 0)
      .map((s) => ({
        id: s.id,
        name: s.name,
        system: s.system,
        lat: s.lat,
        lon: s.lon,
        lineIds: s.lineIds.sort((a, b) => lineIndex.get(a) - lineIndex.get(b)),
        connections: s.connections,
        accessible: s.accessible,
        entrances: [],
      }))
      .sort((a, b) => a.name.localeCompare(b.name, "es") || systemIndex.get(a.system) - systemIndex.get(b.system)),
  };

  writeFileSync(outFile, JSON.stringify(out));
  for (const cfg of SYSTEMS) {
    const sts = out.stations.filter((s) => s.system === cfg.system);
    const nLines = out.lines.filter((l) => l.system === cfg.system).length;
    const transfers = sts.filter((s) => s.lineIds.length > 1).length;
    console.log(`${cfg.system.padEnd(12)} ${String(nLines).padStart(2)} líneas  ${String(sts.length).padStart(3)} estaciones  ${transfers} con correspondencia`);
  }
  const connected = out.stations.filter((s) => s.connections.length > 0).length;
  console.log(`OK: ${out.lines.length} líneas, ${out.stations.length} estaciones, ${connected} con conexión a otro sistema -> ${outFile}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
