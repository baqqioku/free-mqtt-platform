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
import java.util.concurrent.atomic.AtomicLong;

public class DefaultAuthorizator implements IAuthorizator {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAuthorizator.class);

    private final boolean enabled;
    private final boolean defaultAllow;
    private final long reloadIntervalMs;
    private final Path aclPath;
    private volatile long nextReloadAt;
    private volatile long lastModified;
    private volatile List<AclRule> rules = Collections.emptyList();
    
    // 统计信息
    private final AtomicLong ruleMatchCount = new AtomicLong(0);
    private final AtomicLong ruleCheckCount = new AtomicLong(0);
    private volatile int lastRuleCount = 0;
    private volatile long lastReloadTime = -1;

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
        ruleCheckCount.incrementAndGet();
        
        if (!enabled) {
            return true;
        }
        
        reloadIfNeeded(false);
        List<AclRule> current = rules;
        
        if (current == null || current.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("ACL规则为空，使用默认策略: action={}, defaultAllow={}, topic={}, user={}, client={}", 
                    action, defaultAllow, topic, user, client);
            }
            return defaultAllow;
        }
        
        String topicStr = topic == null ? null : topic.toString();
        
        for (AclRule rule : current) {
            if (rule == null) {
                continue;
            }
            if (rule.matches(action, user, client, topicStr)) {
                ruleMatchCount.incrementAndGet();
                if (logger.isDebugEnabled()) {
                    logger.debug("ACL规则匹配: action={}, rule={}, topic={}, user={}, client={}, allow={}", 
                        action, rule, topicStr, user, client, rule.isAllow());
                }
                return rule.isAllow();
            }
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("ACL规则未匹配，使用默认策略: action={}, defaultAllow={}, topic={}, user={}, client={}", 
                action, defaultAllow, topicStr, user, client);
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
        long startTime = System.currentTimeMillis();
        List<String> lines = readLines();
        if (lines == null) {
            return;
        }
        
        List<AclRule> parsed = new ArrayList<>();
        int parseErrors = 0;
        
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }
            
            try {
                AclRule rule = parseRule(line);
                if (rule != null) {
                    parsed.add(rule);
                } else {
                    parseErrors++;
                    logger.warn("ACL规则解析失败（规则格式无效）: {}", line);
                }
            } catch (Exception e) {
                parseErrors++;
                logger.warn("ACL规则解析异常: {}, 原因: {}", line, e.getMessage());
            }
        }
        
        lastRuleCount = parsed.size();
        lastReloadTime = System.currentTimeMillis() - startTime;
        rules = parsed;
        
        logger.info("ACL规则加载完成: 总规则数={}, 解析错误={}, 加载耗时={}ms", 
            lastRuleCount, parseErrors, lastReloadTime);
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
                    logger.info("从ACL文件加载规则: {}", aclPath.toAbsolutePath());
                    return Files.readAllLines(aclPath, StandardCharsets.UTF_8);
                }
                logger.warn("ACL文件不存在: {}", aclPath.toAbsolutePath());
                return Collections.emptyList();
            }
            
            try (InputStream in = DefaultAuthorizator.class.getClassLoader().getResourceAsStream("acl.conf")) {
                if (in == null) {
                    logger.debug("acl.conf资源不存在，使用默认ACL策略");
                    return Collections.emptyList();
                }
                List<String> rtv = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        rtv.add(line);
                    }
                }
                logger.info("从classpath加载acl.conf完成，共{}行", rtv.size());
                return rtv;
            }
        } catch (Exception e) {
            logger.error("ACL加载失败", e);
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
    
    /**
     * 获取ACL统计信息（用于监控）
     */
    public String getStatistics() {
        return String.format(
            "ACL统计: 规则总数=%d, 规则检查次数=%d, 规则匹配次数=%d, 匹配率=%.2f%%, 最后加载耗时=%dms, 已启用=%b, 默认策略=%s",
            lastRuleCount,
            ruleCheckCount.get(),
            ruleMatchCount.get(),
            ruleCheckCount.get() > 0 ? (ruleMatchCount.get() * 100.0 / ruleCheckCount.get()) : 0,
            lastReloadTime,
            enabled,
            defaultAllow ? "ALLOW" : "DENY"
        );
    }
}
