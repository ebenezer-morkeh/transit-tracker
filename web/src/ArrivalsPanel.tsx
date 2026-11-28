import { useEffect, useRef, useState } from 'react';
import { agencyName } from './agencies';
import { api, type StopArrivals } from './api';
import { arrivalStatus, clockTime, displayHeadsign, minutesAway } from './format';
import { RouteBadge } from './RouteBadge';

const REFRESH_MS = 15_000; // one poll cycle
const TICK_MS = 5_000; // recount "N min" between refreshes

interface Props {
  agency: string;
  stopId: string;
  onClose: () => void;
}

/** A stop's next arrivals by route, refreshed every poll cycle. Plain text lists: usable without the map. */
export function ArrivalsPanel({ agency, stopId, onClose }: Props) {
  const [data, setData] = useState<StopArrivals>();
  const [updatedAt, setUpdatedAt] = useState<Date>();
  const [error, setError] = useState<'not-found' | 'failed'>();
  const heading = useRef<HTMLHeadingElement>(null);
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), TICK_MS);
    return () => window.clearInterval(timer);
  }, []);

  // Move focus here when the rider picks a stop, not on refreshes.
  useEffect(() => heading.current?.focus(), [stopId]);

  useEffect(() => {
    setData(undefined);
    setError(undefined);
    let controller = new AbortController();
    const load = () => {
      controller.abort();
      controller = new AbortController();
      api
        .arrivals(agency, stopId, controller.signal)
        .then((result) => {
          setData(result);
          setUpdatedAt(new Date());
          setError(undefined);
        })
        .catch((e: Error) => {
          if (e.name === 'AbortError') return;
          setError(e.message.endsWith('404') ? 'not-found' : 'failed');
        });
    };
    load();
    const timer = window.setInterval(load, REFRESH_MS);
    return () => {
      window.clearInterval(timer);
      controller.abort();
    };
  }, [agency, stopId]);

  return (
    <section className="arrivals" aria-labelledby="arrivals-heading">
      <div className="arrivals-header">
        <h2 id="arrivals-heading" ref={heading} tabIndex={-1}>{data ? data.name : 'Arrivals'}</h2>
        <button type="button" className="close" onClick={onClose} aria-label="Close arrivals">
          ×
        </button>
      </div>
      {error === 'not-found' && <p role="alert">This stop no longer exists. Pick another from a route.</p>}
      {error === 'failed' && (
        <p role="alert">Couldn't refresh arrivals. {data ? 'Showing the last times we had.' : 'Trying again shortly.'}</p>
      )}
      {!data && !error && <p>Loading arrivals…</p>}
      {data?.liveDataUnavailable && (
        <p className="notice" role="status">
          Live times unavailable for {agencyName(agency)}, showing schedule.
        </p>
      )}
      {data && data.routes.length === 0 && <p className="empty">No buses due in the next hour.</p>}
      {data?.routes.map((route) => (
        <div key={route.routeId} className="route-arrivals">
          <h3>
            <RouteBadge route={route} />
          </h3>
          <ul>
            {route.arrivals.map((a) => (
              <li key={a.tripId}>
                <span className="minutes">{minutesAway(a.expectedTime, now)}</span>
                <span className="detail">
                  {a.headsign && <span className="headsign">To {displayHeadsign(a.headsign)}</span>}
                  <span className="when">
                    <span className="clock">{clockTime(a.expectedTime)}</span>
                    <span className={a.live ? 'status live' : 'status'}>{arrivalStatus(a)}</span>
                  </span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      ))}
      {updatedAt && <p className="updated">Updated {clockTime(updatedAt.toISOString())}</p>}
    </section>
  );
}
