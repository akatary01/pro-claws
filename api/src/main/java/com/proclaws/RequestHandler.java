package com.proclaws;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

public class RequestHandler {
    static CloseableHttpClient httpclient = HttpClients.createDefault();

    public static CloseableHttpResponse post(String uri, HashMap<String, ?> params, HashMap<String, String> headers) throws Exception {
        final HttpPost request = new HttpPost(uri);
        headers.forEach((key, value) -> request.addHeader(key, value));
        request.setEntity(new UrlEncodedFormEntity(createParamsList(params), "UTF-8"));
        return httpclient.execute(request);
    }

    public static CloseableHttpResponse get(String uri, HashMap<String, ?> params, HashMap<String, String> headers) throws Exception {
        final HttpGet request = new HttpGet(new URIBuilder(uri).addParameters(createParamsList(params)).build());
        headers.forEach((key, value) -> request.addHeader(key, value));
        return httpclient.execute(request);
    }

    private static List<NameValuePair> createParamsList(HashMap<String, ?> params) {
        final List<NameValuePair> paramsList = new ArrayList<>();
        params.forEach((key, value) -> paramsList.add(new BasicNameValuePair(key, value.toString())));
        return paramsList;
    }
}
    
