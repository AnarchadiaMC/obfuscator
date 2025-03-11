# Configuration Migration from JSON to YAML

This project has migrated from using JSON configuration files to YAML format. YAML offers several advantages over JSON for configuration:

1. Better readability with cleaner syntax
2. Support for comments
3. More compact representation for complex configurations
4. Less error-prone (no trailing commas issues)

## Automatic Migration

The obfuscator now includes automatic migration tools to help you transition from JSON to YAML:

1. When you run the obfuscator with a JSON configuration file, it will:
   - Automatically convert it to YAML format
   - Save the YAML version alongside your JSON file
   - Use the YAML configuration for processing
   - Encourage you to use the YAML file in the future

2. You can also explicitly migrate all your JSON configurations:
   ```
   java -jar obfuscator.jar --migrateConfigs [directory]
   ```
   This will convert all JSON configuration files in the specified directory (or current directory if omitted) to YAML format.

## Manual Migration

To manually convert a JSON configuration to YAML:

1. Replace the `.json` extension with `.yml` or `.yaml`
2. Convert the structure following these rules:
   - Remove all curly braces
   - Remove all double quotes around property names
   - Use spaces for indentation (2 or 4 spaces recommended)
   - Use `-` for array elements

## Example

**Old JSON format:**
```json
{
  "NameObfuscation": {
    "Enabled": true,
    "Package": true,
    "New Packages": "org.batman",
    "Accept Missing Libraries": true
  }
}
```

**New YAML format:**
```yaml
NameObfuscation:
  Enabled: true
  Package: true
  New Packages: org.batman
  Accept Missing Libraries: true
```

## Existing JSON Configurations

Your existing JSON configuration files will continue to work during the transition period, but we recommend migrating to YAML format for better maintainability and easier editing.

## Need Help?

If you encounter any issues during migration, please open an issue on the GitHub repository. 