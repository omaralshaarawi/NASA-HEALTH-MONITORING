import { DailyMeasurementAndCompliance } from '../../../models/contract';

export type Attachment = NonNullable<DailyMeasurementAndCompliance['attachments']>[number];
export type ScanKind = Attachment['kind'];

export interface ScanGuide {
  kind: ScanKind;
  tab: string;
  title: string;
  device: string;
  // what the two numbered points in the diagram are
  points: [string, string];
  steps: string[];
  why: string;
  fact?: { value: string; caption: string };
}

// Placement help for the three capture types. Training content for a prototype:
// the app stores the capture and forwards it, it never interprets it.
export const SCAN_GUIDES: ScanGuide[] = [
  {
    kind: 'OCULAR_ULTRASOUND',
    tab: 'Eye ultrasound',
    title: 'Eye ultrasound for SANS',
    device: 'Ultrasound 2 · linear probe',
    points: ['Gel on the closed eyelid', 'Probe rests on the gel, not on the eye'],
    steps: [
      'Strap in. A crewmate steadies your head, or brace it against the wall pad.',
      'Close the eye and put a generous layer of gel on the closed eyelid.',
      'Rest your hand on the cheekbone and lower the probe into the gel. Never press on the eye.',
      'Tilt slowly until the dark round globe and the nerve shadow behind it are centred.',
      'Freeze, save the frame, then repeat for the other eye.',
    ],
    why: 'The head-ward fluid shift raises pressure behind the eye. Over months it can flatten the back of the eye and swell the optic nerve: Spaceflight Associated Neuro-ocular Syndrome (SANS). Regular scans let the flight surgeon follow it.',
    fact: { value: 'About 70%', caption: 'of crew on long space-station missions show signs of SANS' },
  },
  {
    kind: 'CARDIAC_ULTRASOUND',
    tab: 'Heart ultrasound',
    title: 'Heart ultrasound',
    device: 'Ultrasound 2 · phased-array probe',
    points: ['Beside the breastbone, left side of the chest', 'Lower left chest, at the tip of the heart'],
    steps: [
      'Strap in with the chest bare. The operator hooks both feet in restraints so both hands are free.',
      'Put gel on the probe and place it just left of the breastbone (point 1).',
      'Tilt and rotate a little at a time until the heart chambers fill the picture.',
      'Save a clip of a few heartbeats, then repeat from the lower left chest (point 2).',
    ],
    why: 'With no gravity to work against, the heart changes shape and the muscle slowly deconditions. Ultrasound shows how it fills and pumps, which matters before a landing or heavy surface work.',
    fact: { value: 'Deconditioning', caption: 'the heart works less against gravity and adapts to it' },
  },
  {
    kind: 'ECG',
    tab: 'ECG',
    title: 'Resting ECG',
    device: 'Four-electrode ECG recorder',
    points: ['Upper electrodes below each collarbone', 'Lower electrodes on each side of the lower abdomen'],
    steps: [
      'Wipe the skin dry. Shave if needed so the electrodes stick.',
      'Place the four electrodes as shown: two below the collarbones, two on the lower abdomen.',
      'Clip the leads on, strap in and let your arms float. Muscle tension shows up as noise.',
      'Stay still and breathe normally while the strip records, then save it.',
    ],
    why: 'Fluid shifts, changes in blood salts and a deconditioned heart can disturb its rhythm. A resting strip gives the flight surgeon something to compare with your pre-flight recording.',
  },
];
