import { describe, expect, it } from 'vitest';
import { arrivalStatus, bulletTextColor, clockTime, displayHeadsign, minutesAway } from './format';

const now = new Date('2026-11-25T13:00:00Z'); // 8:00 AM in Raleigh

describe('minutesAway', () => {
  it('is "Due" under a minute, including just passed', () => {
    expect(minutesAway('2026-11-25T08:00:59-05:00', now)).toBe('Due');
    expect(minutesAway('2026-11-25T07:59:50-05:00', now)).toBe('Due');
  });

  it('is whole minutes, rounded down, from a minute on', () => {
    expect(minutesAway('2026-11-25T08:01:00-05:00', now)).toBe('1 min');
    expect(minutesAway('2026-11-25T08:04:59-05:00', now)).toBe('4 min');
    expect(minutesAway('2026-11-25T09:00:00-05:00', now)).toBe('60 min');
  });
});

describe('clockTime', () => {
  it('shows Raleigh wall-clock time whatever the browser zone', () => {
    expect(clockTime('2026-11-25T19:47:00Z')).toBe('2:47 PM');
    expect(clockTime('2026-07-01T12:05:00Z')).toBe('8:05 AM'); // EDT
  });
});

describe('arrivalStatus', () => {
  it('says scheduled when not live', () => {
    expect(arrivalStatus({ live: false, delaySeconds: null })).toBe('Scheduled');
  });

  it('says on time within a minute either way', () => {
    expect(arrivalStatus({ live: true, delaySeconds: 59 })).toBe('Live, on time');
    expect(arrivalStatus({ live: true, delaySeconds: -59 })).toBe('Live, on time');
  });

  it('rounds lateness and earliness to minutes', () => {
    expect(arrivalStatus({ live: true, delaySeconds: 180 })).toBe('Live, 3 min late');
    expect(arrivalStatus({ live: true, delaySeconds: 90 })).toBe('Live, 2 min late');
    expect(arrivalStatus({ live: true, delaySeconds: -120 })).toBe('Live, 2 min early');
  });
});

describe('displayHeadsign', () => {
  it('turns an all-caps destination into title case, keeping short codes', () => {
    expect(displayHeadsign('TRIANGLE TOWN CENTER MALL')).toBe('Triangle Town Center Mall');
    expect(displayHeadsign('RDU AIRPORT VIA I-40')).toBe('RDU Airport via I-40');
  });

  it('leaves mixed-case destinations alone', () => {
    expect(displayHeadsign('To Crabtree Valley Mall')).toBe('To Crabtree Valley Mall');
  });
});

describe('bulletTextColor', () => {
  it('picks white on dark route colours and black on light ones', () => {
    expect(bulletTextColor('d92a1c')).toBe('#ffffff');
    expect(bulletTextColor('1f4aa8')).toBe('#ffffff');
    expect(bulletTextColor('f2c500')).toBe('#000000');
    expect(bulletTextColor(null)).toBe('#ffffff');
  });
});
