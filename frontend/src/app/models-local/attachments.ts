import { DailyMeasurementAndCompliance } from '../models/contract';

// not exported by the contract
export type AttachmentRef = NonNullable<DailyMeasurementAndCompliance['attachments']>[number];
export type AttachmentKind = AttachmentRef['kind'];
