package com.backtester.strategy.loader;

import com.backtester.strategy.Strategy;
import com.backtester.strategy.ml.OnnxStrategy;

import javax.tools.*;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Dynamic strategy loader supporting:
 * - .java source files (compiled on-the-fly)
 * - .class compiled files
 * - .jar files containing strategy classes
 * - .onnx model files (creates OnnxStrategy wrapper)
 *
 * Supports drag-and-drop loading via UI.
 */
public class StrategyLoader {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private final List<LoadedStrategy> loadedStrategies = new ArrayList<>();

    /**
     * Load a strategy from a file.
     *
     * @param file Path to the strategy file
     * @return Loaded strategy info, or null on failure
     */
    public LoadedStrategy loadStrategy(Path file) throws StrategyLoadException {
        String fileName = file.getFileName().toString().toLowerCase();

        try {
            LoadedStrategy loaded;

            if (fileName.endsWith(".java")) {
                loaded = loadJavaSource(file);
            } else if (fileName.endsWith(".class")) {
                loaded = loadCompiledClass(file);
            } else if (fileName.endsWith(".jar")) {
                loaded = loadJarFile(file);
            } else if (fileName.endsWith(".onnx")) {
                loaded = loadOnnxModel(file);
            } else {
                throw new StrategyLoadException("Unsupported file type: " + fileName);
            }

            loadedStrategies.add(loaded);
            return loaded;

        } catch (Exception e) {
            throw new StrategyLoadException("Failed to load strategy: " + e.getMessage(), e);
        }
    }

    /**
     * Load strategy from .java source file.
     * Compiles the source on-the-fly.
     */
    private LoadedStrategy loadJavaSource(Path sourcePath) throws Exception {
        String sourceCode = Files.readString(sourcePath);

        // Extract class name from source
        String className = extractClassName(sourceCode);
        if (className == null) {
            throw new StrategyLoadException("Could not determine class name from source");
        }

        // Extract package name
        String packageName = extractPackageName(sourceCode);
        String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;

        // Create temp directory for compilation
        Path tempDir = Files.createTempDirectory("strategy-compile");
        Path outputDir = tempDir;

        // If there's a package, create package directories
        if (!packageName.isEmpty()) {
            Path packageDir = tempDir.resolve(packageName.replace('.', File.separatorChar));
            Files.createDirectories(packageDir);
        }

        // Copy source to temp location with correct name
        Path tempSource = tempDir.resolve(className + ".java");
        Files.writeString(tempSource, sourceCode);

        // Get Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new StrategyLoadException("Java compiler not available. Ensure JDK is installed.");
        }

        // Compile with classpath including strategy module
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        // Get current classpath
        String classpath = System.getProperty("java.class.path");

        Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromFiles(List.of(tempSource.toFile()));

