import { AssessmentResponse, CountermeasurePlan } from '../models/contract';

import assessmentKai from './assessment_ast_kai.json';
import assessmentLin from './assessment_ast_lin.json';
import assessmentNova from './assessment_ast_nova.json';
import planKai from './plan_ast_kai.json';
import planLin from './plan_ast_lin.json';
import planNova from './plan_ast_nova.json';

export const MOCK_ASSESSMENTS: Record<string, AssessmentResponse> = {
  ast_nova: assessmentNova as unknown as AssessmentResponse,
  ast_kai: assessmentKai as unknown as AssessmentResponse,
  ast_lin: assessmentLin as unknown as AssessmentResponse,
};

export const MOCK_PLANS: Record<string, CountermeasurePlan> = {
  ast_nova: planNova as unknown as CountermeasurePlan,
  ast_kai: planKai as unknown as CountermeasurePlan,
  ast_lin: planLin as unknown as CountermeasurePlan,
};
