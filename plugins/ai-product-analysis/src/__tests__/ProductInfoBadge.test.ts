import { getVerdictClassName, PRICE_DISCLAIMER } from "../pages/product-info-badge";

describe("ProductInfoBadge", () => {
  test("ProductInfoBadge_passVerdict_rendersTcBadgeSuccess", () => {
    const className = getVerdictClassName("PASS");
    expect(className).toBe("tc-badge tc-badge--success");
  });

  test("ProductInfoBadge_failVerdict_rendersTcBadgeDanger", () => {
    const className = getVerdictClassName("FAIL");
    expect(className).toBe("tc-badge tc-badge--danger");
  });

  test("ProductInfoBadge_warnVerdict_rendersNeutralBadge", () => {
    const className = getVerdictClassName("WARN");
    expect(className).toBe("tc-badge");
    expect(className).not.toContain("tc-badge--success");
    expect(className).not.toContain("tc-badge--danger");
  });
});

describe("ProductTab", () => {
  test("ProductTab_priceDisclaimer_isPresent", () => {
    expect(PRICE_DISCLAIMER).toBe(
      "Based on LLM general knowledge, not real-time market data"
    );
  });
});
