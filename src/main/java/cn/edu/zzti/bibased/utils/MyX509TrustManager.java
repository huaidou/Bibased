package cn.edu.zzti.bibased.utils;

import org.apache.http.conn.ssl.X509HostnameVerifier;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.security.cert.X509Certificate;

public class MyX509TrustManager implements X509HostnameVerifier {

    @Override
    public void verify(String s, SSLSocket sslSocket) throws IOException {

    }

    @Override
    public void verify(String s, X509Certificate x509Certificate) throws SSLException {

    }

    @Override
    public void verify(String s, String[] strings, String[] strings1) throws SSLException {

    }

    @Override
    public boolean verify(String s, SSLSession sslSession) {
        return true;
    }
}
