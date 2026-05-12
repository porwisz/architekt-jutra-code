import { useEffect, useMemo, useState } from "react";
import { getSDK } from "../../../sdk";
import { toProductAnalysis } from "../domain";
import type { ProductAnalysis } from "../domain";

export const PRICE_DISCLAIMER =
  "Based on LLM general knowledge, not real-time market data";

export function getVerdictClassName(verdict: "PASS" | "WARN" | "FAIL"): string {
  if (verdict === "PASS") return "tc-badge tc-badge--success";
  if (verdict === "FAIL") return "tc-badge tc-badge--danger";
  return "tc-badge";
}

export default function ProductInfoBadge() {
  const sdk = useMemo(() => (typeof window !== "undefined" ? getSDK() : null), []);
  const productId = sdk?.thisPlugin.productId ?? "";

  const [analysis, setAnalysis] = useState<ProductAnalysis | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!sdk || !productId) {
      setLoading(false);
      return;
    }

    async function load() {
      try {
        const objects = await sdk!.thisPlugin.objects.listByEntity("PRODUCT", productId);
        if (objects.length > 0) {
          setAnalysis(toProductAnalysis(objects[0]));
        }
      } finally {
        setLoading(false);
      }
    }
    void load();
  }, [productId, sdk]);

  if (loading || !analysis) return null;

  return (
    <div className="tc-plugin" style={{ padding: "0.5rem 1rem" }}>
      <span className={getVerdictClassName(analysis.overallVerdict)}>
        {analysis.overallVerdict} — {analysis.summary}
      </span>
    </div>
  );
}
