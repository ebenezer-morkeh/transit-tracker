import type { Arrival } from './api';

/** "Due" under a minute away (or just passed), else whole minutes rounded down: "4 min". */
export function minutesAway(expectedTime: string, now: Date): string {
  const minutes = Math.floor((Date.parse(expectedTime) - now.getTime()) / 60_000);
  return minutes < 1 ? 'Due' : `${minutes} min`;
}

const CLOCK = new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit', timeZone: 'America/New_York' });

/** Wall-clock time in Raleigh, e.g. "2:47 PM". */
export function clockTime(time: string): string {
  return CLOCK.format(new Date(time)).replace(/ /g, ' '); // newer ICU puts a narrow no-break space before PM
}

/** "Scheduled", or "Live, on time" / "Live, 3 min late" / "Live, 2 min early" (within a minute is on time). */
export function arrivalStatus(arrival: Pick<Arrival, 'live' | 'delaySeconds'>): string {
  if (!arrival.live || arrival.delaySeconds === null) return 'Scheduled';
  const seconds = arrival.delaySeconds;
  if (Math.abs(seconds) < 60) return 'Live, on time';
  return `Live, ${Math.round(Math.abs(seconds) / 60)} min ${seconds > 0 ? 'late' : 'early'}`;
}

const SMALL_WORDS = new Set(['VIA', 'TO', 'AT', 'AND', 'OF', 'THE', 'ON']);

/**
 * Feeds often send destinations in ALL CAPS, which is hard to read. Title-case those, keeping short codes (RDU, UNC)
 * and anything with a digit (I-40) as they are; mixed-case destinations pass through untouched.
 */
export function displayHeadsign(headsign: string): string {
  if (headsign !== headsign.toUpperCase() || !/[A-Z]/.test(headsign)) return headsign;
  return headsign
    .split(' ')
    .map((word, i) => {
      if (i > 0 && SMALL_WORDS.has(word)) return word.toLowerCase();
      if (/\d/.test(word) || (word.length <= 3 && !SMALL_WORDS.has(word))) return word;
      return word
        .split('-')
        .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
        .join('-');
    })
    .join(' ');
}

/** Black or white, whichever contrasts more with the route colour (hex, no #): always at least 4.5:1. */
export function bulletTextColor(color: string | null): string {
  if (!color) return '#ffffff';
  const channel = (i: number) => {
    const c = parseInt(color.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  const luminance = 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4);
  return 1.05 / (luminance + 0.05) >= (luminance + 0.05) / 0.05 ? '#ffffff' : '#000000';
}
