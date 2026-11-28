import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useEffect, useMemo, useState } from 'react';
import { CircleMarker, MapContainer, Marker, TileLayer, Tooltip, useMap } from 'react-leaflet';
import { api, type Stop, type VehiclePosition } from './api';

interface Props {
  agency: string;
  route: string;
  color: string | null;
  stops: Stop[];
  stopId: string | undefined;
  onStop: (stop: Stop) => void;
}

const RALEIGH: L.LatLngTuple = [35.78, -78.64];

/** Fits the map to the stops whenever they change, and redraws when its box resizes (rotation, layout change). */
function FitStops({ stops }: { stops: Stop[] }) {
  const map = useMap();
  useEffect(() => {
    const observer = new ResizeObserver(() => map.invalidateSize());
    observer.observe(map.getContainer());
    return () => observer.disconnect();
  }, [map]);
  useEffect(() => {
    if (stops.length > 0) {
      map.fitBounds(L.latLngBounds(stops.map((s) => [s.latitude, s.longitude])), { padding: [24, 24] });
    }
  }, [map, stops]);
  return null;
}

/** An arrow pointing along the vehicle's bearing; a dot when the feed sends none. */
function vehicleIcon(bearing: number | null): L.DivIcon {
  const shape = bearing === null ? '<span class="vehicle-dot"></span>' : `<span class="vehicle-arrow" style="transform: rotate(${bearing}deg)">▲</span>`;
  return L.divIcon({ className: 'vehicle-icon', html: shape, iconSize: [28, 28], iconAnchor: [14, 14] });
}

/** Live vehicles on a route: one fetch now, then each poll's list from the stream. */
function useVehicles(agency: string, route: string): VehiclePosition[] {
  const [vehicles, setVehicles] = useState<VehiclePosition[]>([]);
  useEffect(() => {
    setVehicles([]);
    const controller = new AbortController();
    api.vehicles(agency, route, controller.signal).then(setVehicles, () => {});
    const stream = new EventSource(api.vehicleStreamUrl(agency, route));
    stream.onmessage = (event) => setVehicles(JSON.parse(event.data) as VehiclePosition[]);
    return () => {
      controller.abort();
      stream.close();
    };
  }, [agency, route]);
  return vehicles;
}

/** Supplementary: everything here is also reachable from the stop list. */
export function RouteMap({ agency, route, color, stops, stopId, onStop }: Props) {
  const vehicles = useVehicles(agency, route);
  const lineColor = color ? `#${color}` : '#0b5cad';
  const icons = useMemo(() => new Map<number | null, L.DivIcon>(), []);
  const icon = (bearing: number | null) => {
    const key = bearing === null ? null : Math.round(bearing / 10) * 10;
    if (!icons.has(key)) icons.set(key, vehicleIcon(key));
    return icons.get(key)!;
  };

  return (
    <section className="map-panel" aria-label="Route map">
      <MapContainer center={RALEIGH} zoom={12} className="map" scrollWheelZoom>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <FitStops stops={stops} />
        {stops.map((s) => (
          <CircleMarker
            key={s.stopId}
            center={[s.latitude, s.longitude]}
            radius={s.stopId === stopId ? 9 : 6}
            pathOptions={{ color: lineColor, weight: 2, fillColor: s.stopId === stopId ? lineColor : '#ffffff', fillOpacity: 1 }}
            eventHandlers={{ click: () => onStop(s) }}
          >
            <Tooltip>{s.name}</Tooltip>
          </CircleMarker>
        ))}
        {vehicles.map((v) => (
          <Marker key={v.vehicleId} position={[v.latitude, v.longitude]} icon={icon(v.bearing)} keyboard={false}>
            <Tooltip>Vehicle {v.vehicleId}</Tooltip>
          </Marker>
        ))}
      </MapContainer>
      {vehicles.length === 0 && <p className="notice map-notice">No live vehicles on this route right now.</p>}
    </section>
  );
}
