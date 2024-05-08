/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.dashboard.util;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;


@Component
public class HttpRequestUtil {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestUtil.class);

    private static String ipAddr;

    @Value("${rocketmq.config.connectAPIAddr}")
    public void setIpAddr(String ipAddr) {
        HttpRequestUtil.ipAddr = ipAddr;
    }

    public static String requestString(String urlSuffix) {
        String urlString = getUrl(urlSuffix);

        return requestMsg(urlString);
    }

    public static <T> T requestJSON(String urlSuffix, Class<T> targetClass) {
        String urlString = getUrl(urlSuffix);
        String responseByte = requestMsg(urlString);

        return JsonUtil.string2Obj(responseByte, targetClass);
    }

    public static String getUrl(String urlSuffix) {
        if (ipAddr == null || ipAddr.length() == 0) {
            throw new RuntimeException("Failed to get url! Please edit it in application.properties!");
        }

        return "http://" + ipAddr + urlSuffix;
    }

    public static String requestMsg(String urlString) {

        HttpURLConnection conn = null;
        InputStream is = null;

        try {

            URL mURL = new URL(urlString);
            conn = (HttpURLConnection) mURL.openConnection();

            conn.setRequestMethod("GET");
            conn.setReadTimeout(5000);
            conn.setConnectTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {

                is = conn.getInputStream();
                String state = getStringFromInputStream(is);
                return state;
            } else {
                log.info("Failed to request! Code: " + responseCode);

            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    private static String getStringFromInputStream(InputStream is)
            throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int len = -1;

        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        is.close();
        String state = os.toString();
        os.close();
        return state;
    }

    public static String postRequest(String url, JSONObject requestParam) {

        PrintWriter out = null;
        BufferedReader in = null;
        String result = "";
        try {
            URL realUrl = new URL(url);
            URLConnection conn = realUrl.openConnection();

            conn.setDoOutput(true);
            conn.setDoInput(true);

            conn.setReadTimeout(5000);
            conn.setConnectTimeout(5000);

            conn.setRequestProperty("Content-type", "application/json;charset=UTF-8");

            out = new PrintWriter(conn.getOutputStream());
            out.print(requestParam);
            out.flush();

            in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));

            String line;
            while ((line = in.readLine()) != null) {
                line = new String(line.getBytes(), "utf-8");
                result += line;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error connecting to: " + url + "," + e);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                throw new RuntimeException("Error closing connection");
            }
        }
        return result;


    }
}
