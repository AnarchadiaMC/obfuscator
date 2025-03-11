/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.superblaubeere27.jobf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import me.superblaubeere27.jobf.utils.ConfigurationDebugger;
import me.superblaubeere27.jobf.utils.values.ConfigManager;
import me.superblaubeere27.jobf.utils.values.ConfigMigrationUtil;
import me.superblaubeere27.jobf.utils.values.Configuration;
import me.superblaubeere27.jobf.utils.values.YamlConfigManager;

public class JObf {
    private static final Logger log = LoggerFactory.getLogger("obfuscator");
    
    public static final String SHORT_VERSION = (JObf.class.getPackage().getImplementationVersion() == null ? "DEV" : "v" + JObf.class.getPackage().getImplementationVersion()) + " by superblaubeere27";
    public static final String VERSION = "obfuscator " + (JObf.class.getPackage().getImplementationVersion() == null ? "DEV" : "v" + JObf.class.getPackage().getImplementationVersion()) + " by superblaubeere27";

    public static boolean VERBOSE = false;

    public static void main(String[] args) throws Exception {
        log.info(VERSION);
        log.info("");

        OptionParser parser = new OptionParser();

        parser.accepts("help", "Shows this help menu").forHelp();
        parser.accepts("version", "Displays the version number");
        parser.accepts("jarIn", "The input jar").withRequiredArg().required();
        parser.accepts("jarOut", "The output jar").withRequiredArg().required();
        parser.accepts("config", "The config file (YAML format preferred)").withRequiredArg();
        parser.accepts("script", "Script for the obfuscator").withRequiredArg();
        parser.accepts("verbose", "Displays verbose debug output");
        parser.accepts("noUpdate", "Skip update check");
        parser.accepts("threads", "Number of threads").withRequiredArg().ofType(Integer.class).defaultsTo(Runtime.getRuntime().availableProcessors());
        parser.accepts("skipLibs", "Skip extracting libraries");
        parser.accepts("libraries", "List of additional libraries").withRequiredArg();
        parser.accepts("migrateConfigs", "Migrate all JSON configurations to YAML").withOptionalArg();

        try {
            OptionSet options = parser.parse(args);

            if (options.has("help")) {
                parser.printHelpOn(System.out);
                return;
            }

            if (options.has("version")) {
                System.out.println(VERSION);
                return;
            }

            VERBOSE = options.has("verbose");

            String jarIn = (String) options.valueOf("jarIn");
            String jarOut = (String) options.valueOf("jarOut");
            File configPath = null;

            if (options.has("config")) {
                configPath = new File((String) options.valueOf("config"));
            }

            // Handle migration option
            if (options.has("migrateConfigs")) {
                String migrationPath = (String) options.valueOf("migrateConfigs");
                File migrationDir;
                
                if (migrationPath != null && !migrationPath.trim().isEmpty()) {
                    migrationDir = new File(migrationPath);
                } else {
                    migrationDir = new File("."); // Current directory
                }
                
                log.info("Starting migration of JSON configs to YAML in {}", migrationDir.getAbsolutePath());
                ConfigMigrationUtil.migrateDirectoryToYaml(migrationDir);
                log.info("Migration complete. Please use the new YAML configuration files.");
                return;
            }

            String scriptContent = null;
            if (options.has("script")) {
                scriptContent = new String(Files.readAllBytes(new File((String) options.valueOf("script")).toPath()), StandardCharsets.UTF_8);
            }

            List<String> libraries = new ArrayList<>();

            if (options.has("libraries")) {
                libraries.addAll(Arrays.asList(((String) options.valueOf("libraries")).split(",")));
            }

            boolean updateCheck = !options.has("noUpdate");
            int threads = (int) options.valueOf("threads");

            boolean outdated = false;

            if (updateCheck) {
                String version = checkForUpdate();

                if (version != null) {
                    log.info("Update check was successful");

                    outdated = version != null && !version.equals(JObf.class.getPackage().getImplementationVersion());

                    if (outdated) {
                        log.info("Your version is outdated. Latest version: " + version);
                    }
                } else {
                    log.info("Update check failed");
                }
            }

            log.info("Running on " + threads + " threads");

            runObfuscator(jarIn, jarOut, configPath, libraries, outdated, false, null, scriptContent, threads);
        } catch (OptionException e) {
            log.error(e.getMessage());
            log.error("");
            parser.printHelpOn(System.out);
        }
    }

