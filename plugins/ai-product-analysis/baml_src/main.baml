class DimensionAssessment {
  verdict "PASS" | "WARN" | "FAIL"
  score int
  explanation string
}

class ProductAnalysis {
  overallVerdict "PASS" | "WARN" | "FAIL"
  descriptionQuality DimensionAssessment
  categoryRelevance DimensionAssessment
  priceAssessment DimensionAssessment
  summary string
}

function AnalyzeProduct(
  productName: string,
  productDescription: string,
  categoryName: string,
  price: string
) -> ProductAnalysis {
  client LlmProvider
  prompt #"
    You are a product quality analyst. Evaluate the following product across three dimensions.

    Product Name: {{ productName }}
    Product Description: {{ productDescription }}
    Category: {{ categoryName }}
    Price: {{ price }}

    Evaluate each dimension and assign a verdict (PASS, WARN, or FAIL) with a score from 1 to 10 and a brief explanation:

    1. Description Quality: Assess whether the description is accurate and complete relative to the product name. A good description clearly explains what the product is and its key features.

    2. Category Relevance: Assess whether the assigned category is the most appropriate classification for this product. Consider whether a more specific or accurate category exists.

    3. Price Assessment: Based on your training knowledge, estimate whether the price is normal, suspicious, or clearly wrong for this type of product in its category.

    Also provide an overall verdict and a one-sentence summary suitable for a sidebar badge.

    {{ ctx.output_format }}
  "#
}
