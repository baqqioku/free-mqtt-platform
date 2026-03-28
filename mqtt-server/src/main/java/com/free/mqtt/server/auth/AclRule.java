package com.free.mqtt.server.auth;

import java.util.ArrayList;
import java.util.List;

public class AclRule {
    public enum Action {
        PUB,
        SUB,
        PUBSUB
    }

    public enum SubjectType {
        USER,
        CLIENT,
        ALL
    }

    private final boolean allow;
    private final SubjectType subjectType;
    private final String subject;
    private final Action action;
    private final String topicFilter;

    public AclRule(boolean allow, SubjectType subjectType, String subject, Action action, String topicFilter) {
        this.allow = allow;
        this.subjectType = subjectType;
        this.subject = subject;
        this.action = action;
        this.topicFilter = topicFilter;
    }

    public boolean isAllow() {
        return allow;
    }

    public boolean matches(Action act, String user, String clientId, String topic) {
        if (!actionMatches(act)) {
            return false;
        }
        if (!subjectMatches(user, clientId)) {
            return false;
        }
        return topicMatches(topicFilter, topic);
    }

    private boolean actionMatches(Action act) {
        if (action == Action.PUBSUB) {
            return act == Action.PUB || act == Action.SUB;
        }
        return action == act;
    }

    private boolean subjectMatches(String user, String clientId) {
        String val = subject == null ? "" : subject;
        switch (subjectType) {
            case ALL:
                return true;
            case USER:
                if ("*".equals(val)) {
                    return true;
                }
                return user != null && user.equals(val);
            case CLIENT:
                if ("*".equals(val)) {
                    return true;
                }
                return clientId != null && clientId.equals(val);
            default:
                return false;
        }
    }

    private boolean topicMatches(String filter, String topic) {
        if (filter == null || topic == null) {
            return false;
        }
        if (filter.equals(topic)) {
            return true;
        }
        List<String> fs = split(filter);
        List<String> ts = split(topic);
        int i = 0;
        for (; i < fs.size(); i++) {
            String f = fs.get(i);
            if ("#".equals(f)) {
                return true;
            }
            if (i >= ts.size()) {
                return false;
            }
            if ("+".equals(f)) {
                continue;
            }
            if (!f.equals(ts.get(i))) {
                return false;
            }
        }
        return i == ts.size();
    }

    private List<String> split(String topic) {
        String s = topic;
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = s.split("/");
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            out.add(p);
        }
        return out;
    }
}
