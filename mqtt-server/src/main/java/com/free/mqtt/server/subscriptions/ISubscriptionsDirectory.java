package com.free.mqtt.server.subscriptions;

import com.free.mqtt.server.subscriptions.data.Subscription;
import com.free.mqtt.server.subscriptions.data.Topic;

import java.util.List;

public interface ISubscriptionsDirectory {

    void addSubscription(Subscription newSubscription);

    void removeSubscription(Subscription subscription);

    List<Subscription> matches(Topic topic);


}
