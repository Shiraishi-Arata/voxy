package me.cortex.voxy.client.core.gl.shader;


import net.minecraft.resources.Identifier;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class ShaderLoader {
    public static String parse(String id) {
        var identifier = Identifier.parse(id);
        var src = defaultVersionForShaderPath(identifier.getPath());
        src += String.join("\n", ShaderLoadingParser.parseRoot(identifier));
        return src;
    }

    private static String defaultVersionForShaderPath(String shaderPath) {
        return shaderPath.endsWith(".comp") ? "#version 460\n" : "#version 320 es\n";
    }


    //Use our own loader

    private static final class ShaderLoadingParser {
        private static final Pattern IMPORT_PATTERN = Pattern.compile("#import <(?<namespace>.*):(?<path>.*)>");
        public static List<String> parseRoot(Identifier id) {
            List<String> out = new ArrayList<>();
            for (var line : toLines(loadShaderAsset(id))) {
                if (line.startsWith("#version")) {
                    continue;
                } else if (line.startsWith("#import")) {
                    var match = IMPORT_PATTERN.matcher(line);
                    if (!match.matches()) throw new IllegalArgumentException("Unknown import: " + line);
                    var iid = Identifier.fromNamespaceAndPath(match.group("namespace"), match.group("path"));
                    out.addAll(parseRoot(iid));
                } else {
                    out.add(line);
                }
            }
            return out;
        }

        private static List<String> toLines(String src) {
            try {
                return new BufferedReader(new StringReader(src)).readAllLines();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        private static String loadShaderAsset(Identifier id) {
            String path = String.format("/assets/%s/shaders/%s", id.getNamespace(), id.getPath());
            try (InputStream in = ShaderLoadingParser.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new RuntimeException("Shader not found: " + path);
                } else {
                    return IOUtils.toString(in, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read shader source for " + path, e);
            }
        }
    }
}
