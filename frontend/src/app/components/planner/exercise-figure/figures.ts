// Poses for the pictogram figure, on a 240 x 160 canvas, side view facing right.
// A pose is just joints; ExerciseFigureComponent draws the body from them and adds the equipment.

export type Point = [number, number];

export interface Pose {
  head: Point;
  shoulder: Point;
  hip: Point;
  // each limb: [middle joint, end]
  nearArm: [Point, Point];
  farArm: [Point, Point];
  nearLeg: [Point, Point];
  farLeg: [Point, Point];
}

export interface Callout {
  at: Point;
  label: string;
}

export interface Figure {
  // which equipment drawing to use
  scene:
    | 'ared'
    | 'cycle'
    | 'treadmill'
    | 'compact'
    | 'surface'
    | 'stretch'
    | 'cuff'
    | 'compression'
    | 'circadian'
    | 'swab';
  pose: Pose;
  callouts: Callout[];
  caption: string;
}

const ARED: Figure = {
  scene: 'ared',
  pose: {
    head: [137, 46],
    shoulder: [129, 62],
    hip: [108, 98],
    nearArm: [[139, 76], [131, 62]],
    farArm: [[142, 72], [135, 60]],
    nearLeg: [[141, 108], [120, 134]],
    farLeg: [[148, 110], [130, 134]],
  },
  callouts: [
    { at: [84, 58], label: 'Load bar on the shoulders' },
    { at: [172, 126], label: 'Feet flat on the platform' },
  ],
  caption: 'Squat against the resistive device',
};

export const FIGURES: Record<string, Figure> = {
  resistive_moderate: ARED,
  resistive_vigorous: ARED,

  cycle_ergometer: {
    scene: 'cycle',
    pose: {
      head: [80, 40],
      shoulder: [84, 56],
      hip: [98, 94],
      nearArm: [[106, 72], [130, 64]],
      farArm: [[108, 68], [130, 60]],
      nearLeg: [[132, 82], [160, 101]],
      farLeg: [[126, 104], [141, 123]],
    },
    callouts: [
      { at: [62, 76], label: 'Back against the pad, strapped in' },
      { at: [186, 112], label: 'Shoes clipped to the pedals' },
    ],
    caption: 'Pedal at a steady pace',
  },

  treadmill_harness: {
    scene: 'treadmill',
    pose: {
      head: [130, 34],
      shoulder: [125, 50],
      hip: [117, 88],
      nearArm: [[112, 66], [100, 76]],
      farArm: [[140, 64], [152, 52]],
      nearLeg: [[138, 104], [150, 129]],
      farLeg: [[110, 110], [90, 118]],
    },
    callouts: [
      { at: [86, 92], label: 'Harness around hips and shoulders' },
      { at: [172, 112], label: 'Bungees pull you onto the belt' },
    ],
    caption: 'Run while the harness holds you down',
  },

  zero_t2_ergometer: {
    scene: 'compact',
    pose: {
      head: [96, 58],
      shoulder: [102, 74],
      hip: [112, 112],
      nearArm: [[124, 84], [146, 86]],
      farArm: [[126, 80], [146, 82]],
      nearLeg: [[142, 94], [166, 120]],
      farLeg: [[146, 100], [166, 128]],
    },
    callouts: [
      { at: [156, 66], label: 'Pull the handle towards your chest' },
      { at: [204, 88], label: 'Feet braced on the compact unit' },
    ],
    caption: 'Compact device: pull and push with the legs',
  },

  walk_surface: {
    scene: 'surface',
    pose: {
      head: [122, 40],
      shoulder: [120, 58],
      hip: [118, 94],
      nearArm: [[112, 74], [107, 88]],
      farArm: [[130, 72], [138, 84]],
      nearLeg: [[130, 111], [139, 132]],
      farLeg: [[110, 113], [99, 131]],
    },
    callouts: [
      { at: [86, 62], label: 'Suit and life-support pack checked' },
      { at: [168, 118], label: 'Short, steady steps in partial gravity' },
    ],
    caption: 'Walk on the surface',
  },

  stretch_mobility: {
    scene: 'stretch',
    pose: {
      head: [134, 41],
      shoulder: [131, 57],
      hip: [120, 90],
      nearArm: [[149, 50], [163, 37]],
      farArm: [[148, 44], [160, 30]],
      nearLeg: [[122, 112], [126, 134]],
      farLeg: [[113, 112], [112, 134]],
    },
    callouts: [
      { at: [86, 126], label: 'Feet under the restraint bar' },
      { at: [184, 52], label: 'Slow reach, no bouncing' },
    ],
    caption: 'Anchor your feet and stretch slowly',
  },

  thigh_cuff_bcomplex: {
    scene: 'cuff',
    pose: {
      head: [78, 56],
      shoulder: [92, 66],
      hip: [126, 86],
      nearArm: [[108, 84], [124, 76]],
      farArm: [[110, 78], [128, 70]],
      nearLeg: [[152, 72], [172, 92]],
      farLeg: [[154, 82], [170, 104]],
    },
    callouts: [
      { at: [150, 48], label: 'Cuffs on the upper thighs' },
      { at: [70, 30], label: 'Fluid shifts towards the head in 0 g' },
    ],
    caption: 'Flight surgeon sets the cuff and timing',
  },

  compression_fluid_loading: {
    scene: 'compression',
    pose: {
      head: [119, 36],
      shoulder: [119, 52],
      hip: [119, 90],
      nearArm: [[132, 68], [138, 52]],
      farArm: [[109, 72], [106, 90]],
      nearLeg: [[125, 113], [127, 136]],
      farLeg: [[113, 113], [111, 136]],
    },
    callouts: [
      { at: [80, 112], label: 'Compression garment on legs and abdomen' },
      { at: [166, 44], label: 'Fluids as directed by the flight surgeon' },
    ],
    caption: 'Before landing or surface work',
  },

  circadian_led_journaling: {
    scene: 'circadian',
    pose: {
      head: [108, 60],
      shoulder: [114, 76],
      hip: [124, 110],
      nearArm: [[132, 94], [147, 84]],
      farArm: [[134, 90], [149, 80]],
      nearLeg: [[151, 102], [157, 128]],
      farLeg: [[148, 110], [150, 134]],
    },
    callouts: [
      { at: [74, 36], label: 'Daytime light after waking, dim before sleep' },
      { at: [182, 74], label: 'A few honest lines in the journal' },
    ],
    caption: 'Light for the body clock, journal for the mind',
  },

  microbial_swab_air_check: {
    scene: 'swab',
    pose: {
      head: [118, 40],
      shoulder: [116, 56],
      hip: [110, 92],
      nearArm: [[138, 62], [158, 68]],
      farArm: [[114, 76], [128, 86]],
      nearLeg: [[114, 114], [116, 136]],
      farLeg: [[105, 114], [103, 136]],
    },
    callouts: [
      { at: [204, 40], label: 'Swab the marked surfaces' },
      { at: [46, 96], label: 'Run the air sampler' },
    ],
    caption: 'Check surfaces and cabin air',
  },
};
