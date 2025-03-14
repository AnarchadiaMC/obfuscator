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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ModifiedClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

import me.superblaubeere27.jobf.processors.CrasherTransformer;
import me.superblaubeere27.jobf.processors.HideMembers;
import me.superblaubeere27.jobf.processors.InlineTransformer;
import me.superblaubeere27.jobf.processors.InvokeDynamic;
import me.superblaubeere27.jobf.processors.LineNumberRemover;
import me.superblaubeere27.jobf.processors.NumberObfuscationTransformer;
import me.superblaubeere27.jobf.processors.ReferenceProxy;
import me.superblaubeere27.jobf.processors.ShuffleMembersTransformer;
import me.superblaubeere27.jobf.processors.StaticInitializionTransformer;
import me.superblaubeere27.jobf.processors.StringEncryptionTransformer;
import me.superblaubeere27.jobf.processors.flowObfuscation.FlowObfuscator;
import me.superblaubeere27.jobf.processors.name.ClassWrapper;
import me.superblaubeere27.jobf.processors.name.INameObfuscationProcessor;
import me.superblaubeere27.jobf.processors.name.InnerClassRemover;
import me.superblaubeere27.jobf.processors.name.NameObfuscation;
import me.superblaubeere27.jobf.processors.optimizer.Optimizer;
import me.superblaubeere27.jobf.utils.ClassTree;
import me.superblaubeere27.jobf.utils.MissingClassException;
import me.superblaubeere27.jobf.utils.NameUtils;
import me.superblaubeere27.jobf.utils.Utils;
import me.superblaubeere27.jobf.utils.scheduler.ScheduledRunnable;
import me.superblaubeere27.jobf.utils.scheduler.Scheduler;
import me.superblaubeere27.jobf.utils.script.JObfScript;
import me.superblaubeere27.jobf.utils.values.Configuration;
import me.superblaubeere27.jobf.utils.values.Value;
import me.superblaubeere27.jobf.utils.values.ValueManager;

public class JObfImpl {
    private static final Logger log = LoggerFactory.getLogger(JObfImpl.class);
    public static final JObfImpl INSTANCE = new JObfImpl();
    public static List<IClassTransformer> processors;
    public static HashMap<String, ClassNode> classes = new HashMap<>();
    public static HashMap<String, byte[]> files = new HashMap<>();
    private static List<IPreClassTransformer> preProcessors;
    public JObfScript script;
    private boolean mainClassChanged;
    private final List<INameObfuscationProcessor> nameObfuscationProcessors = new ArrayList<>();
    private String mainClass;
    private Map<String, ClassWrapper> classPath = new HashMap<>();
    private Map<String, ClassTree> hierarchy = new HashMap<>();
    private Set<ClassWrapper> libraryClassnodes = new HashSet<>();
    private List<File> libraryFiles;
    private int computeMode;
    private boolean invokeDynamic;
    private final JObfSettings settings = new JObfSettings();
    private int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors());
    private final Map<String, String> classRenameMappings = new HashMap<>();
    private Random random;

    private JObfImpl() {
        this.random = new Random();
        this.threadCount = Runtime.getRuntime().availableProcessors();
        
        // Register settings with ValueManager early
        ValueManager.registerClass(settings);
        
        // Initialize processors
        processors = new ArrayList<>();
        log.info("Initializing processors early to register with ValueManager...");
        addProcessors();
        
        log.info("JObfImpl initialized successfully");
    }

    public static Map<String, ClassNode> getClasses() {
        return classes;
    }

    public String getMainClass() {
        return mainClass;
    }

    private void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public ClassTree getTree(String ref) {
        if (!hierarchy.containsKey(ref)) {
            ClassWrapper wrapper = classPath.get(ref);

            if (wrapper == null)
                return null;

            buildHierarchy(wrapper, null, false);
        }

        return hierarchy.get(ref);
    }

    public void buildHierarchy(ClassWrapper classWrapper, ClassWrapper sub, boolean acceptMissingClass) {
        if (hierarchy.get(classWrapper.classNode.name) == null) {
            ClassTree tree = new ClassTree(classWrapper);
            if (classWrapper.classNode.superName != null) {
                tree.parentClasses.add(classWrapper.classNode.superName);
                ClassWrapper superClass = classPath.get(classWrapper.classNode.superName);

                if (superClass == null && !acceptMissingClass)
                    throw new MissingClassException(classWrapper.classNode.superName + " (referenced in " + classWrapper.classNode.name + ") is missing in the classPath.");
                else if (superClass == null) {
                    tree.missingSuperClass = true;

                    log.warn("Missing class: " + classWrapper.classNode.superName + " (No methods of subclasses will be remapped)");
                } else {
                    buildHierarchy(superClass, classWrapper, acceptMissingClass);

                    // Inherit the missingSuperClass state
                    if (hierarchy.get(classWrapper.classNode.superName).missingSuperClass) {
                        tree.missingSuperClass = true;
                    }
                }
            }
            if (classWrapper.classNode.interfaces != null && !classWrapper.classNode.interfaces.isEmpty()) {
                for (String s : classWrapper.classNode.interfaces) {
                    tree.parentClasses.add(s);
                    ClassWrapper interfaceClass = classPath.get(s);

                    if (interfaceClass == null && !acceptMissingClass)
                        throw new MissingClassException(s + " (referenced in " + classWrapper.classNode.name + ") is missing in the classPath.");
                    else if (interfaceClass == null) {
                        tree.missingSuperClass = true;

                        log.warn("Missing interface class: " + s + " (No methods of subclasses will be remapped)");
                    } else {
                        buildHierarchy(interfaceClass, classWrapper, acceptMissingClass);

                        // Inherit the missingSuperClass state
                        if (hierarchy.get(s).missingSuperClass) {
                            tree.missingSuperClass = true;
                        }
                    }
                }
            }
            hierarchy.put(classWrapper.classNode.name, tree);
        }
        if (sub != null) {
            hierarchy.get(classWrapper.classNode.name).subClasses.add(sub.classNode.name);
        }
    }

    //    private Map<String, ClassWrapper> loadClasspathFile(File file) throws IOException {
