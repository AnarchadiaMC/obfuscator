package me.superblaubeere27.jobf.utils.values;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

public class YamlConfigManager {
    private static final Logger log = LoggerFactory.getLogger("obfuscator");
    private static final Yaml yaml;

    static {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        
        LoaderOptions loaderOptions = new LoaderOptions();
        Representer representer = new Representer(options);
        representer.getPropertyUtils().setSkipMissingProperties(true);
        
        yaml = new Yaml(new Constructor(loaderOptions), representer, options);
    }

    public static String generateConfig(Configuration config) {
        Map<String, Object> root = new LinkedHashMap<>();
        
        // Add basic configuration
        root.put("input", config.getInput());
        root.put("output", config.getOutput());
        root.put("script", config.getScript());
        root.put("libraries", config.getLibraries());

        // Add processor configurations
        for (String owner : ValueManager.getValuesForOwner(null).keySet()) {
            Map<String, Value<?>> ownerValues = ValueManager.getValuesForOwner(owner);
            if (ownerValues.isEmpty()) {
                continue;
            }
            
            Map<String, Object> processorConfig = new LinkedHashMap<>();
            
            for (Value<?> value : ownerValues.values()) {
                processorConfig.put(value.getName(), value.getObject());
            }
            
            root.put(owner, processorConfig);
        }

        return yaml.dump(root);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Configuration loadConfig(String yamlContent) {
        try {
            // First verify that required processors are registered
            if (!ValueManager.verifyRequiredProcessors()) {
                log.warn("Loading configuration before all processors are registered. Some settings may not be applied correctly.");
            }
            
            Map<String, Object> root = yaml.load(yamlContent);
            if (root == null) {
                throw new IllegalArgumentException("YAML content is empty or invalid");
            }

            // Log the loaded YAML for debugging
            log.debug("Loaded YAML content:");
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                log.debug("  {} = {}", entry.getKey(), entry.getValue());
            }

            // Load basic configuration
            String input = getString(root, "input", "");
            String output = getString(root, "output", "");
            String script = getString(root, "script", null);
            List<String> libraries = getList(root, "libraries", new ArrayList<>());

            Configuration config = new Configuration(input, output, script, libraries);

            // Load processor configurations
            // First dump all registered values for debugging
            ValueManager.dumpRegisteredValues();
            
            // Process each YAML section that might be a processor
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                String yamlOwner = entry.getKey();
                
                // Skip basic config keys
                if (yamlOwner.equals("input") || yamlOwner.equals("output") || 
                    yamlOwner.equals("script") || yamlOwner.equals("libraries")) {
                    continue;
                }
                
                // Get the processor's values
                Object processorConfigObj = entry.getValue();
                if (!(processorConfigObj instanceof Map)) {
                    log.warn("Skipping non-map processor config: {}", yamlOwner);
                    continue;
                }
                
                Map<String, Object> processorConfig = (Map<String, Object>) processorConfigObj;
                log.info("Processing configuration for: {}", yamlOwner);
                
                // Get all values for this processor
                Map<String, Value<?>> registeredValues = ValueManager.getValuesForOwner(yamlOwner);
                
                if (registeredValues.isEmpty()) {
                    log.warn("No registered values found for processor: {}", yamlOwner);
                    continue;
                }
                
                // Process each value in the YAML
                for (Map.Entry<String, Object> valueEntry : processorConfig.entrySet()) {
                    String yamlValueName = valueEntry.getKey();
                    Object yamlValueObject = valueEntry.getValue();
                    
                    // Try to find the corresponding registered value
                    Value value = registeredValues.get(yamlValueName);
                    
                    if (value == null) {
                        // Try case-insensitive matching
                        for (String registeredName : registeredValues.keySet()) {
                            if (registeredName.equalsIgnoreCase(yamlValueName)) {
                                value = registeredValues.get(registeredName);
                                log.info("Found case-insensitive match for {}.{} -> {}.{}", 
                                        yamlOwner, yamlValueName, yamlOwner, registeredName);
                                break;
                            }
                        }
                    }
                    
                    if (value == null) {
                        log.warn("Could not find registered value for {}.{}", yamlOwner, yamlValueName);
                        continue;
                    }
                    
                    // Update the value
                    try {
                        // Type conversion
                        Object currentValue = value.getObject();
                        log.info("Updating {}.{}: {} -> {}", yamlOwner, yamlValueName, 
                                currentValue, yamlValueObject);
                        
                        if (currentValue instanceof Number) {
                            if (yamlValueObject instanceof Number) {
                                Number numValue = (Number) yamlValueObject;
                                if (currentValue instanceof Integer) {
                                    value.setObject(numValue.intValue());
                                } else if (currentValue instanceof Long) {
                                    value.setObject(numValue.longValue());
                                } else if (currentValue instanceof Float) {
                                    value.setObject(numValue.floatValue());
                                } else if (currentValue instanceof Double) {
                                    value.setObject(numValue.doubleValue());
                                }
                            } else {
                                // Try parsing string to number
                                try {
                                    if (currentValue instanceof Integer) {
                                        value.setObject(Integer.parseInt(yamlValueObject.toString()));
                                    } else if (currentValue instanceof Long) {
                                        value.setObject(Long.parseLong(yamlValueObject.toString()));
                                    } else if (currentValue instanceof Float) {
                                        value.setObject(Float.parseFloat(yamlValueObject.toString()));
                                    } else if (currentValue instanceof Double) {
                                        value.setObject(Double.parseDouble(yamlValueObject.toString()));
                                    }
                                } catch (NumberFormatException e) {
                                    log.error("Failed to convert {} to {}", yamlValueObject, 
                                            currentValue.getClass().getSimpleName());
                                }
                            }
                        } else if (currentValue instanceof Boolean) {
                            if (yamlValueObject instanceof Boolean) {
                                value.setObject(yamlValueObject);
                            } else {
                                // Parse string to boolean
                                value.setObject(Boolean.parseBoolean(yamlValueObject.toString()));
                            }
                        } else if (currentValue instanceof String) {
                            value.setObject(yamlValueObject.toString());
                        } else {
                            log.warn("Unsupported value type: {}", currentValue.getClass().getName());
                        }
                        
                        log.info("Updated {}.{} = {}", yamlOwner, yamlValueName, value.getObject());
                    } catch (Exception e) {
                        log.error("Error updating value {}.{}", yamlOwner, yamlValueName, e);
                    }
                }
            }
            
            // Dump all values after update for verification
            log.info("Values after YAML configuration:");
            ValueManager.dumpRegisteredValues();

            return config;
        } catch (Exception e) {
            log.error("Failed to parse YAML configuration", e);
            throw new IllegalArgumentException("Failed to parse YAML configuration", e);
        }
    }

    public static void saveConfig(Configuration config, File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(generateConfig(config));
        }
    }

    public static Configuration loadConfig(File file) throws IOException {
        log.info("Loading configuration from file: {}", file.getAbsolutePath());
        try (FileReader reader = new FileReader(file)) {
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                content.append(buffer, 0, read);
            }
            return loadConfig(content.toString());
        }
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> getList(Map<String, Object> map, String key, List<T> defaultValue) {
        Object value = map.get(key);
        return value instanceof List ? (List<T>) value : defaultValue;
    }

    /**
     * Reapplies configuration values to a YAML root data structure.
     * This is useful when processors are registered after initial config loading.
     * 
     * @param yamlRoot The YAML root map loaded from file
     * @return true if any values were updated, false otherwise
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean reapplyConfigValues(Map<String, Object> yamlRoot) {
        if (yamlRoot == null) {
            log.warn("Cannot reapply config from null YAML root");
            return false;
        }
        
        boolean anyUpdated = false;
        log.info("Reapplying configuration values from YAML...");
        
        // Verify processors are registered now
        if (!ValueManager.verifyRequiredProcessors()) {
            log.warn("Still missing required processors when reapplying configuration");
        }
        
        // Process each YAML section that might be a processor
        for (Map.Entry<String, Object> entry : yamlRoot.entrySet()) {
            String yamlOwner = entry.getKey();
            
            // Skip basic config keys
            if (yamlOwner.equals("input") || yamlOwner.equals("output") || 
                yamlOwner.equals("script") || yamlOwner.equals("libraries")) {
                continue;
            }
            
            // Get the processor's values
            Object processorConfigObj = entry.getValue();
            if (!(processorConfigObj instanceof Map)) {
                continue;
            }
            
            Map<String, Object> processorConfig = (Map<String, Object>) processorConfigObj;
            log.info("Reapplying configuration for: {}", yamlOwner);
            
            // Get all values for this processor
            Map<String, Value<?>> registeredValues = ValueManager.getValuesForOwner(yamlOwner);
            
            if (registeredValues.isEmpty()) {
                log.warn("No registered values found for processor: {}", yamlOwner);
                continue;
            }
            
            // Process each value in the YAML
            for (Map.Entry<String, Object> valueEntry : processorConfig.entrySet()) {
                String yamlValueName = valueEntry.getKey();
                Object yamlValueObject = valueEntry.getValue();
                
                // Try to find the corresponding registered value
                Value value = registeredValues.get(yamlValueName);
                
                if (value == null) {
                    // Try case-insensitive matching
                    for (String registeredName : registeredValues.keySet()) {
                        if (registeredName.equalsIgnoreCase(yamlValueName)) {
                            value = registeredValues.get(registeredName);
                            log.info("Found case-insensitive match for {}.{} -> {}.{}", 
                                    yamlOwner, yamlValueName, yamlOwner, registeredName);
                            break;
                        }
                    }
                }
                
                if (value == null) {
                    log.warn("Could not find registered value for {}.{}", yamlOwner, yamlValueName);
                    continue;
                }
                
                // Update the value
                try {
                    // Type conversion
                    Object currentValue = value.getObject();
                    log.info("Updating {}.{}: {} -> {}", yamlOwner, yamlValueName, 
                            currentValue, yamlValueObject);
                    
                    if (currentValue instanceof Number) {
                        if (yamlValueObject instanceof Number) {
                            Number numValue = (Number) yamlValueObject;
                            if (currentValue instanceof Integer) {
                                value.setObject(numValue.intValue());
                            } else if (currentValue instanceof Long) {
                                value.setObject(numValue.longValue());
                            } else if (currentValue instanceof Float) {
                                value.setObject(numValue.floatValue());
                            } else if (currentValue instanceof Double) {
                                value.setObject(numValue.doubleValue());
                            }
                            anyUpdated = true;
                        } else {
                            // Try parsing string to number
                            try {
                                if (currentValue instanceof Integer) {
                                    value.setObject(Integer.parseInt(yamlValueObject.toString()));
                                } else if (currentValue instanceof Long) {
                                    value.setObject(Long.parseLong(yamlValueObject.toString()));
                                } else if (currentValue instanceof Float) {
                                    value.setObject(Float.parseFloat(yamlValueObject.toString()));
                                } else if (currentValue instanceof Double) {
                                    value.setObject(Double.parseDouble(yamlValueObject.toString()));
                                }
                                anyUpdated = true;
                            } catch (NumberFormatException e) {
                                log.error("Failed to convert {} to {}", yamlValueObject, 
                                        currentValue.getClass().getSimpleName());
                            }
                        }
                    } else if (currentValue instanceof Boolean) {
                        if (yamlValueObject instanceof Boolean) {
                            value.setObject(yamlValueObject);
                            anyUpdated = true;
                        } else {
                            // Parse string to boolean
                            value.setObject(Boolean.parseBoolean(yamlValueObject.toString()));
                            anyUpdated = true;
                        }
                    } else if (currentValue instanceof String) {
                        value.setObject(yamlValueObject.toString());
                        anyUpdated = true;
                    } else {
                        log.warn("Unsupported value type: {}", currentValue.getClass().getName());
                    }
                    
                    log.info("Updated {}.{} = {}", yamlOwner, yamlValueName, value.getObject());
                } catch (Exception e) {
                    log.error("Error updating value {}.{}", yamlOwner, yamlValueName, e);
                }
            }
        }
        
        if (anyUpdated) {
            log.info("Successfully reapplied configuration values");
            // Dump values for verification
            log.info("Values after reapplying configuration:");
            ValueManager.dumpRegisteredValues();
        } else {
            log.warn("No configuration values were updated during reapplication");
        }
        
        return anyUpdated;
    }

    /**
     * Reapplies configuration from a file after processors are initialized.
     * 
     * @param file The YAML configuration file
     * @return true if any values were updated, false otherwise
     */
    public static boolean reapplyConfig(File file) {
        if (file == null || !file.exists()) {
            log.warn("Cannot reapply config from non-existent file");
            return false;
        }
        
        try {
            log.info("Reapplying configuration from file: {}", file.getAbsolutePath());
            Map<String, Object> yamlRoot = yaml.load(new FileReader(file));
            return reapplyConfigValues(yamlRoot);
        } catch (Exception e) {
            log.error("Failed to reapply configuration", e);
            return false;
        }
    }
} 