const NAMES: Record<string, string> = {
  goraleigh: 'GoRaleigh',
  godurham: 'GoDurham',
  gotriangle: 'GoTriangle',
  chapelhill: 'Chapel Hill Transit',
  gocary: 'GoCary',
};

/** A rider-facing agency name for an agency ID. */
export function agencyName(agencyId: string): string {
  return NAMES[agencyId] ?? agencyId;
}
