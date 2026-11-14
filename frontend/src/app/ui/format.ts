import { Status } from '../models/contract';

// How a one-way signal delay is written: "Live" near Earth, seconds at the Moon, minutes at Mars.
export function delayText(seconds: number): { value: string; unit: string } {
  if (seconds < 0.1) return { value: 'Live', unit: '' };
  if (seconds < 60) return { value: seconds.toFixed(1), unit: 's' };
  return { value: (seconds / 60).toFixed(1), unit: 'min' };
}

// The plain word that goes with each status colour.
export const STATUS_WORD: Record<Status, string> = {
  GREEN: 'nominal',
  YELLOW: 'caution',
  RED: 'alert',
};
