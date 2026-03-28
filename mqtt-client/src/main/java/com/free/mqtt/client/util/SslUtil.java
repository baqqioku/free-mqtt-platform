package com.free.mqtt.client.util;

import com.free.mqtt.client.MqttClientConfig;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class SslUtil {

    public static SSLSocketFactory createSocketFactory(MqttClientConfig config) throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        KeyManager[] kms = buildKeyManagers(config);
        TrustManager[] tms = buildTrustManagers(config);
        ctx.init(kms, tms, new SecureRandom());
        return ctx.getSocketFactory();
    }

    private static KeyManager[] buildKeyManagers(MqttClientConfig config) throws Exception {
        String keyStorePath = config.getKeyStorePath();
        if (keyStorePath == null || keyStorePath.trim().isEmpty()) {
            return null;
        }
        File ksFile = new File(keyStorePath);
        if (!ksFile.exists()) {
            return null;
        }
        char[] pass = config.getKeyStorePassword() == null ? new char[0] : config.getKeyStorePassword().toCharArray();
        String type = config.getKeyStoreType() == null || config.getKeyStoreType().isEmpty() ? "JKS" : config.getKeyStoreType();
        KeyStore ks = KeyStore.getInstance(type);
        try (FileInputStream in = new FileInputStream(ksFile)) {
            ks.load(in, pass);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pass);
        return kmf.getKeyManagers();
    }

    private static TrustManager[] buildTrustManagers(MqttClientConfig config) throws Exception {
        if (config.isSslInsecure()) {
            return new TrustManager[] { new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
        }
        String trustStorePath = config.getTrustStorePath();
        if (trustStorePath == null || trustStorePath.trim().isEmpty()) {
            return null;
        }
        File tsFile = new File(trustStorePath);
        if (!tsFile.exists()) {
            return null;
        }
        char[] pass = config.getTrustStorePassword() == null ? new char[0] : config.getTrustStorePassword().toCharArray();
        String type = config.getTrustStoreType() == null || config.getTrustStoreType().isEmpty() ? "JKS" : config.getTrustStoreType();
        KeyStore ts = KeyStore.getInstance(type);
        try (FileInputStream in = new FileInputStream(tsFile)) {
            ts.load(in, pass);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        return tmf.getTrustManagers();
    }
}
