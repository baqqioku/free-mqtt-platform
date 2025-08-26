package com.free.mqtt.server.subscriptions;

import com.free.mqtt.server.BaseThread;
import com.free.mqtt.server.subscriptions.data.Subscription;
import com.free.mqtt.server.subscriptions.data.Topic;
import com.free.mqtt.server.subscriptions.data.TopicNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TreeSubscriptionDirectory extends BaseThread<Subscription> implements ISubscriptionsDirectory {

    private static final Logger logger = LoggerFactory.getLogger(TreeSubscriptionDirectory.class);

    private static TopicNode root = null;

    public TreeSubscriptionDirectory(){
        root = new TopicNode();
        super.start();
    }

    @Override
    public void doing(Subscription subscription) {

        Topic topic = subscription.getTopicFilter();
        List<String> tokens = topic.getTokens();
        if(null == tokens || tokens.size()<=0){
            return;
        }
        if(subscription.isAlive()){
            root.addNode(tokens,subscription);
        }else {
            root.removeNode(tokens,subscription);
        }
    };

    @Override
    public void addSubscription(Subscription newSubscription) {
        newSubscription.setAlive(true);
        this.submit(newSubscription);
    }

    @Override
    public void removeSubscription(Subscription unSubscription) {
        unSubscription.setAlive(false);
        this.submit(unSubscription);
    }

    @Override
    public List<Subscription> matches(Topic topic) {
        List<String> tokens = topic.getTokens();
        return root.match(tokens);
    }
}
