package com.proklaws;

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
    CloseableHttpClient httpclient = HttpClients.createDefault();

    public CloseableHttpResponse post(String uri, HashMap<String, ?> params) throws Exception {
        final HttpPost request = new HttpPost(uri);
        
        request.setEntity(new UrlEncodedFormEntity(createParamsList(params), "UTF-8"));
        return httpclient.execute(request);
    }

    public CloseableHttpResponse get(String uri, HashMap<String, ?> params) throws Exception {
        final HttpGet request = new HttpGet(new URIBuilder(uri).addParameters(createParamsList(params)).build());
        return httpclient.execute(request);
    }

    private List<NameValuePair> createParamsList(HashMap<String, ?> params) {
        final List<NameValuePair> paramsList = new ArrayList<>();
        params.forEach((key, value) -> paramsList.add(new BasicNameValuePair(key, value.toString())));
        return paramsList;
    }
}
    
