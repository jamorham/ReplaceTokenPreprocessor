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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.tasks.TaskProvider;

import java.util.Collections;
import java.util.List;

import static com.github.jamorham.android.replace.token.PreprocessorTask.ANDROID_MANIFEST;

@SuppressWarnings({"unused"})
public class PreprocessorPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Register replace preprocessor extension
        final PreprocessorExtension extension = project.getExtensions().create(
                PreprocessorExtension.EXTENSION_NAME
                , PreprocessorExtension.class
                , project);

        // Register replace token preprocessor task
        final TaskProvider<PreprocessorTask> replaceTokenPreprocessorTask = project.getTasks().register(
                PreprocessorTask.TASK_ID
                , PreprocessorTask.class);

        final List<String> tasks = project.getGradle().getStartParameter().getTaskNames();

        if (!tasks.isEmpty()
                && (!(tasks.size() == 1 && tasks.get(0).equals("clean")))) {
            project.getTasks().forEach((task) -> {
                if (task.getName().startsWith("pre")) {
                    task.dependsOn(replaceTokenPreprocessorTask);
                }
            });

            try {

                final AppExtension appExtension = (AppExtension) project.getExtensions().getByName("android");
                final String targetPath = new PreprocessorExtension(project).getTarget().getAbsolutePath();
                appExtension.getSourceSets().all(sourceSet -> {
                            sourceSet.getJava().setSrcDirs(Collections.singleton(targetPath + "/java"));
                            sourceSet.getManifest().srcFile(targetPath + "/" + ANDROID_MANIFEST);
                        }
                );

                try {
                    final String firstTask = project.getGradle().getStartParameter().getTaskNames().get(0);
                    final Task preprocessorTask = project.getTasks().getByName(firstTask);
                    preprocessorTask.dependsOn(replaceTokenPreprocessorTask);
                } catch (UnknownTaskException ignored) {
                }

            } catch (UnknownDomainObjectException e) {
                final String error_message = "Cannot find Android gradle plugin - This plugin must be called after the android plugin within build.gradle - adjust your apply plugin order";
                System.out.println(error_message);
                throw new RuntimeException(error_message + "\n" + e);
            }
        } else {
            //System.out.println("Gradle Syncing invocation");
        }

    }
}
