package com.hmdp.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * .env 文件加载器。
 * 优先级：系统环境变量 > .env 文件 > application.yaml 默认值。
 * 实现方式：将 .env 中的值设为 System Property，
 * Spring Boot 解析 ${VAR:default} 时先查环境变量，再查 system property。
 */
public class EnvConfig {

    private static final String ENV_FILE = ".env";

    public static void load() {
        // 优先从 classpath 加载，其次从工作目录加载
        InputStream is = EnvConfig.class.getClassLoader().getResourceAsStream(ENV_FILE);
        if (is != null) {
            loadFromStream(is);
        } else {
            Path path = Paths.get(ENV_FILE);
            if (Files.exists(path)) {
                try {
                    loadFromStream(Files.newInputStream(path));
                } catch (IOException e) {
                    System.err.println("[EnvConfig] Failed to read " + ENV_FILE + ": " + e.getMessage());
                }
            }
        }
    }

    private static void loadFromStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 跳过空行和注释
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIndex = line.indexOf('=');
                if (eqIndex <= 0) {
                    continue;
                }
                String key = line.substring(0, eqIndex).trim();
                String value = line.substring(eqIndex + 1).trim();
                // 去除可选的引号
                if (value.length() >= 2 &&
                        ((value.startsWith("\"") && value.endsWith("\"")) ||
                         (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                // 仅当系统环境变量中不存在该 key 时，才设置为 system property
                // 这样系统环境变量始终优先于 .env
                if (System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("[EnvConfig] Error reading .env: " + e.getMessage());
        }
    }
}
