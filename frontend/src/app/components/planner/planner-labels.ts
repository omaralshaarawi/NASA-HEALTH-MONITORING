import { RegionId, Status } from '../../models/contract';
import menu from '../../models/exercise_menu.json';

export interface MenuItem {
  exercise_id: string;
  label: string;
  kind: string;
  met: number;
  target_system: string;
  requires_flight_surgeon_direction: boolean;
  nasa_basis: string;
}

const MENU = new Map<string, MenuItem>((menu.items as MenuItem[]).map((i) => [i.exercise_id, i]));

export function menuItem(exerciseId: string): MenuItem | undefined {
  return MENU.get(exerciseId);
}

// Short titles for the cards. The full contract label is shown underneath.
const TITLES: Record<string, string> = {
  resistive_moderate: 'ARED resistive loading',
  resistive_vigorous: 'ARED resistive loading, vigorous',
  cycle_ergometer: 'Cycle ergometer',
  treadmill_harness: 'Treadmill with harness',
  zero_t2_ergometer: 'Zero T2 compact workout',
  walk_surface: 'Surface walk',
  stretch_mobility: 'Stretch and mobility',
  thigh_cuff_bcomplex: 'Thigh cuff protocol',
  compression_fluid_loading: 'Compression and fluid loading',
  circadian_led_journaling: 'Circadian light and journal',
  microbial_swab_air_check: 'Habitat swab and air check',
};

export function exerciseTitle(exerciseId: string): string {
  return TITLES[exerciseId] ?? menuItem(exerciseId)?.label ?? exerciseId;
}

export const REGION_LABELS: Record<RegionId, string> = {
  HEAD_NEURO: 'Head / Neuro',
  CHEST_CARDIO: 'Chest / Cardio',
  SPINE_BONE: 'Spine / Bone',
  SYSTEMIC_IMMUNE_METABOLIC: 'Immune / Metabolic',
};

export function statusClass(status: Status): string {
  return 'status status-' + status.toLowerCase();
}
