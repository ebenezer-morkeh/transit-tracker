import { describe, expect, it } from 'vitest';
import { readSelection, writeSelection } from './selection';

describe('selection in the query string', () => {
  it('reads every part, with direction as a number', () => {
    expect(readSelection('?agency=goraleigh&route=R2&direction=1&stop=1234')).toEqual({
      agency: 'goraleigh',
      route: 'R2',
      direction: 1,
      stop: '1234',
    });
  });

  it('a stop link needs only agency and stop', () => {
    expect(readSelection('?agency=goraleigh&stop=1234')).toEqual({ agency: 'goraleigh', stop: '1234' });
  });

  it('ignores parts that make no sense on their own or are malformed', () => {
    expect(readSelection('')).toEqual({});
    expect(readSelection('?route=R2&stop=1234')).toEqual({});
    expect(readSelection('?agency=goraleigh&route=R2&direction=north')).toEqual({ agency: 'goraleigh', route: 'R2' });
    expect(readSelection('?agency=&route=R2')).toEqual({});
  });

  it('writes only the parts that are set, in a stable order, and round-trips', () => {
    expect(writeSelection({})).toBe('');
    expect(writeSelection({ agency: 'goraleigh', route: 'R 2', direction: 0 })).toBe(
      '?agency=goraleigh&route=R+2&direction=0',
    );
    const full = { agency: 'goraleigh', route: 'R2', direction: 1, stop: 'A&B' };
    expect(readSelection(writeSelection(full))).toEqual(full);
  });
});
