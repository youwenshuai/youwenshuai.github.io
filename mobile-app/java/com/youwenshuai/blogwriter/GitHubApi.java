package com.youwenshuai.blogwriter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

/** GitHub Contents API 的轻量封装 */
public class GitHubApi {

    static String request(String method, String urlStr, String token, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", "blogwriter-android");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        if (token != null && !token.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        if (in != null) {
            byte[] tmp = new byte[8192];
            int n;
            while ((n = in.read(tmp)) > 0) buf.write(tmp, 0, n);
            in.close();
        }
        String resp = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        if (code >= 400) throw new Exception("HTTP " + code + ": " + resp);
        return resp;
    }

    /** 对单个路径段做 URL 编码 */
    static String enc(String seg) {
        try {
            return URLEncoder.encode(seg, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return seg;
        }
    }

    /** 对 "a/b/c" 形式的路径逐段编码，保留斜杠 */
    static String encPath(String path) {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(enc(parts[i]));
        }
        return sb.toString();
    }
}
