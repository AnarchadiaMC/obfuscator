package me.superblaubeere27.jobf.utils.values;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for migrating JSON configurations to YAML
 */
public class ConfigMigrationUtil {
    private static final Logger log = LoggerFactory.getLogger("obfuscator");

    /**
     * Migrates a JSON configuration file to YAML format
     *
     * @param jsonConfigFile The JSON configuration file to migrate
     * @return The path to the new YAML file
     */
    public static Path migrateJsonToYaml(File jsonConfigFile) {
        try {
            // First read and parse the JSON
            String jsonContent = Files.readString(jsonConfigFile.toPath());
            Configuration config = ConfigManager.loadConfig(jsonContent);
            
            // Generate YAML content
            String yamlContent = YamlConfigManager.generateConfig(config);
            
            // Create YAML file with the same name but .yml extension
            String yamlFileName = jsonConfigFile.getAbsolutePath().replace(".json", ".yml");
            Path yamlFilePath = Paths.get(yamlFileName);
            Files.writeString(yamlFilePath, yamlContent);
            
            log.info("Successfully migrated config from {} to {}", jsonConfigFile.getPath(), yamlFilePath);
            
            return yamlFilePath;
        } catch (IOException e) {
            log.error("Failed to migrate JSON config to YAML", e);
            throw new RuntimeException("Failed to migrate JSON config to YAML", e);
        }
    }
    
    /**
     * Migrates all JSON configuration files in a directory to YAML format
     *
     * @param directory The directory containing JSON configurations
     */
    public static void migrateDirectoryToYaml(File directory) {
        if (!directory.isDirectory()) {
            log.error("Path is not a directory: {}", directory.getAbsolutePath());
            return;
        }
        
        log.info("Migrating all JSON configurations in {}", directory.getAbsolutePath());
        
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(path -> path.toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        migrateJsonToYaml(path.toFile());
                    } catch (Exception e) {
                        log.error("Failed to migrate {}", path, e);
                    }
                });
        } catch (IOException e) {
            log.error("Error walking directory {}", directory.getAbsolutePath(), e);
        }
    }
} 