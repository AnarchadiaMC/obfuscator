package me.superblaubeere27.jobf.utils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.superblaubeere27.jobf.utils.values.Value;
import me.superblaubeere27.jobf.utils.values.ValueManager;

/**
 * Utility class for debugging configuration issues
 */
public class ConfigurationDebugger {
    private static final Logger log = LoggerFactory.getLogger("obfuscator");

    /**
     * Dumps the configuration file contents to the log
     * 
     * @param configFile The configuration file to dump
     */
    public static void dumpConfigFile(File configFile) {
        if (configFile == null || !configFile.exists()) {
            log.warn("Cannot dump configuration file: file is null or does not exist");
            return;
        }

        log.info("==== Configuration File Contents ====");
        log.info("File: {}", configFile.getAbsolutePath());
        
        try (FileReader reader = new FileReader(configFile)) {
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                content.append(buffer, 0, read);
            }
            
            log.info("\n{}", content.toString());
        } catch (IOException e) {
            log.error("Failed to read configuration file", e);
        }
        
        log.info("===================================");
    }
    
    /**
     * Dumps all registered values for a specific processor
     * 
     * @param processorName The name of the processor to dump values for
     */
    public static void dumpProcessorValues(String processorName) {
        log.info("==== Values for Processor: {} ====", processorName);
        
        Map<String, Value<?>> values = ValueManager.getValuesForOwner(processorName);
        
        if (values.isEmpty()) {
            log.info("No values registered for this processor");
        } else {
            for (Map.Entry<String, Value<?>> entry : values.entrySet()) {
                Value<?> value = entry.getValue();
                log.info("  {}::{} = {}", processorName, entry.getKey(), value.getObject());
                log.info("    Type: {}", value.getObject().getClass().getName());
                log.info("    Value object: {}", value);
            }
        }
        
        log.info("====================================");
    }
    
    /**
     * Checks for case mismatches between YAML keys and registered values
     * 
     * @param yamlKey The key from the YAML file
     * @param processorName The processor name to check against
     * @return The matching registered value name if found, null otherwise
     */
    public static String findCaseInsensitiveMatch(String yamlKey, String processorName) {
        Map<String, Value<?>> values = ValueManager.getValuesForOwner(processorName);
        
        for (String registeredName : values.keySet()) {
            if (registeredName.equalsIgnoreCase(yamlKey)) {
                if (!registeredName.equals(yamlKey)) {
                    log.info("Found case mismatch: YAML key '{}' -> Registered value '{}'", 
                            yamlKey, registeredName);
                    return registeredName;
                }
                return registeredName;
            }
        }
        
        return null;
    }
    
    /**
     * Dumps information about all registered processors and their values
     */
    public static void dumpAllProcessors() {
        log.info("==== All Registered Processors ====");
        
        for (Value<?> value : ValueManager.getValues()) {
            String owner = value.getOwner();
            if (owner != null && !owner.isEmpty()) {
                log.info("Found processor: {}", owner);
            }
        }
        
        log.info("==================================");
    }
} 