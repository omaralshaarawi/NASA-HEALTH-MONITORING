import { Pipe, PipeTransform } from '@angular/core';

import { REGION_LABELS, exerciseTitle } from '../components/planner/planner-labels';
import menu from '../models/exercise_menu.json';
import rules from '../models/rules.json';

// identifier -> the name people use, longest first so one id is never cut by a shorter one
const NAMES: Array<[string, string]> = [
  ...Object.entries(REGION_LABELS),
  ...menu.items.map((item): [string, string] => [item.exercise_id, exerciseTitle(item.exercise_id)]),
  ...Object.entries(rules.environments).map(([id, env]): [string, string] => [id, env.label]),
].sort((a, b) => b[0].length - a[0].length);

// Plan text sometimes carries identifiers such as HEAD_NEURO or zero_t2_ergometer.
// This swaps them for their labels. Wording only: no number is touched.
@Pipe({ name: 'plainWords' })
export class PlainWordsPipe implements PipeTransform {
  transform(text: string): string {
    return NAMES.reduce((out, [id, name]) => out.replaceAll(id, name), text);
  }
}
