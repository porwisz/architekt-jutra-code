import type { NextApiRequest, NextApiResponse } from "next";
import { b } from "../../../baml_client";
import { createServerSDK } from "../../../../server-sdk";

interface AnalyzeRequest {
  productId: string | number;
}

interface ErrorResponse {
  error: string;
  details?: string[];
}

interface DimensionAssessmentResponse {
  verdict: "PASS" | "WARN" | "FAIL";
  score: number;
  explanation: string;
}

interface AnalyzeResponse {
  objectId: string;
  overallVerdict: "PASS" | "WARN" | "FAIL";
  summary: string;
  descriptionQuality: DimensionAssessmentResponse;
  categoryRelevance: DimensionAssessmentResponse;
  priceAssessment: DimensionAssessmentResponse;
}

export default async function handler(
  req: NextApiRequest,
  res: NextApiResponse<AnalyzeResponse | ErrorResponse>
) {
  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method not allowed" });
  }

  const body = req.body as Partial<AnalyzeRequest>;

  const productId = body.productId != null ? String(body.productId).trim() : "";
  if (!productId) {
    return res.status(400).json({
      error: "Missing required fields",
      details: ["productId"],
    });
  }

  const sdk = createServerSDK("ai-product-analysis", undefined, req);

  try {
    const product = (await sdk.hostApp.getProduct(productId)) as {
      name: string;
      description: string;
      category?: { name: string };
      categoryName?: string;
      price?: string | number | null;
    };

    const categoryName = product.category?.name ?? product.categoryName ?? "";
    const price = String(product.price ?? "");

    const data = await b.AnalyzeProduct(
      product.name,
      product.description,
      categoryName,
      price
    );

    const dataToSave = {
      overallVerdict: data.overallVerdict,
      summary: data.summary,
      descriptionQuality: data.descriptionQuality,
      categoryRelevance: data.categoryRelevance,
      priceAssessment: data.priceAssessment,
    };

    await sdk.thisPlugin.objects.save("analysis", productId, dataToSave, {
      entityType: "PRODUCT",
      entityId: productId,
    });

    return res.status(200).json({
      objectId: productId,
      ...dataToSave,
    });
  } catch (err) {
    console.error("Analyze failed:", err);
    const message = err instanceof Error ? err.message : String(err);
    const statusMatch = message.match(/Host API error (\d+)/);
    const status = statusMatch ? parseInt(statusMatch[1], 10) : 500;
    return res.status(status).json({ error: message });
  }
}
