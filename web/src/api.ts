/** JSON shapes of the backend API (see /swagger-ui.html) and the calls the app makes. */

export interface Route {
  agencyId: string;
  routeId: string;
  shortName: string | null;
  longName: string | null;
  color: string | null;
  textColor: string | null;
}

export interface Stop {
  stopId: string;
  name: string;
  latitude: number;
  longitude: number;
}

export interface RouteDirection {
  directionId: number | null;
  headsign: string | null;
  stops: Stop[];
}

export interface Arrival {
  tripId: string;
  headsign: string | null;
  expectedTime: string;
  scheduledTime: string;
  live: boolean;
  delaySeconds: number | null;
}

export interface RouteArrivals {
  routeId: string;
  shortName: string | null;
  color: string | null;
  textColor: string | null;
  arrivals: Arrival[];
}

export interface StopArrivals {
  agencyId: string;
  stopId: string;
  name: string;
  latitude: number;
  longitude: number;
  liveDataUnavailable: boolean;
  routes: RouteArrivals[];
}

export interface VehiclePosition {
  agencyId: string;
  vehicleId: string;
  tripId: string | null;
  routeId: string | null;
  latitude: number;
  longitude: number;
  bearing: number | null;
  speed: number | null;
}

async function getJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(url, { signal });
  if (!response.ok) throw new Error(`${url} answered ${response.status}`);
  return response.json() as Promise<T>;
}

const part = encodeURIComponent;

export const api = {
  routes: (signal?: AbortSignal) => getJson<Route[]>('/api/routes', signal),
  directions: (agency: string, route: string, signal?: AbortSignal) =>
    getJson<RouteDirection[]>(`/api/agencies/${part(agency)}/routes/${part(route)}/stops`, signal),
  arrivals: (agency: string, stop: string, signal?: AbortSignal) =>
    getJson<StopArrivals>(`/api/agencies/${part(agency)}/stops/${part(stop)}/arrivals`, signal),
  vehicles: (agency: string, route: string, signal?: AbortSignal) =>
    getJson<VehiclePosition[]>(`/api/vehicles?agency=${part(agency)}&route=${part(route)}`, signal),
  vehicleStreamUrl: (agency: string, route: string) =>
    `/api/vehicles/stream?agency=${part(agency)}&route=${part(route)}`,
};
