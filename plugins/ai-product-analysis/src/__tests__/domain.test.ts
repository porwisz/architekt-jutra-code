import type { PluginObject } from "../../../sdk";
import { toProductAnalysis } from "../domain";

function makePluginObject(data: Record<string, unknown>): PluginObject {
  return {
    id: "1",
    pluginId: "ai-product-analysis",
    objectType: "analysis",
    objectId: "42",
    data,
  };
}

describe("toProductAnalysis", () => {
  test("toProductAnalysis_validPluginObject_mapsAllThreeDimensions", () => {
    const obj = makePluginObject({
      overallVerdict: "PASS",
      summary: "Good product",
      descriptionQuality: { verdict: "PASS", score: 8, explanation: "Clear and accurate" },
      categoryRelevance: { verdict: "PASS", score: 9, explanation: "Correct category" },
      priceAssessment: { verdict: "WARN", score: 6, explanation: "Slightly pricey" },
    });

    const result = toProductAnalysis(obj);

    expect(result.descriptionQuality.verdict).toBe("PASS");
    expect(result.descriptionQuality.score).toBe(8);
    expect(result.descriptionQuality.explanation).toBe("Clear and accurate");

    expect(result.categoryRelevance.score).toBe(9);
    expect(result.categoryRelevance.verdict).toBe("PASS");

    expect(result.priceAssessment.verdict).toBe("WARN");
    expect(result.priceAssessment.explanation).toBe("Slightly pricey");
  });

  test("toProductAnalysis_missingOptionalFields_returnsDefaults", () => {
    const obj = makePluginObject({
      overallVerdict: "FAIL",
      summary: "",
      descriptionQuality: { verdict: "FAIL" },
      categoryRelevance: { verdict: "FAIL" },
      priceAssessment: { verdict: "FAIL" },
    });

    const result = toProductAnalysis(obj);

    expect(result.descriptionQuality.explanation).toBe("");
    expect(result.descriptionQuality.score).toBe(0);
    expect(result.categoryRelevance.explanation).toBe("");
    expect(result.categoryRelevance.score).toBe(0);
    expect(result.priceAssessment.explanation).toBe("");
    expect(result.priceAssessment.score).toBe(0);
  });

  test("toProductAnalysis_nullDimensionObject_usesDefaults", () => {
    const obj = makePluginObject({
      overallVerdict: "FAIL",
      summary: null,
      descriptionQuality: null,
      categoryRelevance: null,
      priceAssessment: null,
    });

    const result = toProductAnalysis(obj);

    expect(result.summary).toBe("");
    expect(result.descriptionQuality.verdict).toBe("FAIL");
    expect(result.descriptionQuality.score).toBe(0);
    expect(result.descriptionQuality.explanation).toBe("");
    expect(result.categoryRelevance.score).toBe(0);
    expect(result.priceAssessment.score).toBe(0);
  });

  test("toProductAnalysis_preservesObjectId", () => {
    const obj = makePluginObject({
      overallVerdict: "PASS",
      summary: "ok",
      descriptionQuality: { verdict: "PASS", score: 7, explanation: "" },
      categoryRelevance: { verdict: "PASS", score: 7, explanation: "" },
      priceAssessment: { verdict: "PASS", score: 7, explanation: "" },
    });
    obj.objectId = "product-99";

    const result = toProductAnalysis(obj);

    expect(result.objectId).toBe("product-99");
  });
});
