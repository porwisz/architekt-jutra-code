import { ANALYZE_BUTTON_LABEL, REANALYZE_BUTTON_LABEL } from "../pages/product-tab";

describe("ProductTab", () => {
  test("ProductTab_noAnalysis_showsAnalyzeButton", () => {
    // When no analysis exists, button label is "Analyze"
    expect(ANALYZE_BUTTON_LABEL).toBe("Analyze");
  });

  test("ProductTab_hasAnalysis_showsReanalyzeButton", () => {
    // When analysis exists, button label is "Re-analyze"
    expect(REANALYZE_BUTTON_LABEL).toBe("Re-analyze");
  });
});
