namespace FootprintCalculation.Tests.FootprintReporting;

/// <summary>
/// Tagging attribute for tests that verify Quality Attributes declared in the design doc.
/// Mirrors the Scenario / Actor tagging convention; the noesis annotations package does not
/// yet expose a QualityAttribute attribute, so this lives in the test project.
/// </summary>
[AttributeUsage(AttributeTargets.Method | AttributeTargets.Class, AllowMultiple = true)]
public sealed class QualityAttributeAttribute(string name) : Attribute
{
    public string Name { get; } = name;
}
