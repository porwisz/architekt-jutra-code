using FootprintCalculation.ComponentTree;
using FootprintCalculation.EmissionMeasurement;
using NoesisVision.Annotations.Domain;
using NoesisVision.Annotations.Domain.DDD;
using NoesisVision.Annotations.People;
using NoesisVision.Annotations.Technology.CleanArchitecture;

namespace FootprintCalculation.FootprintReporting;

/// <summary>
/// Jedyny publiczny interfejs modułu FootprintCalculation. Bezstanowy. Orkiestruje pobranie
/// drzewa komponentów z <see cref="IComponentRepository"/>, rozwiązanie wersji przez
/// <see cref="SimpleComponent.VersionAt"/>, sprawdzenie applicability, wywołanie
/// <see cref="EmissionCalculator"/> dla każdego liścia, agregacja kompozytów, budowa
/// <see cref="FootprintBreakdown"/> z polami audytowymi.
/// </summary>
[DddApplicationService]
[UseCasesLayer]
public sealed class FootprintFacade
{
    private readonly IComponentRepository _repo;
    private readonly IEmissionFactorPort _port;
    private readonly EmissionCalculator _calculator;

    public FootprintFacade(
        IComponentRepository repo,
        IEmissionFactorPort port,
        EmissionCalculator calculator)
    {
        _repo = repo;
        _port = port;
        _calculator = calculator;
    }

    /// <summary>
    /// Główna operacja silnika. Pobiera korzeń drzewa komponentów dla produktu z
    /// <see cref="IComponentRepository.FindRootByProductId"/>, rekurencyjnie schodzi w dół drzewa:
    /// dla każdego <see cref="SimpleComponent"/> rozwiązuje aktywną wersję przez
    /// <see cref="SimpleComponent.VersionAt"/>, sprawdza
    /// <see cref="Applicability.IsSatisfiedBy"/>, woła
    /// <see cref="EmissionCalculator.Calculate"/> gdzie rate pochodzi z aktywnej wersji a
    /// quantity z odpowiedniego pola <see cref="FootprintParameters"/> wskazanego przez
    /// <c>SimpleComponent.QuantitySource</c>. Dzieci nieaktywne są CAŁKOWICIE wykluczone z
    /// wynikowego drzewa (nie zerowane). Kompozytowe węzły sumują kgCO2 swoich aktywnych dzieci.
    /// </summary>
    /// <remarks>
    /// <code>
    /// sequenceDiagram
    ///   participant Customer
    ///   participant Facade as FootprintFacade
    ///   participant Repo as ComponentRepository
    ///   participant Tree as CompositeComponent
    ///   participant Leaf as SimpleComponent
    ///   participant Port as EmissionFactorPort
    ///   participant Calc as EmissionCalculator
    ///   participant Result as FootprintBreakdown
    ///
    ///   Customer-&gt;&gt;Facade: CalculateTotalFootprint(parameters)
    ///   Facade-&gt;&gt;Repo: FindRootByProductId(parameters.productId)
    ///   Repo--&gt;&gt;Facade: root CompositeComponent
    ///   Facade-&gt;&gt;Tree: walk(parameters)
    ///   loop for each child Component
    ///     Tree-&gt;&gt;Tree: check applicability against parameters
    ///     alt applicable composite child
    ///       Tree-&gt;&gt;Tree: recurse into composite child
    ///     else applicable leaf
    ///       Tree-&gt;&gt;Leaf: VersionAt(parameters.timestamp)
    ///       Leaf--&gt;&gt;Tree: SimpleComponentVersion
    ///       Tree-&gt;&gt;Port: GetActiveRate(leaf.id, parameters.timestamp)
    ///       Port--&gt;&gt;Tree: EmissionFactorRate
    ///       Tree-&gt;&gt;Calc: Calculate(rate, quantity)
    ///       Calc--&gt;&gt;Tree: KgCO2
    ///     else not applicable
    ///       Tree-&gt;&gt;Tree: skip child (exclude from breakdown)
    ///     end
    ///   end
    ///   Tree--&gt;&gt;Facade: BreakdownNode tree with kgCO2 at every node
    ///   Facade-&gt;&gt;Result: wrap as FootprintBreakdown
    ///   Result--&gt;&gt;Customer: FootprintBreakdown (total + tree)
    /// </code>
    /// </remarks>
    [DomainBehavior]
    [Actor("Customer")]
    public FootprintBreakdown CalculateTotalFootprint(FootprintParameters parameters)
    {
        ArgumentNullException.ThrowIfNull(parameters);

        var root = _repo.FindRootByProductId(parameters.ProductId);

        var rootNode = BuildNode(root, parameters)
            ?? throw new InvalidOperationException(
                "Root composite component must always be applicable.");

        var compositeRoot = (CompositeBreakdownNode)rootNode;
        return new FootprintBreakdown(compositeRoot, compositeRoot.KgCO2);
    }

