/*
 * Copyright (c) 2012-2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package com.free.mqtt.server.subscriptions.data;

import com.free.mqtt.server.subscriptions.TopicConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Topic implements Serializable{

    private static final Logger LOG = LoggerFactory.getLogger(Topic.class);

    private static final long serialVersionUID = 2438799283749822L;

    private final String topic;

    private transient List<String> tokens;

    private transient boolean valid;

    /**
     * Factory method
     */
    public static Topic asTopic(String s) {
        return new Topic(s);
    }

    public Topic(String topic) {
        this.topic = topic;
    }

    public List<String> getTokens() {
        if (tokens == null) {
            try {
                tokens = parseTopic(topic);
                valid = true;
            } catch (ParseException e) {
                valid = false;
                LOG.error("Error parsing the topic: {}, message: {}", topic, e.getMessage());
            }
        }

        return tokens;
    }

    private List<String> parseTopic(String topic) throws ParseException {


        if (!topic.startsWith("/")) {
            throw new ParseException("topic必须以/开始" + topic, 0);
        }

        if (topic.endsWith("/")) {
            throw new ParseException("topick不能以/结束" + topic, 0);
        }

        String[] splitted = topic.split("/");

        if (splitted.length <= 0) {
            return null;
        }

        List<String> res = new ArrayList<>();

        for (int i = 1; i < splitted.length; i++) {
            String s = splitted[i];
            if (s.isEmpty() || s.indexOf(" ") != -1) {
                throw new ParseException("topic中包含空格   topic=" + topic, i);
            }

            if (s.length() > 1 & s.contains(TopicConstant.MULTI)) {
                throw new ParseException("Bad format of topic, invalid subtopic name: " + s, i);
            }

            if (s.length() > 1 & s.contains(TopicConstant.SINGLE)) {
                throw new ParseException("Bad format of topic, invalid subtopic name: " + s, i);
            }


            switch (s) {
                case TopicConstant.MULTI: {
                    if (i != splitted.length - 1) {
                        throw new ParseException("topic中间不能够包含#号通配符", i);
                    }
                    res.add(TopicConstant.MULTI);
                    break;
                }

                case TopicConstant.SINGLE: {
                    res.add(TopicConstant.SINGLE);
                    break;
                }

                default: {
                    res.add(s);
                }
            }
        }

        return res;
    }

    public boolean isEmpty() {
        final List<String> tokens = getTokens();
        return tokens == null || tokens.isEmpty();
    }


    public boolean isValid() {
        if (tokens == null)
            getTokens();

        return valid;
    }


    @Override
    public String toString() {
        return topic;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        Topic other = (Topic) obj;

        return this.topic.equals(other.topic);
    }

    @Override
    public int hashCode() {
        return topic.hashCode();
    }

}