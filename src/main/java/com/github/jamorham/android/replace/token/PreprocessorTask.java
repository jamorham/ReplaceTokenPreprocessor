/*
 * This file is part of ReplaceTokenPreprocessor, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Hexosse <https://github.com/hexomod-tools/gradle.replace.token.preprocessor.plugin>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.jamorham.android.replace.token;

import com.android.build.gradle.AppExtension;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

@SuppressWarnings({"WeakerAccess", "unused"})
public class PreprocessorTask extends DefaultTask {

    // The task ID
    public static final String TASK_ID = "replacePreprocessor";
    public static final String TASK_ID_CLEAN = "replacePreprocessorClean";

    // Manifest
    public static final String ANDROID_MANIFEST = "AndroidManifest.xml";

    // Extension
    private final PreprocessorExtension extension;

    @Inject
    public PreprocessorTask() {
        this.extension = getProject().getExtensions().findByType(PreprocessorExtension.class);
    }

    @TaskAction
    public synchronized void process() throws IOException {

        final long startTime = System.currentTimeMillis();
        boolean setSourcesDefaults = false;
        boolean setResourcesDefaults = false;

         try {

            extension.getLock().lock();

            final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
            // Instantiate the preprocessor
            final Preprocessor preprocessor = new Preprocessor(this.extension.getExtensions(), this.extension.getReplace(), this.extension.isVerbose());

            log("Starting android replace token preprocessor");

            // Data
            final Project project = this.extension.getProject();
            final Set<String> sources = this.extension.getSources();
            final Set<String> resources = this.extension.getResources();

            final File target = this.extension.getTarget();
            final File resTarget = new File(target, "main/res");
            final File manifestTarget = new File(target, ANDROID_MANIFEST);

            log("  Checking sources folders...");

            if (sources.isEmpty()) {
                extension.setSource(Stream.of("src/main/java", "src/test/java").collect(Collectors.toList()));
                setSourcesDefaults = true;
            }

            if (resources.isEmpty()) {
                extension.setResource("src/main/res");
                setResourcesDefaults = true;
            }

            // Check
            for (String source : sources) {
                log("Checking source: " + source);
                if (!Files.isDirectory((new File(source)).toPath())) {
                    log("    " + source + " is not a valid folder!");
                }
            }

            log("  Processing files...");

            // Loop through all source files
            for (final String source : sources) {
                executor.submit(() -> {
                    final String pair = getFolderPair(source);
                    if (pair != null) {
                        final File srcTarget = new File(target, pair);
                        processFolder(source, srcTarget, project, preprocessor);
                        processManifest(source, manifestTarget, preprocessor);
                    } else {
                        final String error_message = "Failure to parse source folder: " + source;
                        log(error_message);
                        throw new RuntimeException(error_message);
                    }
                });
            } // per source folder

            // Loop through all resource files
            for (String resource : resources) {
                executor.submit(() -> {
                    processFolder(resource, resTarget, project, preprocessor);
                });
            } // per resource folder

            executor.shutdown();    // prepare to end threading
            try {
                while (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log("Awaiting completion of threads.");
                }
            } catch (InterruptedException e) {
                log("Got interruption waiting for threads to finish! " + e);
            }

            final AppExtension extension = (AppExtension) project.getExtensions().getByName("android");

            extension.getSourceSets().all(sourceSet -> {
                        log("Source set: " + sourceSet + " " + sourceSet.getRes().getSrcDirs().toString());
                        //sourceSet.getJava().setSrcDirs(Collections.singleton(srcTarget));       // warning this is probably too late in the sequence
                        sourceSet.getRes().setSrcDirs(Collections.singleton(resTarget));
                    }
            );

            if (setSourcesDefaults) {
                sources.clear();
            }
            if (setResourcesDefaults) {
                resources.clear();
            }

        } finally {
            extension.getLock().unlock();
        }
        final long duration = System.currentTimeMillis() - startTime;
        log("Finished processing in " + duration + " ms");

    }

    private String getFolderPair(final String path) {
        final String[] pathA = path.split("/");
        final int l = pathA.length;
        if (l < 4) return null;
        return pathA[l - 2] + "/" + pathA[l - 1];
    }

    private void processFolder(final String source, final File target, final Project project, final Preprocessor preprocessor) {
        try {
            FileUtils.forceMkdirParent(target);
        } catch (IOException e) {
            //
        }
        final HashSet<String> files = new HashSet<>();
        final File srcDir = new File(source);
        for (final File file : project.fileTree(srcDir)) {
            log("Processing " + file.toString());
            final File out = target.toPath().resolve(srcDir.toPath().relativize(file.toPath())).toFile();
            files.add(out.getAbsolutePath());
            preprocessor.process(file, out);
        }
        log("Processed " + files.size() + " files in " + source);
        removeNotInSet(files, project.fileTree(target));
    }

    private void processManifest(final String source, final File manifestTarget, final Preprocessor preprocessor) {
        // Special handling of manifest
        final File srcDir = new File(source);
        final File manifest = new File(srcDir.getParent() + "/" + ANDROID_MANIFEST);
        log("manifest: " + manifest.getAbsolutePath());
        if (manifest.exists()) {
            preprocessor.process(manifest, manifestTarget);
        }
    }

    private void removeNotInSet(final HashSet<String> files, final FileTree tree) {
        for (final File file : tree) {
            if (!files.contains(file.getAbsolutePath())) {
                log("removing file not in source tree: " + file.getAbsolutePath() + " success: " + file.delete());
            }
        }
    }

    // Print out a string if verbose is enable
    private void log(final String msg) {
        if (this.extension != null && this.extension.isVerbose()) {
            System.out.println("Replace Plugin: " + msg);
        }
    }
}
