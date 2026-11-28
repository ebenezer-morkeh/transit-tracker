import { useCallback, useEffect, useState } from 'react';
import { api, type Route, type RouteDirection } from './api';
import { ArrivalsPanel } from './ArrivalsPanel';
import { RouteList } from './RouteList';
import { RouteMap } from './RouteMap';
import { RouteView } from './RouteView';
import { readSelection, writeSelection, type Selection } from './selection';
import './styles.css';

/** The selection lives in the URL: every change is a history entry, so links and the back button work. */
function useSelection(): [Selection, (next: Selection) => void] {
  const [selection, setSelection] = useState(() => readSelection(window.location.search));
  useEffect(() => {
    const onPop = () => setSelection(readSelection(window.location.search));
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);
  const navigate = useCallback((next: Selection) => {
    window.history.pushState(null, '', writeSelection(next) || window.location.pathname);
    setSelection(next);
  }, []);
  return [selection, navigate];
}

export function App() {
  const [selection, navigate] = useSelection();
  const { agency, route: routeId, stop } = selection;

  const [routes, setRoutes] = useState<Route[]>();
  const [routesError, setRoutesError] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    api.routes(controller.signal).then(setRoutes, (e: Error) => e.name !== 'AbortError' && setRoutesError(true));
    return () => controller.abort();
  }, []);

  const [directions, setDirections] = useState<RouteDirection[]>();
  const [directionsError, setDirectionsError] = useState(false);
  useEffect(() => {
    setDirections(undefined);
    setDirectionsError(false);
    if (!agency || !routeId) return;
    const controller = new AbortController();
    api
      .directions(agency, routeId, controller.signal)
      .then(setDirections, (e: Error) => e.name !== 'AbortError' && setDirectionsError(true));
    return () => controller.abort();
  }, [agency, routeId]);

  const route = routes?.find((r) => r.agencyId === agency && r.routeId === routeId);
  const direction =
    directions?.find((d) => selection.direction !== undefined && d.directionId === selection.direction) ?? directions?.[0];

  return (
    <>
      <header className="site-header">
        <h1>
          <a href={window.location.pathname} onClick={(e) => (e.preventDefault(), navigate({}))}>
            Triangle Bus Times
          </a>
        </h1>
        <p>Live arrivals for GoRaleigh, GoDurham, GoTriangle, Chapel Hill Transit and GoCary</p>
      </header>
      <main className={routeId ? 'layout with-map' : 'layout'}>
        <div className="panel">
          {agency && stop && (
            <ArrivalsPanel agency={agency} stopId={stop} onClose={() => navigate({ ...selection, stop: undefined })} />
          )}
          {agency && routeId ? (
            <RouteView
              route={route}
              routeId={routeId}
              agency={agency}
              directions={directions}
              error={directionsError}
              direction={direction}
              stopId={stop}
              onBack={() => navigate(stop ? { agency, stop } : {})}
              onDirection={(d) => navigate({ agency, route: routeId, direction: d.directionId ?? undefined, stop })}
              onStop={(s) => navigate({ ...selection, direction: direction?.directionId ?? undefined, stop: s.stopId })}
            />
          ) : (
            <RouteList
              routes={routes}
              error={routesError}
              onPick={(r) => navigate({ agency: r.agencyId, route: r.routeId })}
            />
          )}
        </div>
        {agency && routeId && (
          <RouteMap
            agency={agency}
            route={routeId}
            color={route?.color ?? null}
            stops={direction?.stops ?? []}
            stopId={stop}
            onStop={(s) => navigate({ ...selection, direction: direction?.directionId ?? undefined, stop: s.stopId })}
          />
        )}
      </main>
      <footer className="site-footer">
        Schedules and live data from GoRaleigh, GoDurham, GoTriangle, Chapel Hill Transit and GoCary (CC BY).
      </footer>
    </>
  );
}
