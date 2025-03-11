/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.superblaubeere27.jobf.utils.values;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValueManager {
    private static final Logger log = LoggerFactory.getLogger("obfuscator");
    private static final List<Value<?>> values = new ArrayList<>();
    private static final Map<String, Map<String, Value<?>>> valuesByOwnerAndName = new HashMap<>();
    private static boolean isInitialized = false;

    /**
     * Registers a field if it is a Value instance
     *
     * @param field The field to register
     * @param object The object containing the field
     */
    private static void registerField(Field field, Object object) {
        field.setAccessible(true);

        try {
            Object obj = field.get(object);

            if (obj instanceof Value) {
                Value<?> value = (Value<?>) obj;
                values.add(value);
                
                // Also index by owner and name for faster lookups
                String owner = value.getOwner();
                String name = value.getName();
                
                log.debug("Registering value: {}::{} = {}", owner, name, value.getObject());
                
                if (!valuesByOwnerAndName.containsKey(owner)) {
                    valuesByOwnerAndName.put(owner, new HashMap<>());
                }
                valuesByOwnerAndName.get(owner).put(name, value);
            }
        } catch (IllegalAccessException e) {
            log.error("Failed to access field {} on {}", field.getName(), object.getClass().getName(), e);
        }
    }

    /**
     * Registers all Value fields in a class
     *
     * @param obj The object to scan for Value fields
     */
    public static void registerClass(Object obj) {
        if (obj == null) {
            log.warn("Attempted to register null object");
            return;
        }
        
        Class<?> clazz = obj.getClass();
        log.debug("Registering values from class: {}", clazz.getName());

        // Register public fields
        for (Field field : clazz.getFields()) {
            registerField(field, obj);
        }
        
        // Register private and protected fields
        for (Field field : clazz.getDeclaredFields()) {
            registerField(field, obj);
        }
        
        isInitialized = true;
    }

    /**
     * Gets all registered values
     *
     * @return A list of all registered Value objects
     */
    public static List<Value<?>> getValues() {
        if (!isInitialized) {
            log.warn("Attempting to get values before ValueManager is initialized");
        }
        return values;
    }
    
    /**
     * Retrieves a value by owner and name
     *
     * @param owner The owner of the value (e.g., processor name)
     * @param name The name of the value
     * @return The Value object if found, null otherwise
     */
    public static Value<?> getValue(String owner, String name) {
        if (!isInitialized) {
            log.warn("Attempting to get value before ValueManager is initialized: {}::{}", owner, name);
        }
        
        if (valuesByOwnerAndName.containsKey(owner)) {
            Map<String, Value<?>> ownerValues = valuesByOwnerAndName.get(owner);
            if (ownerValues.containsKey(name)) {
                return ownerValues.get(name);
            }
        }
        
        log.debug("Value not found: {}::{}", owner, name);
        return null;
    }
    
    /**
     * Gets all values for a specific owner
     *
     * @param owner The owner (e.g., processor name)
     * @return Map of values by name for this owner
     */
    public static Map<String, Value<?>> getValuesForOwner(String owner) {
        if (!valuesByOwnerAndName.containsKey(owner)) {
            return new HashMap<>();
        }
        return valuesByOwnerAndName.get(owner);
    }
    
    /**
     * Debug method to print all registered values
     */
    public static void dumpRegisteredValues() {
        log.info("=== Registered Values ===");
        for (String owner : valuesByOwnerAndName.keySet()) {
            log.info("Owner: {}", owner);
            Map<String, Value<?>> ownerValues = valuesByOwnerAndName.get(owner);
            for (Map.Entry<String, Value<?>> entry : ownerValues.entrySet()) {
                log.info("  {}::{} = {}", owner, entry.getKey(), entry.getValue().getObject());
            }
        }
        log.info("========================");
    }
    
    /**
     * Clear all registered values. Use with caution.
     */
    public static void clearAll() {
        values.clear();
        valuesByOwnerAndName.clear();
        isInitialized = false;
        log.debug("ValueManager has been reset");
    }

    /**
     * Verifies that necessary processors are registered.
     * This helps detect when values are requested before registration is complete.
     * 
     * @return true if required processors are registered, false otherwise
     */
    public static boolean verifyRequiredProcessors() {
        if (!isInitialized) {
            log.warn("ValueManager not fully initialized when verifying processors");
            return false;
        }
        
        // Check for required processors
        boolean hasNameObfuscation = valuesByOwnerAndName.containsKey("NameObfuscation");
        
        if (!hasNameObfuscation) {
            log.error("Required processor 'NameObfuscation' is not registered");
            return false;
        }
        
        // Check specific important values
        Value<?> shouldPackage = getValue("NameObfuscation", "Package");
        Value<?> newPackage = getValue("NameObfuscation", "New Packages");
        
        if (shouldPackage == null) {
            log.error("Required value 'NameObfuscation::Package' is not registered");
            return false;
        }
        
        if (newPackage == null) {
            log.error("Required value 'NameObfuscation::New Packages' is not registered");
            return false;
        }
        
        log.info("Verified all required processors and values are registered");
        return true;
    }
}
