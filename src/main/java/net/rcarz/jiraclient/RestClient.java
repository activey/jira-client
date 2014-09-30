/**
 * jira-client - a simple JIRA REST client
 * Copyright (c) 2013 Bob Carroll (bob.carroll@alum.rit.edu)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.rcarz.jiraclient;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;

/**
 * A simple REST client that speaks JSON.
 */
public class RestClient {

    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    private HttpClient httpClient = null;
    private Locale serverLocale;
    private ICredentials creds = null;
    private URI uri = null;

    /**
     * Creates a REST client instance with a URI.
     *
     * @param httpclient Underlying HTTP client to use
     * @param uri Base URI of the remote REST service
     */
    public RestClient(HttpClient httpclient, URI uri) {
        this(httpclient, Locale.getDefault(), null, uri);
    }

    /**
     * Creates a REST client instance with a URI.
     *
     * @param httpclient Underlying HTTP client to use
     * @param uri Base URI of the remote REST service
     */
    public RestClient(HttpClient httpclient, Locale serverLocale, URI uri) {
        this(httpclient, serverLocale, null, uri);
    }

    /**
     * Creates an authenticated REST client instance with a URI.
     *
     * @param httpclient Underlying HTTP client to use
     * @param creds Credentials to send with each request
     * @param uri Base URI of the remote REST service
     */
    public RestClient(HttpClient httpclient, Locale serverLocale, ICredentials creds, URI uri) {
        this.httpClient = httpclient;
        this.serverLocale = serverLocale;
        this.creds = creds;
        this.uri = uri;
    }

    /**
     * Build a URI from a path.
     *
     * @param path Path to append to the base URI
     *
     * @return the full URI
     *
     * @throws URISyntaxException when the path is invalid
     */
    public URI buildURI(String path) throws URISyntaxException {
        return buildURI(path, null);
    }

    /**
     * Build a URI from a path and query parmeters.
     *
     * @param path Path to append to the base URI
     * @param params Map of key value pairs
     *
     * @return the full URI
     *
     * @throws URISyntaxException when the path is invalid
     */
    public URI buildURI(String path, Map<String, String> params) throws URISyntaxException {
        URIBuilder ub = new URIBuilder(uri);
        ub.setPath(ub.getPath() + path);

        if (params != null) {
            for (Map.Entry<String, String> ent : params.entrySet())
                ub.addParameter(ent.getKey(), ent.getValue());
        }

        return ub.build();
    }

