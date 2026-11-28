import type { Route } from './api';
import { bulletTextColor } from './format';

/** A route bullet: the short name in the route's colour, with black or white text for contrast, like transit signage. */
export function RouteBadge({ route }: { route: Pick<Route, 'routeId' | 'shortName' | 'color'> }) {
  return (
    <span className="route-bullet" style={{ background: `#${route.color ?? '1f4aa8'}`, color: bulletTextColor(route.color) }}>
      {route.shortName ?? route.routeId}
    </span>
  );
}

/** The long name without a repeated short name prefix (Chapel Hill writes "A - Martin Luther King Jr. Blvd"). */
export function routeName(route: Pick<Route, 'shortName' | 'longName'>): string {
  const long = route.longName ?? '';
  const prefix = route.shortName ? `${route.shortName} - ` : '';
  return prefix && long.startsWith(prefix) ? long.slice(prefix.length) : long;
}
