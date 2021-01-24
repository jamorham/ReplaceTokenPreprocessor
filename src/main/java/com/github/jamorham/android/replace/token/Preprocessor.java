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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//@SuppressWarnings({"WeakerAccess", "unused"})
public class Preprocessor {

    private final List<String> extensions;
    private final Map<String, Object> replace;
    private final Map<Pattern, String> replacePatterns = new LinkedHashMap<>();
    private final boolean verbose;

    public Preprocessor(final Set<String> extensions, final Map<String, Object> replace) {
        this(extensions, replace, false);
    }

    public Preprocessor(final Set<String> extensions, final Map<String, Object> replace, final boolean verbose) {
        this.extensions = new ArrayList<>(extensions);
        this.replace = replace;
        this.verbose = verbose;

        // Precompile the patterns for maximum efficiency
        this.replace.forEach((key, value) -> {
            replacePatterns.put(Pattern.compile(key, Pattern.LITERAL), Matcher.quoteReplacement(value.toString()));
        });

        // Check for hash map inconsistency - shouldn't happen
        if (replacePatterns.size() != replace.size()) {
            throw new RuntimeException("Pattern matching compilation error - do you have duplicate patterns described?");
        }

    }

    public void process(final File inFile, final File outFile) {
        final String fileExtension = FilenameUtils.getExtension(inFile.getName());
        // First check if the file need to be processed
        try {
            // If not, the file is just copied to its destination
            if (!this.extensions.contains(fileExtension)) {
                if (!outFile.exists()
                        || inFile.lastModified() != outFile.lastModified()
                        || inFile.length() != outFile.length()) {
                    if (verbose) System.out.println("COPY " + inFile + " -> " + outFile);
                    FileUtils.copyFile(inFile, outFile);
                    outFile.setLastModified(inFile.lastModified());
                } else {
                    if (verbose) System.out.println("No need to copy " + inFile);
                }
            }
            // If yes, the file is processed
            else {
                if (!outFile.exists()
                        || inFile.lastModified() != outFile.lastModified()
                        || inFile.length() != outFile.length()) {
                    if (verbose)
                        System.out.println("PROCESS " + inFile + " -> " + outFile);
                    //
                    try {

                        String content = FileUtils.readFileToString(inFile, StandardCharsets.UTF_8);
                        content = processLine(content);
                        FileUtils.writeStringToFile(outFile, content, StandardCharsets.UTF_8);
                        outFile.setLastModified(inFile.lastModified());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to convert file " + inFile, e);
                    }
                } else {
                    if (verbose) System.out.println("No need to process " + inFile);
                }
            }
        } catch (IOException e) {
            System.out.println("Preprocessor got exception with IO operation: " + e);
            throw new RuntimeException("Preprocessor exception: " + e);
        }
    }

    String processLine(String line) {
        final String[] newLine = {line};
        this.replacePatterns.forEach((key, value) -> {
            newLine[0] = key.matcher(newLine[0]).replaceAll(value);
        });
        return newLine[0];
    }
}
