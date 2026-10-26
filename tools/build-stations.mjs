#!/usr/bin/env node
// Genera app/src/main/assets/stations.json a partir del GTFS estático de la CDMX.
//
// Uso:
//   node tools/build-stations.mjs                 # descarga el GTFS oficial
//   node tools/build-stations.mjs ruta/al/gtfs    # usa una carpeta ya descomprimida
//
// Solo toma rutas del Metro (agency_id = METRO), une estaciones de distintas líneas
// que comparten nombre (correspondencias) y guarda el orden de estaciones por línea.

import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const GTFS_URL =
  "https://datos.cdmx.gob.mx/dataset/75538d96-3ade-4bc5-ae7d-d85595e4522d/resource/32ed1b6b-41cd-49b3-b7f0-b57acb0eb819/download/32ed1b6b-41cd-49b3-b7f0-b57acb0eb819.zip";
const AGENCY = "METRO";
const SYSTEM = "Metro";
// Paradas con el mismo nombre a más de esta distancia se consideran estaciones distintas.
const MERGE_RADIUS_M = 800; // Atlalilco (L8–L12) tiene ~640 m entre andenes

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

  const routes = readCsv(dir, "routes.txt").filter((r) => r.agency_id === AGENCY);
  const lines = routes
    .map((r) => {
      const shortName = r.route_short_name.replace(/^L/i, "");
      return {
        id: `metro-${shortName.toLowerCase()}`,
        routeId: r.route_id,
        name: `Línea ${shortName}`,
        shortName,
        color: `#${r.route_color.toUpperCase()}`,
        textColor: `#${(r.route_text_color || "FFFFFF").toUpperCase()}`,
        system: SYSTEM,
        terminals: r.route_long_name,
      };
    })
    .sort((a, b) => sortKey(a.shortName).localeCompare(sortKey(b.shortName)));
  const lineByRoute = new Map(lines.map((l) => [l.routeId, l]));

  // Un viaje representativo por ruta (el que tenga más paradas) define el orden de estaciones.
  const trips = readCsv(dir, "trips.txt").filter((t) => lineByRoute.has(t.route_id));
  const tripRoute = new Map(trips.map((t) => [t.trip_id, t.route_id]));
  const stopsByTrip = new Map();
  for (const st of readCsv(dir, "stop_times.txt")) {
    if (!tripRoute.has(st.trip_id)) continue;
    if (!stopsByTrip.has(st.trip_id)) stopsByTrip.set(st.trip_id, []);
    stopsByTrip.get(st.trip_id).push(st);
  }
  const bestTrip = new Map();
  for (const [tripId, sts] of stopsByTrip) {
    const routeId = tripRoute.get(tripId);
    if (!bestTrip.has(routeId) || sts.length > bestTrip.get(routeId).length) bestTrip.set(routeId, sts);
  }

  const stops = new Map(readCsv(dir, "stops.txt").map((s) => [s.stop_id, s]));

  // Agrupa paradas por nombre normalizado y cercanía.
  const stations = [];
  const stationFor = (stop) => {
    const key = normalizeName(stop.stop_name);
    const lat = +stop.stop_lat, lon = +stop.stop_lon;
    let st = stations.find((s) => s.key === key && s.points.some(([pLat, pLon]) => haversine(pLat, pLon, lat, lon) < MERGE_RADIUS_M));
    if (!st) {
      st = { key, name: tidyName(stop.stop_name), points: [], lineIds: [], accessible: false, gtfsStopIds: [] };
      stations.push(st);
    }
    return st;
  };

  const lineOrder = {};
  for (const line of lines) {
    const sts = (bestTrip.get(line.routeId) || []).slice().sort((a, b) => +a.stop_sequence - +b.stop_sequence);
    const order = [];
    for (const s of sts) {
      const stop = stops.get(s.stop_id);
      if (!stop) continue;
      const st = stationFor(stop);
      if (!st.gtfsStopIds.includes(stop.stop_id)) {
        st.gtfsStopIds.push(stop.stop_id);
        st.points.push([+stop.stop_lat, +stop.stop_lon]);
      }
      if (!st.lineIds.includes(line.id)) st.lineIds.push(line.id);
      if (stop.wheelchair_boarding === "1") st.accessible = true;
      if (order[order.length - 1] !== st) order.push(st);
    }
    lineOrder[line.id] = order;
  }

  // IDs estables y legibles.
  const used = new Set();
  for (const st of stations) {
    let id = slug(st.name);
    while (used.has(id)) id += "-2";
    used.add(id);
    st.id = id;
    st.lat = +(st.points.reduce((a, p) => a + p[0], 0) / st.points.length).toFixed(6);
    st.lon = +(st.points.reduce((a, p) => a + p[1], 0) / st.points.length).toFixed(6);
  }

  const lineIndex = new Map(lines.map((l, i) => [l.id, i]));
  const out = {
    version: new Date().toISOString().slice(0, 10),
    source: "GTFS estático, Portal de Datos Abiertos de la Ciudad de México (datos.cdmx.gob.mx)",
    lines: lines.map(({ routeId, ...l }) => ({ ...l, stationIds: lineOrder[l.id].map((s) => s.id) })),
    stations: stations
      .map((s) => ({
        id: s.id,
        name: s.name,
        lat: s.lat,
        lon: s.lon,
        lineIds: s.lineIds.sort((a, b) => lineIndex.get(a) - lineIndex.get(b)),
        accessible: s.accessible,
        entrances: [],
      }))
      .sort((a, b) => a.name.localeCompare(b.name, "es")),
  };

  writeFileSync(outFile, JSON.stringify(out));
  const transfers = out.stations.filter((s) => s.lineIds.length > 1).length;
  console.log(`OK: ${out.lines.length} líneas, ${out.stations.length} estaciones (${transfers} con correspondencia) -> ${outFile}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
