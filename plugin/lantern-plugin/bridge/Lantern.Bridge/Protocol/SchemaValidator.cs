using System.Text.Json;
using Json.Schema;

namespace Lantern.Bridge.Protocol;

/// <summary>
/// JSON Schema (draft 2020-12) validation against contracts/schemas. Inbox
/// reads are strict (unknown fields fail). Bridge writes are also strict
/// (spec §15.2).
/// </summary>
public sealed class SchemaValidator
{
    private static readonly object LoadGate = new();
    private static Dictionary<string, JsonSchema>? Cached;
    private readonly Dictionary<string, JsonSchema> _schemas;

    public SchemaValidator(string? contractsDir = null)
    {
        Dialect.Default = Dialect.Draft202012;
        lock (LoadGate)
        {
            if (Cached is not null)
            {
                _schemas = Cached;
                return;
            }

            _schemas = new Dictionary<string, JsonSchema>(StringComparer.OrdinalIgnoreCase);
            var loaded = TryLoadFromDirectory(contractsDir)
                || TryLoadByWalkingUp()
                || TryLoadEmbedded();
            if (!loaded)
            {
                throw new InvalidOperationException(
                    "Could not load contracts/schemas. Set LANTERN_CONTRACTS_DIR or run from the repo.");
            }

            Cached = _schemas;
        }
    }

    public SchemaValidationResult Validate(string schemaFileName, string json)
    {
        if (!_schemas.TryGetValue(schemaFileName, out var schema))
        {
            throw new InvalidOperationException($"Unknown schema: {schemaFileName}");
        }

        JsonDocument document;
        try
        {
            document = JsonDocument.Parse(json);
        }
        catch (Exception ex)
        {
            return SchemaValidationResult.Fail($"JSON parse error: {ex.Message}");
        }

        using (document)
        {
            var result = schema.Evaluate(document.RootElement, new EvaluationOptions
            {
                OutputFormat = OutputFormat.List,
                RequireFormatValidation = true,
            });
            if (result.IsValid)
            {
                return SchemaValidationResult.Ok();
            }

            var errors = CollectErrors(result);
            if (errors.Count == 0)
            {
                errors.Add("Schema validation failed.");
            }

            return SchemaValidationResult.Fail(string.Join("; ", errors));
        }
    }

    public void EnsureValid(string schemaFileName, string json)
    {
        var result = Validate(schemaFileName, json);
        if (!result.IsValid)
        {
            throw new InvalidOperationException($"Strict-writer violation ({schemaFileName}): {result.Error}");
        }
    }

    private static List<string> CollectErrors(EvaluationResults result)
    {
        var errors = new List<string>();
        Collect(result, errors);
        return errors;
    }

    private static void Collect(EvaluationResults result, List<string> errors)
    {
        if (result.Errors is { Count: > 0 })
        {
            foreach (var error in result.Errors)
            {
                errors.Add($"{result.InstanceLocation}: {error.Value}");
            }
        }

        if (result.Details is null)
        {
            return;
        }

        foreach (var child in result.Details)
        {
            if (!child.IsValid)
            {
                Collect(child, errors);
            }
        }
    }

    private bool TryLoadFromDirectory(string? contractsDir)
    {
        if (string.IsNullOrWhiteSpace(contractsDir) || !Directory.Exists(contractsDir))
        {
            return false;
        }

        return LoadFrom(contractsDir);
    }

    private bool TryLoadByWalkingUp()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null)
        {
            var schemas = Path.Combine(dir.FullName, "contracts", "schemas");
            if (Directory.Exists(schemas) && LoadFrom(schemas))
            {
                return true;
            }

            dir = dir.Parent;
        }

        return false;
    }

    private bool TryLoadEmbedded()
    {
        var assembly = typeof(SchemaValidator).Assembly;
        var names = assembly.GetManifestResourceNames()
            .Where(n => n.Contains("schemas", StringComparison.OrdinalIgnoreCase)
                && n.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
            .ToList();
        if (names.Count == 0)
        {
            return false;
        }

        foreach (var name in names)
        {
            using var stream = assembly.GetManifestResourceStream(name);
            if (stream is null)
            {
                continue;
            }

            using var reader = new StreamReader(stream);
            var json = reader.ReadToEnd();
            var marker = name.LastIndexOf("schemas.", StringComparison.OrdinalIgnoreCase);
            var shortName = marker >= 0
                ? name[(marker + "schemas.".Length)..]
                : Path.GetFileName(name);
            _schemas[shortName] = JsonSchema.FromText(json, new BuildOptions { Dialect = Dialect.Draft202012 });
        }

        return _schemas.Count > 0;
    }

    private bool LoadFrom(string schemasDir)
    {
        var files = Directory.GetFiles(schemasDir, "*.schema.json");
        if (files.Length == 0)
        {
            return false;
        }

        foreach (var file in files)
        {
            _schemas[Path.GetFileName(file)] = JsonSchema.FromFile(file, new BuildOptions { Dialect = Dialect.Draft202012 });
        }

        return true;
    }
}

public sealed record SchemaValidationResult(bool IsValid, string? Error)
{
    public static SchemaValidationResult Ok() => new(true, null);

    public static SchemaValidationResult Fail(string error) => new(false, error);
}
