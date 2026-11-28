import { agencyName } from './agencies';
import type { Route } from './api';
import { routeName } from './RouteBadge';

interface Props {
  routes: Route[] | undefined;
  error: boolean;
  onPick: (route: Route) => void;
}

/** One dropdown per agency; picking a route opens it. Native selects: keyboard and screen-reader friendly on phones. */
export function RouteList({ routes, error, onPick }: Props) {
  const byAgency = new Map<string, Route[]>();
  for (const r of routes ?? []) byAgency.set(r.agencyId, [...(byAgency.get(r.agencyId) ?? []), r]);

  return (
    <section aria-labelledby="routes-heading">
      <h2 id="routes-heading">Choose a route</h2>
      <p className="lede">Pick your route, then your stop, to see when the next buses arrive.</p>
      {error && <p role="alert">Couldn't load routes. Check your connection and reload.</p>}
      {!routes && !error && <p>Loading routes…</p>}
      {routes && routes.length === 0 && <p>No routes yet. Schedules load in the first minute after startup.</p>}
      <div className="agency-pickers">
        {[...byAgency].map(([agencyId, agencyRoutes]) => (
          <div key={agencyId} className="agency-picker">
            <label htmlFor={`routes-${agencyId}`}>{agencyName(agencyId)}</label>
            <select
              id={`routes-${agencyId}`}
              value=""
              onChange={(e) => {
                const route = agencyRoutes.find((r) => r.routeId === e.target.value);
                if (route) onPick(route);
              }}
            >
              <option value="" disabled>
                Choose a route…
              </option>
              {agencyRoutes.map((r) => (
                <option key={r.routeId} value={r.routeId}>
                  {[r.shortName, routeName(r)].filter(Boolean).join(' – ')}
                </option>
              ))}
            </select>
          </div>
        ))}
      </div>
    </section>
  );
}
