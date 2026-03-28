package com.free.mqtt.server.auth;

import com.free.mqtt.server.auth.AclRule.Action;
import com.free.mqtt.server.auth.AclRule.SubjectType;
import com.free.mqtt.server.subscriptions.data.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultAuthorizator implements IAuthorizator {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAuthorizator.class);

    private final boolean enabled;
    private final boolean defaultAllow;
    private final long reloadIntervalMs;
    private final Path aclPath;
    private volatile long nextReloadAt;
    private volatile long lastModified;
    private volatile List<AclRule> rules = Collections.emptyList();

    public DefaultAuthorizator() {
        this(null, false, true, 30);
    }

    public DefaultAuthorizator(String aclFile, boolean enabled, boolean defaultAllow, int reloadSeconds) {
        this.enabled = enabled;
        this.defaultAllow = defaultAllow;
        this.reloadIntervalMs = Math.max(5, reloadSeconds) * 1000L;
        this.aclPath = (aclFile == null || aclFile.trim().isEmpty()) ? null : Paths.get(aclFile);
        this.lastModified = -1L;
        this.nextReloadAt = 0L;
        reloadIfNeeded(true);
    }

    @Override
    public boolean canWrite(Topic topic, String user, String client) {
        return check(Action.PUB, topic, user, client);
    }

    @Override
    public boolean canRead(Topic topic, String user, String client) {
        return check(Action.SUB, topic, user, client);
    }

    private boolean check(Action action, Topic topic, String user, String client) {
        if (!enabled) {
            return true;
        }
        reloadIfNeeded(false);
        List<AclRule> current = rules;
        if (current == null || current.isEmpty()) {
            return defaultAllow;
        }
        String topicStr = topic == null ? null : topic.toString();
        for (AclRule rule : current) {
            if (rule == null) {
                continue;
            }
            if (rule.matches(action, user, client, topicStr)) {
                return rule.isAllow();
            }
        }
        return defaultAllow;
    }

    private void reloadIfNeeded(boolean force) {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now < nextReloadAt) {
            return;
        }
        nextReloadAt = now + reloadIntervalMs;
        loadRules();
    }

    private void loadRules() {
        List<String> lines = readLines();
        if (lines == null) {
            return;
        }
        List<AclRule> parsed = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }
            AclRule rule = parseRule(line);
            if (rule != null) {
                parsed.add(rule);
            }
        }
        rules = parsed;
    }

    private List<String> readLines() {
        try {
            if (aclPath != null) {
                if (Files.exists(aclPath)) {
                    long lm = Files.getLastModifiedTime(aclPath).toMillis();
                    if (lm == lastModified && !rules.isEmpty()) {
                        return null;
                    }
                    lastModified = lm;
                    return Files.readAllLines(aclPath, StandardCharsets.UTF_8);
                }
                logger.warn("ACL file not found: {}", aclPath.toAbsolutePath());
                return Collections.emptyList();
            }
            try (InputStream in = DefaultAuthorizator.class.getClassLoader().getResourceAsStream("acl.conf")) {
                if (in == null) {
                    return Collections.emptyList();
                }
                List<String> rtv = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        rtv.add(line);
                    }
                }
                return rtv;
            }
        } catch (Exception e) {
            logger.error("ACL load error", e);
            return Collections.emptyList();
        }
    }

    private AclRule parseRule(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 4) {
            return null;
        }
        boolean allow;
        if ("allow".equalsIgnoreCase(parts[0])) {
            allow = true;
        } else if ("deny".equalsIgnoreCase(parts[0])) {
            allow = false;
        } else {
            return null;
        }

        SubjectType subjectType;
        String subject = "*";
        int idx = 1;
        if (idx >= parts.length) {
            return null;
        }
        String subjectToken = parts[idx++];
        if ("user".equalsIgnoreCase(subjectToken)) {
            subjectType = SubjectType.USER;
            if (idx >= parts.length) {
                return null;
            }
            subject = parts[idx++];
        } else if ("client".equalsIgnoreCase(subjectToken)) {
            subjectType = SubjectType.CLIENT;
            if (idx >= parts.length) {
                return null;
            }
            subject = parts[idx++];
        } else if ("all".equalsIgnoreCase(subjectToken) || "*".equals(subjectToken)) {
            subjectType = SubjectType.ALL;
        } else {
            return null;
        }

        if (idx >= parts.length) {
            return null;
        }
        Action action;
        String actionToken = parts[idx++];
        if ("pub".equalsIgnoreCase(actionToken)) {
            action = Action.PUB;
        } else if ("sub".equalsIgnoreCase(actionToken)) {
            action = Action.SUB;
        } else if ("pubsub".equalsIgnoreCase(actionToken) || "all".equalsIgnoreCase(actionToken)) {
            action = Action.PUBSUB;
        } else {
            return null;
        }

        if (idx >= parts.length) {
            return null;
        }
        String topic = parts[idx];
        if (topic.trim().isEmpty()) {
            return null;
        }
        return new AclRule(allow, subjectType, subject, action, topic);
    }
}
