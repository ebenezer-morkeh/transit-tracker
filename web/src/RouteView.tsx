import type React from 'react';
import { agencyName } from './agencies';
import type { Route, RouteDirection, Stop } from './api';
import { displayHeadsign } from './format';
import { RouteBadge, routeName } from './RouteBadge';

interface Props {
  route: Route | undefined;
  routeId: string;
  agency: string;
  directions: RouteDirection[] | undefined;
  error: boolean;
  direction: RouteDirection | undefined;
  stopId: string | undefined;
  onBack: () => void;
  onDirection: (direction: RouteDirection) => void;
  onStop: (stop: Stop) => void;
}

/** A route's direction buttons and the chosen direction's stops in order. */
export function RouteView({ route, routeId, agency, directions, error, direction, stopId, onBack, onDirection, onStop }: Props) {
  return (
    <section className="route-view" aria-labelledby="route-heading">
      <button type="button" className="back" onClick={onBack}>
        ← All routes
      </button>
      <h2 id="route-heading" className="route-title">
        <RouteBadge route={route ?? { routeId, shortName: null, color: null }} />
        <span>
          {route && routeName(route)}
          <span className="agency-tag">{agencyName(agency)}</span>
        </span>
      </h2>
      {error && <p role="alert">Couldn't load this route's stops. It may no longer exist.</p>}
      {!directions && !error && <p>Loading stops…</p>}
      {directions && directions.length > 1 && (
        <div className="directions" role="group" aria-label="Direction">
          {directions.map((d) => (
            <button
              key={d.directionId ?? 'none'}
              type="button"
              aria-pressed={d === direction}
              onClick={() => onDirection(d)}
            >
              {d.headsign ? `To ${displayHeadsign(d.headsign)}` : `Direction ${d.directionId}`}
            </button>
          ))}
        </div>
      )}
      {direction && (
        <>
          <h3 className="visually-hidden">Stops{direction.headsign ? ` towards ${displayHeadsign(direction.headsign)}` : ''}</h3>
          <ol className="strip-map" style={{ '--route-color': `#${route?.color ?? '1f4aa8'}` } as React.CSSProperties}>
            {direction.stops.map((s) => (
              <li key={s.stopId}>
                <button type="button" aria-current={s.stopId === stopId ? 'location' : undefined} onClick={() => onStop(s)}>
                  {s.name}
                </button>
              </li>
            ))}
          </ol>
        </>
      )}
    </section>
  );
}
