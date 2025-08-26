package com.free.mqtt.server.subscriptions.data;

import com.free.mqtt.server.subscriptions.TopicConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TopicNode {

    private Logger logger = LoggerFactory.getLogger(TopicNode.class);

    private Map<String, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();
    private Map<String, TopicNode> nodeData = new ConcurrentHashMap<>();

    public void addNode(List<String> tokens, Subscription subscription) {
        addNode(0, tokens, subscription);
    }

    private void addNode(int index, List<String> tokens, Subscription subscription) {
        String token = tokens.get(index);

        // 最后一层 -> 存放订阅
        if (index == tokens.size() - 1) {
            subscriptions
                    .computeIfAbsent(token, k -> Collections.newSetFromMap(new ConcurrentHashMap<Subscription, Boolean>()))
                    .add(subscription);
            return;
        }

        // 进入子节点
        TopicNode node = nodeData.computeIfAbsent(token, k -> new TopicNode());
        node.addNode(index + 1, tokens, subscription);
    }

    public void removeNode(List<String> tokens, Subscription subscription) {
        removeNode(0, tokens, subscription);
    }

    private boolean removeNode(int index, List<String> tokens, Subscription subscription) {
        String token = tokens.get(index);

        logger.info("删除token:{}",token);

        if (index == tokens.size() - 1) {
            Set<Subscription> setSub = subscriptions.get(token);
            if (setSub != null) {
                setSub.remove(subscription);
                if (setSub.isEmpty()) {
                    subscriptions.remove(token);
                }
            }
        } else {
            TopicNode node = nodeData.get(token);
            if (node != null) {
                boolean empty = node.removeNode(index + 1, tokens, subscription);
                if (empty) {
                    nodeData.remove(token);
                }
            }
        }

        return subscriptions.isEmpty() && nodeData.isEmpty();
    }

    public List<Subscription> match(List<String> tokens) {
        return match(0, tokens);
    }

    private List<Subscription> match(int index, List<String> tokens) {
        String token = tokens.get(index);
        List<Subscription> result = new ArrayList<>();

        // 1. 匹配 "#"
        Set<Subscription> hashSubs = subscriptions.get(TopicConstant.MULTI);
        if (hashSubs != null) {
            result.addAll(hashSubs); // '#' 在当前层直接生效
        }

        // 2. 匹配 "+"
        Set<Subscription> plusSubs = subscriptions.get(TopicConstant.SINGLE);
        if (plusSubs != null && index == tokens.size() - 1) {
            result.addAll(plusSubs);
        }

        // 3. 精确匹配
        if (index == tokens.size() - 1) {
            Set<Subscription> subs = subscriptions.get(token);
            if (subs != null) {
                result.addAll(subs);
            }
        } else {
            // 下钻 "+"
            TopicNode plusNode = nodeData.get(TopicConstant.SINGLE);
            if (plusNode != null) {
                result.addAll(plusNode.match(index + 1, tokens));
            }

            // 下钻精确 token
            TopicNode node = nodeData.get(token);
            if (node != null) {
                result.addAll(node.match(index + 1, tokens));
            }

            // 下钻 "#"
            TopicNode hashNode = nodeData.get(TopicConstant.MULTI);
            if (hashNode != null) {
                Set<Subscription> subs = hashNode.getSubscriptions(TopicConstant.MULTI);
                if (subs != null) {
                    result.addAll(subs);
                }
            }
        }

        return result;
    }

    public Set<Subscription> getSubscriptions(String token) {
        return subscriptions.getOrDefault(token, Collections.emptySet());
    }
}