//        Map<String, ClassWrapper> map = new HashMap<>();
//
//        ZipFile zipIn = new ZipFile(file);
//        Enumeration<? extends ZipEntry> entries = zipIn.entries();
//        while (entries.hasMoreElements()) {
//            ZipEntry ent = entries.nextElement();
//            if (ent.getName().endsWith(".class")) {
//                byte[] bytes = ByteStreams.toByteArray(zipIn.getInputStream(ent));
//
//                ClassReader reader = new ClassReader(bytes);
//                ClassNode node = new ClassNode();
//                reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
//                map.put(node.name, new ClassWrapper(node, true, bytes));
//            }
//        }
//        zipIn.close();
//
//        return map;
//    }
    private List<byte[]> loadClasspathFile(File file) throws IOException {
        ZipFile zipIn = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = zipIn.entries();

        boolean isJmod = file.getName().endsWith(".jmod");

        List<byte[]> byteList = new ArrayList<>(zipIn.size());

        while (entries.hasMoreElements()) {
            ZipEntry ent = entries.nextElement();
            if (ent.getName().endsWith(".class") && (!isJmod || !ent.getName().endsWith("module-info.class") && ent.getName().startsWith("classes/"))) {
                byteList.add(ByteStreams.toByteArray(zipIn.getInputStream(ent)));
            }
        }
        zipIn.close();

        return byteList;
    }

    private void loadClasspath() throws IOException {
        if (libraryFiles != null) {
            int i = 0;

            LinkedList<byte[]> byteList = new LinkedList<>();

            for (File file : libraryFiles) {
                if (file.isFile()) {
                    log.info("Loading " + file.getAbsolutePath() + " (" + (i++ * 100 / libraryFiles.size()) + "%)");
                    byteList.addAll(loadClasspathFile(file));
//                    classPath.putAll(loadClasspathFile(file));
                } else {
                    Files.walk(file.toPath()).map(Path::toFile).filter(f -> f.getName().endsWith(".jar") || f.getName().endsWith(".zip") || f.getName().endsWith(".jmod")).forEach(f -> {
                        log.info("Loading " + f.getName() + " (from " + file.getAbsolutePath() + ") to memory");
                        try {
                            byteList.addAll(loadClasspathFile(f));
//                            classPath.putAll(loadClasspathFile(f));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            }

            log.info("Read " + byteList.size() + " class files to memory");
            log.info("Parsing class files...");

            ScheduledRunnable runnable = () -> {
                Map<String, ClassWrapper> map = new HashMap<>();

                while (true) {
                    byte[] bytes;

                    synchronized (byteList) {
                        bytes = byteList.poll();
                    }

                    if (bytes == null) break;

                    ClassReader reader = new ClassReader(bytes);
                    ClassNode node = new ClassNode();
                    reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    map.put(node.name, new ClassWrapper(node, true, bytes));
                }

                synchronized (classPath) {
                    classPath.putAll(map);
                }

                return true;
            };

            Scheduler scheduler = new Scheduler(runnable);

            scheduler.run(threadCount);
            scheduler.waitFor();

        }

        libraryClassnodes.addAll(classPath.values());
    }

    public Map<String, ClassWrapper> getClassPath() {
        return classPath;
    }

    public boolean isLibrary(ClassNode classNode) {
        return libraryClassnodes.stream().anyMatch(e -> e.classNode.name.equals(classNode.name));
    }

    public boolean isLoadedCode(ClassNode classNode) {
        return classes.containsKey(classNode.name);
    }

    private void addProcessors() {
        log.info("Initializing processors...");
        processors.add(new StaticInitializionTransformer(this));

        processors.add(new Optimizer());
        processors.add(new InlineTransformer(this));
        processors.add(new InvokeDynamic());

        processors.add(new StringEncryptionTransformer(this));
        processors.add(new NumberObfuscationTransformer(this));
        processors.add(new FlowObfuscator(this));
        processors.add(new HideMembers(this));
        processors.add(new LineNumberRemover(this));
        processors.add(new ShuffleMembersTransformer(this));


        nameObfuscationProcessors.add(new NameObfuscation());
        nameObfuscationProcessors.add(new InnerClassRemover());
        processors.add(new CrasherTransformer(this));
        processors.add(new ReferenceProxy(this));

        preProcessors = new ArrayList<>();

        log.info("Registering processors with ValueManager...");
        // Register all processors with the ValueManager
        for (IClassTransformer processor : processors) {
            ValueManager.registerClass(processor);
            log.debug("Registered processor: {}", processor.getClass().getSimpleName());
        }
        for (IPreClassTransformer processor : preProcessors) {
            ValueManager.registerClass(processor);
            log.debug("Registered pre-processor: {}", processor.getClass().getSimpleName());
        }
        for (INameObfuscationProcessor processor : nameObfuscationProcessors) {
            ValueManager.registerClass(processor);
            log.debug("Registered name obfuscation processor: {}", processor.getClass().getSimpleName());
        }
        
        // Debug: dump all registered values
        ValueManager.dumpRegisteredValues();
        log.info("All processors initialized and registered");
    }

    public void setScript(JObfScript script) {
        this.script = script;
    }

    public void processJar(Configuration config) throws IOException {
        log.info("Starting JAR processing with configuration...");
        
        // Apply configuration values to settings FIRST, before any other operations
        applyConfigToSettings(config);
        
        ZipInputStream inJar = null;
        ZipOutputStream outJar = null;

        boolean stored = settings.getUseStore().getObject();

        libraryFiles = new ArrayList<>();

        classes = new HashMap<>();
        libraryClassnodes = new HashSet<>();
        classPath = new HashMap<>();
        files = new HashMap<>();
        hierarchy = new HashMap<>();

        // Apply settings to NameUtils AFTER config has been applied
        log.info("Applying settings to name utils...");
        NameUtils.applySettings(settings);
        NameUtils.setup();

        try {
            script = StringUtils.isBlank(config.getScript()) ? null : new JObfScript(config.getScript());
        } catch (Exception e) {
            log.error("Failed to load script", e);
            e.printStackTrace();
            return;
        }

        for (String s : config.getLibraries()) libraryFiles.add(new File(s));

        long startTime = System.currentTimeMillis();

        try {
            log.info("Loading classpath...");
            loadClasspath();
            try {
                inJar = new ZipInputStream(new BufferedInputStream(new FileInputStream(config.getInput())));
            } catch (FileNotFoundException e) {
                throw new FileNotFoundException("Could not open input file: " + e.getMessage());
            }

            try {
                OutputStream out = (config.getOutput() == null ? new ByteArrayOutputStream() : new FileOutputStream(config.getOutput()));
                outJar = new ZipOutputStream(new BufferedOutputStream(out));
                outJar.setMethod(stored ? ZipOutputStream.STORED : ZipOutputStream.DEFLATED);

                if (stored) {
                    outJar.setLevel(Deflater.NO_COMPRESSION);
                }
            } catch (FileNotFoundException e) {
                throw new FileNotFoundException("Could not open output file: " + e.getMessage());
            }
            setMainClass(null);

            log.info("... Finished after " + Utils.formatTime(System.currentTimeMillis() - startTime));

            startTime = System.currentTimeMillis();

            log.info("Reading input...");

            HashMap<String, byte[]> classDataMap = new HashMap<>();

            while (true) {
                ZipEntry entry = inJar.getNextEntry();

                if (entry == null) {
                    break;
                }

                if (entry.isDirectory()) {
                    outJar.putNextEntry(entry);
                    continue;
                }

                byte[] data = new byte[4096];
                ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();

                int len;
                do {
                    len = inJar.read(data);
                    if (len > 0) {
                        entryBuffer.write(data, 0, len);
                    }
                } while (len != -1);

                byte[] entryData = entryBuffer.toByteArray();

                String entryName = entry.getName();

                if (entryName.endsWith(".class")) {
                    try {
                        ClassReader cr = new ClassReader(entryData);
                        ClassNode cn = new ClassNode();


                        //ca = new LineInjectorAdaptor(ASM4, cn);

                        cr.accept(cn, 0);
                        classes.put(entryName, cn);
                        classDataMap.put(entryName, entryData);
                    } catch (Exception e) {
                        log.warn("Failed to read class " + entryName);
                        e.printStackTrace();
                        files.put(entryName, entryData);
                    }

                } else {
                    if (entryName.equals("META-INF/MANIFEST.MF")) {
                        // Check if the main class has been renamed
                        String originalMainClass = Utils.getMainClass(new String(entryData, StandardCharsets.UTF_8));
                        
                        if (originalMainClass != null && !originalMainClass.isEmpty()) {
                            // Convert dots to slashes in the class name for internal format
                            String internalClassName = originalMainClass.replace('.', '/');
                            
                            // Check if this class was renamed
                            if (classRenameMappings.containsKey(internalClassName)) {
                                // Get the new name and convert slashes back to dots for the manifest
                                String newName = classRenameMappings.get(internalClassName);
                                mainClass = newName.replace('/', '.');
                                mainClassChanged = true;
                                log.info("Main class has been renamed from " + originalMainClass + " to " + mainClass);
                            } else {
                                log.warn("Original main class " + originalMainClass + " not found in rename mappings. The JAR may not be executable.");
                            }
                        }
                        
                        if (mainClassChanged) {
                            entryData = Utils.replaceMainClass(new String(entryData, StandardCharsets.UTF_8), mainClass).getBytes(StandardCharsets.UTF_8);
                            log.info("Replaced Main-Class with " + mainClass);
                        }

                        log.info("Processed MANIFEST.MF");
                    }

                    files.put(entryName, entryData);
                }
            }

            for (Map.Entry<String, ClassNode> stringClassNodeEntry : classes.entrySet()) {
                classPath.put(stringClassNodeEntry.getKey().replace(".class", ""), new ClassWrapper(stringClassNodeEntry.getValue(), false, classDataMap.get(stringClassNodeEntry.getKey())));
            }
            for (ClassNode value : classes.values()) {
                libraryClassnodes.add(new ClassWrapper(value, false, null));
            }

//            if (nameobf) {
            for (INameObfuscationProcessor nameObfuscationProcessor : nameObfuscationProcessors) {
                nameObfuscationProcessor.transformPost(this, classes);
            }
            for (IPreClassTransformer preProcessor : preProcessors) {
                preProcessor.process(classes.values());
            }
//            }

            AtomicInteger processed = new AtomicInteger();

            log.info("... Finished after " + Utils.formatTime(System.currentTimeMillis() - startTime));

            startTime = System.currentTimeMillis();

            log.info("Transforming with " + threadCount + " threads...");

            final LinkedList<Map.Entry<String, ClassNode>> classQueue = new LinkedList<>(classes.entrySet());

            HashMap<String, byte[]> toWrite = new HashMap<>();

            List<Thread> threads = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
//                ZipOutputStream finalOutJar = outJar;


                Thread t = new Thread(() -> {
                    try {
                        while (true) {
                            Map.Entry<String, ClassNode> stringClassNodeEntry;

                            synchronized (classQueue) {
                                stringClassNodeEntry = classQueue.poll();
                            }

                            if (stringClassNodeEntry == null) break;

                            ProcessorCallback callback = new ProcessorCallback();

                            String entryName = stringClassNodeEntry.getKey();
                            byte[] entryData;
                            ClassNode cn = stringClassNodeEntry.getValue();

                            try {
                                try {

                                    computeMode = ModifiedClassWriter.COMPUTE_MAXS;


                                    if (script == null || script.isObfuscatorEnabled(cn)) {
                                        log.info(String.format("[%s] (%s/%s), Processing %s", Thread.currentThread().getName(), processed, classes.size(), entryName));

                                        for (IClassTransformer proc : processors) {
                                            try {
                                                proc.process(callback, cn);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    } else {
                                        log.info(String.format("[%s] (%s/%s), Skipping %s", Thread.currentThread().getName(), processed, classes.size(), entryName));
                                    }

                                    if (callback.isForceComputeFrames())
                                        cn.methods.forEach(method -> Arrays.stream(method.instructions.toArray()).filter(abstractInsnNode -> abstractInsnNode instanceof FrameNode).forEach(abstractInsnNode -> method.instructions.remove(abstractInsnNode)));


                                    int mode = computeMode
                                            | (callback.isForceComputeFrames() ? ModifiedClassWriter.COMPUTE_FRAMES : 0);

                                    log.info(String.format("[%s] (%s/%s), Writing (computeMode = %s) %s", Thread.currentThread().getName(), processed, classes.size(), mode, entryName));

                                    ModifiedClassWriter writer = new ModifiedClassWriter(
                                            mode
//                                            ModifiedClassWriter.COMPUTE_MAXS |
//                                            ModifiedClassWriter.COMPUTE_FRAMES
                                    );
                                    cn.accept(writer);

                                    entryData = writer.toByteArray();
                                } catch (Throwable e) {
                                    System.err.println("Error while writing " + entryName);
                                    e.printStackTrace();
//                                    if (e instanceof) {
//
//                                    }
                                    ModifiedClassWriter writer = new ModifiedClassWriter(ModifiedClassWriter.COMPUTE_MAXS
                                            //                            | ModifiedClassWriter.COMPUTE_FRAMES
                                    );
                                    cn.accept(writer);


                                    entryData = writer.toByteArray();
                                }

                                synchronized (toWrite) {
                                    toWrite.put(entryName, entryData);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            processed.incrementAndGet();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                
                t.setName("Thread-" + i);
                t.setContextClassLoader(ObfuscatorClassLoader.INSTANCE);
                t.start();

                threads.add(t);
            }

            while (true) {
                synchronized (threads) {
                    if (threads.isEmpty()) break;

                    threads.stream().filter(thread -> thread == null || !thread.isAlive()).collect(Collectors.toList()).forEach(threads::remove);
                }

                Thread.sleep(100);
            }

            log.info("... Finished after " + Utils.formatTime(System.currentTimeMillis() - startTime));

            startTime = System.currentTimeMillis();

            log.info("Writing classes...");

            for (Map.Entry<String, byte[]> stringEntry : toWrite.entrySet()) {
                writeEntry(outJar, stringEntry.getKey(), stringEntry.getValue(), stored);
            }

            log.info("... Finished after " + Utils.formatTime(System.currentTimeMillis() - startTime));

            startTime = System.currentTimeMillis();

            log.info("Writing resources...");

            for (Map.Entry<String, byte[]> stringEntry : files.entrySet()) {
                String entryName = stringEntry.getKey();
                byte[] entryData = stringEntry.getValue();

                if (entryName.equals("META-INF/MANIFEST.MF")) {
                    // Check if the main class has been renamed
                    String originalMainClass = Utils.getMainClass(new String(entryData, StandardCharsets.UTF_8));
                    
                    if (originalMainClass != null && !originalMainClass.isEmpty()) {
                        // Convert dots to slashes in the class name for internal format
                        String internalClassName = originalMainClass.replace('.', '/');
                        
                        // Check if this class was renamed
                        if (classRenameMappings.containsKey(internalClassName)) {
                            // Get the new name and convert slashes back to dots for the manifest
                            String newName = classRenameMappings.get(internalClassName);
                            mainClass = newName.replace('/', '.');
                            mainClassChanged = true;
                            log.info("Main class has been renamed from " + originalMainClass + " to " + mainClass);
                        } else {
                            log.warn("Original main class " + originalMainClass + " not found in rename mappings. The JAR may not be executable.");
                        }
                    }
                    
                    if (mainClassChanged) {
                        entryData = Utils.replaceMainClass(new String(entryData, StandardCharsets.UTF_8), mainClass).getBytes(StandardCharsets.UTF_8);
                        log.info("Replaced Main-Class with " + mainClass);
                    }

                    log.info("Processed MANIFEST.MF");
                }
                log.info("Copying " + entryName);

                writeEntry(outJar, entryName, entryData, stored);
            }

            log.info("... Finished after " + Utils.formatTime(System.currentTimeMillis() - startTime));

            startTime = System.currentTimeMillis();
        } catch (InterruptedException ignored) {
        } finally {
            classPath.clear();
            classes.clear();
            libraryFiles.clear();
            libraryClassnodes.clear();
            files.clear();
            hierarchy.clear();

            NameUtils.cleanUp();

            System.gc();

            if (outJar != null) {
                try {
                    log.info("Finishing...");
                    outJar.flush();
                    outJar.close();
                    log.info(">>> Processing completed. If you found a bug / if the output is invalid please open an issue at https://github.com/superblaubeere27/obfuscator/issues");
                } catch (Exception e) {
                    // ignore
                }
            }

            if (inJar != null) {
                try {
                    inJar.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public void writeEntry(ZipOutputStream outJar, String name, byte[] value, boolean stored) throws IOException {
        ZipEntry newEntry = new ZipEntry(name);


        if (stored) {
            CRC32 crc = new CRC32();
            crc.update(value);

            newEntry.setSize(value.length);
            newEntry.setCrc(crc.getValue());
        }


        outJar.putNextEntry(newEntry);
        outJar.write(value);
    }

    public void setWorkDone() {
        boolean workDone = true;
    }


    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * Register a class renaming from original name to new name
     * @param originalName Original class name with slashes (e.g., org/example/Main)
     * @param newName New class name with slashes (e.g., a/b/C)
     */
    public void registerClassRename(String originalName, String newName) {
        classRenameMappings.put(originalName, newName);
        log.info("Registered class rename: " + originalName + " -> " + newName);
    }

    /**
     * Applies configuration values from the loaded config to the JObfSettings object
     * before it's used in the obfuscation process.
     *
     * @param config The loaded configuration
     */
    private void applyConfigToSettings(Configuration config) {
        log.info("Applying configuration values to settings...");
        
        // Get the general settings values from ValueManager
        Map<String, Value<?>> generalSettings = ValueManager.getValuesForOwner("General Settings");
        
        if (generalSettings != null && !generalSettings.isEmpty()) {
            log.info("Found {} General Settings values in the configuration", generalSettings.size());
            
            // Generator characters
            Value<?> generatorCharsValue = generalSettings.get("Generator characters");
            if (generatorCharsValue != null) {
                String configValue = generatorCharsValue.getObject().toString();
                settings.getGeneratorChars().setObject(configValue);
                log.info("Updated generator characters from config: {}", configValue);
            }
            
            // Custom dictionary setting
            Value<?> customDictValue = generalSettings.get("Custom dictionary");
            if (customDictValue != null && customDictValue.getObject() instanceof Boolean) {
                boolean useCustomDict = (Boolean) customDictValue.getObject();
                settings.getUseCustomDictionary().setObject(useCustomDict);
                log.info("Updated custom dictionary setting from config: {}", useCustomDict);
            }
            
            // Name dictionary
            Value<?> nameDictValue = generalSettings.get("Name dictionary");
            if (nameDictValue != null) {
                String nameDict = nameDictValue.getObject().toString();
                settings.getNameDictionary().setObject(nameDict);
                log.info("Updated name dictionary from config: {}", nameDict);
            }
            
            // Class name dictionary
            Value<?> classNameDictValue = generalSettings.get("Class Name dictionary");
            if (classNameDictValue != null) {
                String classNameDict = classNameDictValue.getObject().toString();
                settings.getClassNameDictionary().setObject(classNameDict);
                log.info("Updated class name dictionary from config: {}", classNameDict);
            }
            
            // STORE setting
            Value<?> useStoreValue = generalSettings.get("Use STORE instead of DEFLATE");
            if (useStoreValue != null && useStoreValue.getObject() instanceof Boolean) {
                boolean useStore = (Boolean) useStoreValue.getObject();
                settings.getUseStore().setObject(useStore);
                log.info("Updated STORE setting from config: {}", useStore);
            }
        } else {
            log.warn("No general settings found in configuration. Using default values.");
        }
    }
}