        List<String> options = new ArrayList<>();
        options.add("-classpath");
        options.add(classpath);
        options.add("-d");
        options.add(outputDir.toString());

        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, compilationUnits);

        boolean success = task.call();
        fileManager.close();

        if (!success) {
            StringBuilder errors = new StringBuilder("Compilation failed:\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                errors.append(String.format("Line %d: %s%n",
                        diagnostic.getLineNumber(), diagnostic.getMessage(null)));
            }
            throw new StrategyLoadException(errors.toString());
        }

        // Load compiled class
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{outputDir.toUri().toURL()},
                getClass().getClassLoader()
        );

        Class<?> strategyClass = classLoader.loadClass(fullClassName);
        return createLoadedStrategy(strategyClass, sourcePath, LoadedStrategy.SourceType.JAVA_SOURCE);
    }

    /**
     * Load strategy from .class file.
     */
    private LoadedStrategy loadCompiledClass(Path classPath) throws Exception {
        // Determine class name from file name
        String fileName = classPath.getFileName().toString();
        String className = fileName.substring(0, fileName.length() - 6); // Remove .class

        // Load from parent directory
        Path parentDir = classPath.getParent();
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{parentDir.toUri().toURL()},
                getClass().getClassLoader()
        );

        Class<?> strategyClass = classLoader.loadClass(className);
        return createLoadedStrategy(strategyClass, classPath, LoadedStrategy.SourceType.COMPILED_CLASS);
    }

    /**
     * Load strategy from .jar file.
     */
    private LoadedStrategy loadJarFile(Path jarPath) throws Exception {
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                getClass().getClassLoader()
        );

        // Search for strategy classes in JAR
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            Class<?> strategyClass = null;

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class") && !name.contains("$")) {
                    String className = name.replace('/', '.').substring(0, name.length() - 6);
                    try {
                        Class<?> clazz = classLoader.loadClass(className);
                        if (Strategy.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                            strategyClass = clazz;
                            break;
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    }
                }
            }

            if (strategyClass == null) {
                throw new StrategyLoadException("No Strategy implementation found in JAR");
            }

            return createLoadedStrategy(strategyClass, jarPath, LoadedStrategy.SourceType.JAR_FILE);
        }
    }

    /**
     * Load ONNX model and wrap in OnnxStrategy.
     */
    private LoadedStrategy loadOnnxModel(Path modelPath) throws Exception {
        // Create a dynamic OnnxStrategy subclass for this model
        OnnxModelStrategy strategy = new OnnxModelStrategy(modelPath);

        return new LoadedStrategy(
                strategy.getName(),
                strategy,
                modelPath,
                LoadedStrategy.SourceType.ONNX_MODEL,
                null
        );
    }

    /**
     * Create LoadedStrategy from a class.
     */
    private LoadedStrategy createLoadedStrategy(Class<?> clazz, Path sourcePath,
                                                 LoadedStrategy.SourceType sourceType) throws Exception {
        if (!Strategy.class.isAssignableFrom(clazz)) {
            throw new StrategyLoadException("Class does not implement Strategy interface: " + clazz.getName());
        }

        @SuppressWarnings("unchecked")
        Class<? extends Strategy> strategyClass = (Class<? extends Strategy>) clazz;

        Strategy instance = strategyClass.getDeclaredConstructor().newInstance();

        return new LoadedStrategy(
                instance.getName(),
                instance,
                sourcePath,
                sourceType,
                strategyClass
        );
    }

    /**
     * Extract class name from Java source code.
     */
    private String extractClassName(String source) {
        // Simple regex-free extraction
        int classIndex = source.indexOf("class ");
        if (classIndex == -1) return null;

        int start = classIndex + 6;
        int end = start;
        while (end < source.length()) {
            char c = source.charAt(end);
            if (!Character.isJavaIdentifierPart(c)) break;
            end++;
        }
        return source.substring(start, end).trim();
    }

    /**
     * Extract package name from Java source code.
     */
    private String extractPackageName(String source) {
        int packageIndex = source.indexOf("package ");
        if (packageIndex == -1) return "";

        int start = packageIndex + 8;
        int end = source.indexOf(';', start);
        if (end == -1) return "";

        return source.substring(start, end).trim();
    }

    /**
     * Get all loaded strategies.
     */
    public List<LoadedStrategy> getLoadedStrategies() {
        return new ArrayList<>(loadedStrategies);
    }

    /**
     * Clear all loaded strategies.
     */
    public void clear() {
        loadedStrategies.clear();
    }

    /**
     * Dynamic OnnxStrategy wrapper for loaded models.
     */
    private static class OnnxModelStrategy extends OnnxStrategy {
        private final String modelName;

        public OnnxModelStrategy(Path modelPath) {
            this.modelName = modelPath.getFileName().toString().replace(".onnx", "");
            setModelPath(modelPath);
        }

        @Override
        public String getName() {
            return "ML: " + modelName;
        }
    }

    /**
     * Information about a loaded strategy.
     */
    public static class LoadedStrategy {
        private final String name;
        private final Strategy instance;
        private final Path sourcePath;
        private final SourceType sourceType;
        private final Class<? extends Strategy> strategyClass;

        public LoadedStrategy(String name, Strategy instance, Path sourcePath,
                              SourceType sourceType, Class<? extends Strategy> strategyClass) {
            this.name = name;
            this.instance = instance;
            this.sourcePath = sourcePath;
            this.sourceType = sourceType;
            this.strategyClass = strategyClass;
        }

        public String getName() { return name; }
        public Strategy getInstance() { return instance; }
        public Path getSourcePath() { return sourcePath; }
        public SourceType getSourceType() { return sourceType; }
        public Class<? extends Strategy> getStrategyClass() { return strategyClass; }

        /**
         * Create a new instance of this strategy.
         */
        public Strategy newInstance() {
            if (strategyClass != null) {
                try {
                    return strategyClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create strategy instance", e);
                }
            }
            return instance; // For ONNX strategies, reuse same instance
        }

        public enum SourceType {
            JAVA_SOURCE,
            COMPILED_CLASS,
            JAR_FILE,
            ONNX_MODEL,
            BUILTIN
        }
    }

    /**
     * Exception for strategy loading errors.
     */
    public static class StrategyLoadException extends Exception {
        public StrategyLoadException(String message) {
            super(message);
        }

        public StrategyLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
