using FootprintCalculation.ComponentTree;

namespace FootprintCalculation.EmissionMeasurement;

/// <summary>
/// Port (interfejs domenowy) odczytujący wartości EmissionFactorRate z osobnego BC
/// Emission Factor Management. Read-only — silnik FootprintCalculation nigdy nie zapisuje
/// do modułu Factor. Implementację dostarcza warstwa adapterów; tryb testowy: in-memory.
/// The concrete BB is the <c>EmissionFactorPort</c> adapter; this interface is the C#
/// contract only and is not annotated as a domain Building Block to avoid double-counting.
/// </summary>
public interface IEmissionFactorPort
{
    /// <summary>
    /// Pobiera EmissionFactorRate dla danego ComponentId obowiązującą w danym Timestamp.
    /// Implementacja portu może wewnętrznie sięgać po SimpleComponent.versions (gdy oba moduły
    /// żyją w tym samym procesie i dzielą storage) albo po inne źródło. Zwraca błąd, gdy
    /// ComponentId nie ma żadnej wersji obowiązującej w danym Timestamp.
    /// </summary>
    EmissionFactorRate GetActiveRate(ComponentId componentId, Timestamp timestamp);
}
