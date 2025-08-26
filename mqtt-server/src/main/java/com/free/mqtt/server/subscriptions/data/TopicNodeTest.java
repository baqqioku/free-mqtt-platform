package com.free.mqtt.server.subscriptions.data;

import java.util.Arrays;
import java.util.List;

public class TopicNodeTest {

    public static void main(String[] args) {
        TopicNode root = new TopicNode();

        Subscription sub1 = new Subscription("clientA", new Topic("/guoguo/1"));
        Subscription sub2 = new Subscription("clientA", new Topic("/guoguo/3"));
        Subscription sub3 = new Subscription("clientA", new Topic("/guoguo/#"));
        Subscription sub4 = new Subscription("clientB", new Topic("/guoguo/+"));
        Subscription sub5 = new Subscription("clientC", new Topic("/guoguo/+/test"));


        // 添加订阅
        root.addNode(Arrays.asList("guoguo", "1"), sub1);
        root.addNode(Arrays.asList("guoguo", "3"), sub2);
        root.addNode(Arrays.asList("guoguo", "#"), sub3);
        root.addNode(Arrays.asList("guoguo", "+"), sub4);
        root.addNode(Arrays.asList("guoguo", "+", "test"), sub5);

        // 匹配 /guoguo/6
        List<Subscription> matches = root.match(Arrays.asList("guoguo", "6"));
        System.out.println("匹配结果: " + matches.size()); // 应为1
        //System.out.println("匹配客户端: " + matches.get(0).getClientId()); // 应为B

        // 匹配测试
        System.out.println("匹配 /guoguo/1:");
        System.out.println(root.match(Arrays.asList("guoguo", "1"))); // 应该包含 s1, s3

        System.out.println("匹配 /guoguo/2:");
        System.out.println(root.match(Arrays.asList("guoguo", "2"))); // 应该包含 s3

        System.out.println("匹配 /guoguo/abc/test:");
        System.out.println(root.match(Arrays.asList("guoguo", "abc", "test"))); // 应该包含 s3, s2

        // 删除订阅
        root.removeNode(Arrays.asList("guoguo", "#"), sub3);

        System.out.println("匹配 /guoguo/2 (删除#后):");
        System.out.println(root.match(Arrays.asList("guoguo", "2"))); // 应该为空
        System.out.println("1111");
    }
}
