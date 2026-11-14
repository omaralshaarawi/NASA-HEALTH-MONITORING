import { DailyMeasurementAndCompliance } from '../models/contract';

// The contract does not export these two, so they are derived from the measurement type.
export type AttachmentRef = NonNullable<DailyMeasurementAndCompliance['attachments']>[number];
export type AttachmentKind = AttachmentRef['kind'];
