using FootprintCalculation.FootprintReporting;

namespace FootprintCalculation.ComponentTree;

/// <summary>
/// Port (interfejs domenowy) — kolekcyjny dostęp do drzew komponentów. Punkt łączący
/// FootprintCalculation z Product Catalog (mapping 1:1 productId → root CompositeComponent).
/// Adapter implementujący ten interfejs może czytać dane z Product Catalog albo z lokalnego
/// storage — wybór implementacji jest poza scope tego BC. The concrete BB is the
/// <c>ComponentRepository</c> adapter; this interface is the C# contract only and is not
/// annotated as a domain Building Block to avoid double-counting in scans.
/// </summary>
public interface IComponentRepository
{
    /// <summary>
    /// Zwraca korzeń <see cref="CompositeComponent"/> drzewa komponentów dla danego
    /// <see cref="ProductId"/>. Mapping 1:1. Drzewo dla każdego typu produktu w MVP
    /// jest statyczne. Jeśli produkt nie istnieje albo nie ma przypisanego drzewa,
    /// operacja zgłasza błąd (nie zwraca null).
    /// </summary>
    CompositeComponent FindRootByProductId(ProductId productId);
}