    /// <summary>
    /// Pomocnicza operacja zwracająca breakdown przeliczony na jednostkę referencyjną
    /// (100 g materiału). Deleguje obliczenie do
    /// <see cref="CalculateTotalFootprint"/> a następnie skaluje każdy węzeł drzewa przez ten sam
    /// współczynnik (referenceUnit / materialWeight). Pola audytowe na liściach
    /// (<c>EmissionFactorUsed</c>, <c>FactorValidFrom</c>) pozostają NIEZMIENIONE — przeliczenie
    /// nie modyfikuje współczynników.
    /// </summary>
    /// <remarks>
    /// <code>
    /// sequenceDiagram
    ///   participant Customer
    ///   participant Facade as FootprintFacade
    ///   participant Total as CalculateTotalFootprint
    ///   participant Result as FootprintBreakdown
    ///
    ///   Customer-&gt;&gt;Facade: CalculateUnitFootprint(parameters)
    ///   Facade-&gt;&gt;Total: CalculateTotalFootprint(parameters)
    ///   Total--&gt;&gt;Facade: total FootprintBreakdown
    ///   Facade-&gt;&gt;Facade: scale each KgCO2 by referenceUnit / materialWeight
    ///   Facade--&gt;&gt;Customer: per-unit FootprintBreakdown
    /// </code>
    /// </remarks>
    [DomainBehavior]
    [Actor("Customer")]
    public FootprintBreakdown CalculateUnitFootprint(FootprintParameters parameters)
    {
        ArgumentNullException.ThrowIfNull(parameters);

        if (parameters.MaterialWeight.Value <= 0m)
        {
            throw new ArgumentException(
                "MaterialWeight must be positive",
                nameof(parameters));
        }

        var total = CalculateTotalFootprint(parameters);

        var scale = 1m / (parameters.MaterialWeight.Value * 10m);

        var scaledRoot = (CompositeBreakdownNode)ScaleNode(total.Root, scale);
        return new FootprintBreakdown(scaledRoot, scaledRoot.KgCO2);
    }

    private BreakdownNode? BuildNode(Component component, FootprintParameters parameters)
    {
        switch (component)
        {
            case SimpleComponent simple:
                {
                    if (!simple.Applicability.IsSatisfiedBy(parameters))
                    {
                        return null;
                    }

                    var version = simple.VersionAt(parameters.Timestamp);
                    var quantity = ResolveQuantity(simple.QuantitySource, parameters);
                    var kg = _calculator.Calculate(version.Rate, quantity);

                    return new SimpleBreakdownNode(
                        simple.Id,
                        kg,
                        simple.Scope,
                        version.Rate,
                        version.Validity.ValidFrom);
                }

            case CompositeComponent composite:
                {
                    if (!composite.Applicability.IsSatisfiedBy(parameters))
                    {
                        return null;
                    }

                    var children = new List<BreakdownNode>();
                    foreach (var child in composite.Children)
                    {
                        var built = BuildNode(child, parameters);
                        if (built is not null)
                        {
                            children.Add(built);
                        }
                    }

                    var kg = children.Aggregate(
                        KgCO2.Zero,
                        (acc, c) => acc.Add(c.KgCO2));

                    return new CompositeBreakdownNode(composite.Id, kg, children);
                }

            default:
                throw new InvalidOperationException(
                    $"Unknown component type: {component.GetType().Name}");
        }
    }

    private static Quantity ResolveQuantity(string source, FootprintParameters p) =>
        source switch
        {
            "materialWeight" => p.MaterialWeight,
            "supplierDistance" => p.SupplierDistance,
            "destinationDistance" => p.DestinationDistance,
            "lastMileDistance" => p.LastMileDistance,
            "storageDays" => p.StorageDays,
            _ => throw new InvalidOperationException($"Unknown quantitySource: {source}"),
        };

    private static BreakdownNode ScaleNode(BreakdownNode node, decimal scale)
    {
        switch (node)
        {
            case SimpleBreakdownNode leaf:
                return new SimpleBreakdownNode(
                    leaf.ComponentId,
                    new KgCO2(leaf.KgCO2.Value * scale),
                    leaf.Scope,
                    leaf.EmissionFactorUsed,
                    leaf.FactorValidFrom);

            case CompositeBreakdownNode composite:
                {
                    var scaledChildren = composite.Children
                        .Select(c => ScaleNode(c, scale))
                        .ToList();
                    return new CompositeBreakdownNode(
                        composite.ComponentId,
                        new KgCO2(composite.KgCO2.Value * scale),
                        scaledChildren);
                }

            default:
                throw new InvalidOperationException(
                    $"Unknown breakdown node type: {node.GetType().Name}");
        }
    }
}
