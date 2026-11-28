import { describe, expect, it } from 'vitest';
import { routeName } from './RouteBadge';

describe('routeName', () => {
  it('drops a long name prefix that repeats the short name', () => {
    expect(routeName({ shortName: 'A', longName: 'A - Martin Luther King Jr. Blvd' })).toBe('Martin Luther King Jr. Blvd');
  });

  it('keeps long names that only start with the same letters, or have no short name', () => {
    expect(routeName({ shortName: '7', longName: '70 Express' })).toBe('70 Express');
    expect(routeName({ shortName: null, longName: 'Airport' })).toBe('Airport');
    expect(routeName({ shortName: '7', longName: null })).toBe('');
  });
});
