import type { PluginObject } from "../../sdk";

export interface DimensionAssessment {
  verdict: "PASS" | "WARN" | "FAIL";
  score: number;
  explanation: string;
}

export interface ProductAnalysis {
  objectId: string;
  overallVerdict: "PASS" | "WARN" | "FAIL";
  summary: string;
  descriptionQuality: DimensionAssessment;
  categoryRelevance: DimensionAssessment;
  priceAssessment: DimensionAssessment;
}

function toDimensionAssessment(raw: unknown): DimensionAssessment {
  const d = (raw ?? {}) as Record<string, unknown>;
  return {
    verdict: (d.verdict as "PASS" | "WARN" | "FAIL") ?? "FAIL",
    score: typeof d.score === "number" ? d.score : 0,
    explanation: typeof d.explanation === "string" ? d.explanation : "",
  };
}

export function toProductAnalysis(obj: PluginObject): ProductAnalysis {
  const d = obj.data;
  return {
    objectId: obj.objectId,
    overallVerdict: (d.overallVerdict as "PASS" | "WARN" | "FAIL") ?? "FAIL",
    summary: typeof d.summary === "string" ? d.summary : "",
    descriptionQuality: toDimensionAssessment(d.descriptionQuality),
    categoryRelevance: toDimensionAssessment(d.categoryRelevance),
    priceAssessment: toDimensionAssessment(d.priceAssessment),
  };
}
