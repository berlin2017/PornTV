package com.berlin.porntv;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class UnsafeOkHttpClient {
    public static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // 创建一个信任所有证书的 TrustManager
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // 安装所有信任的 TrustManager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // 创建一个信任所有主机的 HostnameVerifier
            final HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true; // 始终信任
                }
            };

            // 构建 OkHttpClient
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier(hostnameVerifier);
            builder.setCallTimeout$okhttp(30 * 1000);
            builder.setConnectTimeout$okhttp(30 * 1000);
            builder.setReadTimeout$okhttp(30 * 1000);
            builder.setWriteTimeout$okhttp(30 * 1000);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}