    public static boolean runEmbedded(String jarIn, String jarOut, File configPath, List<String> libraries, String scriptContent) throws IOException, InterruptedException {
        return runObfuscator(jarIn, jarOut, configPath, libraries, false, true, null, scriptContent, Runtime.getRuntime().availableProcessors());
    }

    private static boolean runObfuscator(String jarIn, String jarOut, File configPath, List<String> libraries, boolean outdated, boolean embedded, String version, String scriptContent, int threads) throws IOException, InterruptedException {
        log.info("Input: " + jarIn);
        log.info("Output: " + jarOut);
        
        // Initialize JObfImpl to register processors BEFORE loading config
        log.info("Initializing obfuscator and registering processors...");
        JObfImpl.INSTANCE.setThreadCount(threads);
        
        // Now that processors are registered, load configuration
        Configuration config = new Configuration(jarIn, jarOut, scriptContent, libraries);

        if (configPath != null) {
            if (!configPath.exists()) {
                log.error("Config file specified but not found!");
                return false;
            }

            try {
                log.info("Loading configuration from: {}", configPath.getAbsolutePath());
                // Dump configuration file contents for debugging
                ConfigurationDebugger.dumpConfigFile(configPath);
                
                // Try to load as YAML first
                if (configPath.getName().endsWith(".yml") || configPath.getName().endsWith(".yaml")) {
                    log.info("Detected YAML configuration file");
                    config = YamlConfigManager.loadConfig(configPath);
                    log.info("YAML configuration loaded successfully");
                    
                    // Debug processor values after loading
                    ConfigurationDebugger.dumpProcessorValues("NameObfuscation");
                } else if (configPath.getName().endsWith(".json")) {
                    // Migration: load old JSON file and convert to YAML
                    log.info("Detected JSON config, migrating to YAML format...");
                    Path yamlPath = ConfigMigrationUtil.migrateJsonToYaml(configPath);
                    config = YamlConfigManager.loadConfig(yamlPath.toFile());
                    log.info("Migration successful. Please use the new YAML config file at {} in the future.", yamlPath);
                    
                    // Debug processor values after loading
                    ConfigurationDebugger.dumpProcessorValues("NameObfuscation");
                } else {
                    // Try YAML first, fall back to JSON
                    try {
                        log.info("Trying to load as YAML configuration...");
                        config = YamlConfigManager.loadConfig(configPath);
                        log.info("YAML configuration loaded successfully");
                        
                        // Debug processor values after loading
                        ConfigurationDebugger.dumpProcessorValues("NameObfuscation");
                    } catch (Exception e) {
                        log.warn("Failed to load configuration as YAML, trying JSON format: {}", e.getMessage());
                        config = ConfigManager.loadConfig(new String(ByteStreams.toByteArray(new FileInputStream(configPath)), StandardCharsets.UTF_8));
                        // If JSON loading succeeded, create a YAML backup
                        Path yamlPath = ConfigMigrationUtil.migrateJsonToYaml(configPath);
                        log.info("Created YAML backup at {}. Please use this file in the future.", yamlPath);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to load configuration file", e);
                return false;
            }
            
            config.setInput(jarIn);
            config.setOutput(jarOut);
        }

        config.getLibraries().addAll(libraries);

        if (scriptContent != null && !scriptContent.isEmpty()) {
            config.setScript(scriptContent);
        }

        // Run the actual obfuscation with our now-properly-loaded configuration
        log.info("Starting obfuscation with loaded configuration:");
        ConfigurationDebugger.dumpProcessorValues("NameObfuscation");
        
        // Reapply configuration now that processors are initialized
        if (configPath != null) {
            log.info("Reapplying configuration after processor initialization...");
            YamlConfigManager.reapplyConfig(configPath);
            log.info("Configuration after reapplication:");
            ConfigurationDebugger.dumpProcessorValues("NameObfuscation");
        }
        
        try {
            JObfImpl.INSTANCE.processJar(config);
            return true;
        } catch (Exception e) {
            log.error("Error processing jar", e);
            return false;
        }
    }

    private static String checkForUpdate() {
        try {
            return new String(ByteStreams.toByteArray(new URL("https://raw.githubusercontent.com/superblaubeere27/obfuscator/master/version").openStream()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.error("Failed to check for updates", e);
            return null;
        }
    }
}
