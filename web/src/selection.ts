/** What the rider has picked; lives in the query string so any state can be bookmarked or shared. */
export interface Selection {
  agency?: string;
  route?: string;
  direction?: number;
  stop?: string;
}

/** Route and stop only mean something with an agency; direction only with a route. */
export function readSelection(search: string): Selection {
  const params = new URLSearchParams(search);
  const agency = params.get('agency') || undefined;
  if (!agency) return {};
  const selection: Selection = { agency };
  const route = params.get('route') || undefined;
  if (route) {
    selection.route = route;
    const direction = params.get('direction');
    if (direction !== null && /^\d+$/.test(direction)) selection.direction = Number(direction);
  }
  const stop = params.get('stop') || undefined;
  if (stop) selection.stop = stop;
  return selection;
}

export function writeSelection(selection: Selection): string {
  const params = new URLSearchParams();
  if (selection.agency) params.set('agency', selection.agency);
  if (selection.route) params.set('route', selection.route);
  if (selection.direction !== undefined) params.set('direction', String(selection.direction));
  if (selection.stop) params.set('stop', selection.stop);
  const query = params.toString();
  return query ? `?${query}` : '';
}