    private JSON request(HttpRequestBase request) throws RestException, IOException {
        request.addHeader("Accept", CONTENT_TYPE_JSON);

        updateCredentials(request);

        HttpResponse response = httpClient.execute(request);
        HttpEntity responseEntity = response.getEntity();
        StringBuilder result = new StringBuilder();
        if (responseEntity != null) {
            String encoding = getEncodingFromEntity(responseEntity);
            if (encoding == null) {
                encoding = getEncodingFromHeader(response);
            }
            InputStreamReader isr = getEntityContentStream(responseEntity, encoding);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                result.append(line);
            }
        }

        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() >= 300) {
            request.releaseConnection();
            throw new RestException(statusLine.getReasonPhrase(), statusLine.getStatusCode(), result.toString());
        }

        request.releaseConnection();
        return result.length() > 0 ? JSONSerializer.toJSON(result.toString()): null;
    }

    private InputStreamReader getEntityContentStream(HttpEntity responseEntity, String encoding) throws IOException {
        if (encoding == null) {
            return new InputStreamReader(responseEntity.getContent());
        }
        return new InputStreamReader(responseEntity.getContent(), encoding);
    }

    private String getEncodingFromHeader(HttpResponse resp) {
        Header contentTypeHeader = resp.getFirstHeader(HEADER_CONTENT_TYPE);
        HeaderElement[] contentTypeElements = contentTypeHeader.getElements();
        for (HeaderElement he : contentTypeElements) {
            NameValuePair nvp = he.getParameterByName("charset");
            if (nvp != null) {
                return nvp.getValue();
            }
        }
        return null;
    }

    private String getEncodingFromEntity(HttpEntity ent) {
        if (ent.getContentEncoding() != null) {
            return ent.getContentEncoding().getValue();
        }
        return null;
    }

    private void updateCredentials(HttpRequestBase req) {
        if (creds == null) {
            return;
        }
        creds.authenticate(req);
    }

    private JSON request(HttpEntityEnclosingRequestBase req, String payload)
        throws RestException, IOException {

        if (payload != null) {
            StringEntity ent = null;

            try {
                ent = new StringEntity(payload, "UTF-8");
                ent.setContentType(CONTENT_TYPE_JSON);
            } catch (UnsupportedEncodingException ex) {
                /* utf-8 should always be supported... */
            }

            req.addHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
            req.setEntity(ent);
        }

        return request(req);
    }
    
    private JSON request(HttpEntityEnclosingRequestBase req, File file)
            throws RestException, IOException {
            if (file != null) {
            	File fileUpload = file;
            	req.setHeader("X-Atlassian-Token","nocheck");
            	MultipartEntity ent = new MultipartEntity();
            	ent.addPart("file", new FileBody(fileUpload));
            	req.setEntity(ent);
            }
            return request(req);
        }

    private JSON request(HttpEntityEnclosingRequestBase req, JSON payload)
        throws RestException, IOException {

        return request(req, payload != null ? payload.toString() : null);
    }

    /**
     * Executes an HTTP DELETE with the given URI.
     *
     * @param uri Full URI of the remote endpoint
     *
     * @return JSON-encoded result or null when there's no content returned
     *
     * @throws RestException when an HTTP-level error occurs
     * @throws IOException when an error reading the response occurs
     */
    public JSON delete(URI uri) throws RestException, IOException {
        return request(new HttpDelete(uri));
    }

    /**
     * Executes an HTTP DELETE with the given path.
     *
     * @param path Path to be appended to the URI supplied in the construtor
     *
     * @return JSON-encoded result or null when there's no content returned
     *
     * @throws RestException when an HTTP-level error occurs
     * @throws IOException when an error reading the response occurs
     * @throws URISyntaxException when an error occurred appending the path to the URI
     */
    public JSON delete(String path) throws RestException, IOException, URISyntaxException {
        return delete(buildURI(path));
    }

    /**
     * Executes an HTTP GET with the given URI.
     *
     * @param uri Full URI of the remote endpoint
     *
     * @return JSON-encoded result or null when there's no content returned
     *
     * @throws RestException when an HTTP-level error occurs
     * @throws IOException when an error reading the response occurs
     */
    public JSON get(URI uri) throws RestException, IOException {
        return request(new HttpGet(uri));
    }

    /**
     * Executes an HTTP GET with the given path.
     *
     * @param path Path to be appended to the URI supplied in the construtor
     * @param params Map of key value pairs
     *
     * @return JSON-encoded result or null when there's no content returned
     *
     * @throws RestException when an HTTP-level error occurs
     * @throws IOException when an error reading the response occurs
     * @throws URISyntaxException when an error occurred appending the path to the URI
     */
    public JSON get(String path, Map<String, String> params) throws RestException, IOException, URISyntaxException {
        return get(buildURI(path, params));
    }

    /**
     * Executes an HTTP GET with the given path.
     *
     * @param path Path to be appended to the URI supplied in the construtor
     *
     * @return JSON-encoded result or null when there's no content returned
     *
     * @throws RestException when an HTTP-level error occurs
     * @throws IOException when an error reading the response occurs
     * @throws URISyntaxException when an error occurred appending the path to the URI
     */
    public JSON get(String path) throws RestException, IOException, URISyntaxException {
        return get(path, null);
    }


    /**
     * Executes an HTTP POST with the given URI and payload.
     *
     * @param uri Full URI of the remote endpoint
     * @param payload JSON-encoded data to send to the remote service
     *
     * @return JSON-encoded result or null when there's no content returned
     *
     * @throws RestException when an HTTP-level error occurs
     * @throws IOException when an error reading the response occurs
     */
    public JSON post(URI uri, JSON payload) throws RestException, IOException {
        return request(new HttpPost(uri), payload);
    }

    /**
     * Executes an HTTP POST with the given URI and payload.
     *
     * At least one JIRA REST endpoint expects malformed JSON. The payload
     * argument is quoted and sent to the server with the application/json
     * Content-Type header. You should not use this function when proper JSON
     * is expected.
     *
     * @see https://jira.atlassian.com/browse/JRA-29304
     *
     * @param uri Full URI of the remote endpoint
     * @param payload Raw string to send to the remote service
     *
     * @return JSON-encoded result or null when there's no content returned
     *
     * @throws RestException when an HTTP-level error occurs
     * @throws IOException when an error reading the response occurs
     */
    public JSON post(URI uri, String payload) throws RestException, IOException {
    	String quoted = null;
    	if(payload != null && !payload.equals(new JSONObject())){
    		quoted = String.format("\"%s\"", payload);
    	}
        return request(new HttpPost(uri), quoted);
    }

    /**
     * Executes an HTTP POST with the given path and payload.
     *
     * @param path Path to be appended to the URI supplied in the construtor
     * @param payload JSON-encoded data to send to the remote service
     *
     * @return JSON-encoded result or null when there's no content returned
     *
     * @throws RestException when an HTTP-level error occurs
     * @throws IOException when an error reading the response occurs
     * @throws URISyntaxException when an error occurred appending the path to the URI
     */
    public JSON post(String path, JSON payload)
        throws RestException, IOException, URISyntaxException {

        return post(buildURI(path), payload);
    }
    
    /**
     * Executes an HTTP POST with the given path.
     *
     * @param path Path to be appended to the URI supplied in the construtor
     *
     * @return JSON-encoded result or null when there's no content returned
     *
     * @throws RestException when an HTTP-level error occurs
     * @throws IOException when an error reading the response occurs
     * @throws URISyntaxException when an error occurred appending the path to the URI
     */
    public JSON post(String path)
        throws RestException, IOException, URISyntaxException {
    	
        return post(buildURI(path), new JSONObject());
    }
    
    /**
     * Executes an HTTP POST with the given path and file payload.
     * 
     * @param uri Full URI of the remote endpoint
     * @param file java.io.File
     * 
     * @throws URISyntaxException 
     * @throws IOException 
     * @throws RestException 
     */
    public JSON post(String path, File file) throws RestException, IOException, URISyntaxException{
        return request(new HttpPost(buildURI(path)), file);
    }

    /**
     * Executes an HTTP PUT with the given URI and payload.
     *
     * @param uri Full URI of the remote endpoint
     * @param payload JSON-encoded data to send to the remote service
     *
     * @return JSON-encoded result or null when there's no content returned
     *
     * @throws RestException when an HTTP-level error occurs
     * @throws IOException when an error reading the response occurs
     */
    public JSON put(URI uri, JSON payload) throws RestException, IOException {
        return request(new HttpPut(uri), payload);
    }

    /**
     * Executes an HTTP PUT with the given path and payload.
     *
     * @param path Path to be appended to the URI supplied in the construtor
     * @param payload JSON-encoded data to send to the remote service
     *
     * @return JSON-encoded result or null when there's no content returned
     *
     * @throws RestException when an HTTP-level error occurs
     * @throws IOException when an error reading the response occurs
     * @throws URISyntaxException when an error occurred appending the path to the URI
     */
    public JSON put(String path, JSON payload)
        throws RestException, IOException, URISyntaxException {

        return put(buildURI(path), payload);
    }
    
    /**
     * Exposes the http client.
     *
     * @return the httpClient property
     */
    public HttpClient getHttpClient(){
        return this.httpClient;
    }

    public Locale getServerLocale() {
        return serverLocale;
    }
}

