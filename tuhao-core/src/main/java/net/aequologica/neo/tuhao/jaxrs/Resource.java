package net.aequologica.neo.tuhao.jaxrs;

import static net.aequologica.neo.geppaequo.config.ConfigRegistry.getConfig;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.glassfish.jersey.server.mvc.Viewable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;

import net.aequologica.neo.geppaequo.crypto.Codec;
import net.aequologica.neo.tuhao.config.TuhaoConfig;

@Singleton
@javax.ws.rs.Path("v1")
public class Resource {

    private static final Logger log = LoggerFactory.getLogger(Resource.class);

    @Inject Codec codec;

    final private RedirectStrategy laxRedirectStrategy;
    final private ObjectMapper mapper;
    
    final private String githubId;
    private String githubKey;

    public Resource() {
        super();
        this.laxRedirectStrategy = new LaxRedirectStrategy();
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JSR353Module());
        this.githubId = getConfig(TuhaoConfig.class).getGithubId();
    }

    @GET
    @javax.ws.rs.Path("callback")
    @Produces(MediaType.TEXT_HTML)
    public Viewable callback(@QueryParam("code") String code, @QueryParam("state") String state, @Context HttpServletRequest req) throws Exception {
        log.debug("code:{}, state:{}", code, state);
        
        HttpClientBuilder clientBuilder = HttpClients.custom();
        
        String proxy = "http://proxy.wdf.sap.corp:8080";
        URI github = URI.create("https://github.com");
        
        
        // THIS IS BAAAAAAAAAAAAAAAAAAAAAAAAAAAD !!!!!!!!!!!!!!!!!!!!!!!
        // workaround the
        //     javax.net.ssl.SSLPeerUnverifiedException: Host name 'x.y.z' does not match the certificate subject provided by the peer (CN=...)
        // exception
        // cf.
        // http://stackoverflow.com/questions/2642777/trusting-all-certificates-using-httpclient-over-https
        // http://www.baeldung.com/httpclient-ssl
        
        if (github.getScheme().equals("https")) {
            final SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), new NoopHostnameVerifier());
            clientBuilder.setSSLSocketFactory(sslsf);
        }
        // THIS WAS BAAAAAAAAAAAAAAAAAAAAAAAAAAAD !!!!!!!!!!!!!!!!!!!!!!!
        
        // THIS IS BAAAAAAAAAAAAAD (2) AS WELL !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // HTTP spec says "POST (...) methods will not be automatically redirected as requiring user confirmation"
        // cf. http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/client/DefaultRedirectStrategy.html
        clientBuilder.setRedirectStrategy(laxRedirectStrategy);
        // THIS WAS BAAAAAAAAAAAAAAAAAAAAAAAAAAAD (2) !!!!!!!!!!!!!!!!!!!!!!!

        if (proxy != null) {
            URI proxyURI = URI.create(proxy);
            HttpHost httpHostProxy = new HttpHost(proxyURI.getHost(), proxyURI.getPort());
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(httpHostProxy);
            clientBuilder.setRoutePlanner(routePlanner);
        }
        
        HttpSession session = req.getSession(true);

        String access_token = null;
        {
            Object accessTokenSessionAttribute = session.getAttribute("access_token");
            if (accessTokenSessionAttribute != null) {
                access_token = accessTokenSessionAttribute.toString();
            }
        }

        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
        
        try (CloseableHttpClient httpclient = clientBuilder.build()) {
            if (access_token == null) {
                JsonObject accessTokenAsJsonObject = getAccessToken(httpclient, code);
                if (accessTokenAsJsonObject != null) {
                    access_token = accessTokenAsJsonObject.getString("access_token");
                }
                session.setAttribute("access_token", access_token);
            }
                
            log.debug("access_token: {} <<<<<<<<<<<<<<<<=======================", access_token);
            
            JsonObject user = getUser(httpclient, access_token);
            // JsonArray orgs = getOrgs(httpclient, access_token);
            JsonArray repos = getUserRepos(httpclient, access_token);

            
            objectBuilder.add("user", user)
                          // .add("orgs", orgs)
                         .add("repos", repos);
        }        

        return new Viewable("/WEB-INF/tuhao/tuhao", objectBuilder.build());
    }

    private JsonObject getAccessToken(CloseableHttpClient httpclient, String code) throws Exception {

        JsonObject accessTokenEntityAsJsonObject;
        URI uriPost2GetAccessToken = URI.create("https://github.com/login/oauth/access_token");
        
        if (this.githubKey == null) {
            this.githubKey = codec.decrypt(getConfig(TuhaoConfig.class).getEncryptedGithubKey().toCharArray());
        }

        HttpPost httpPost2GetAccessToken = new HttpPost(uriPost2GetAccessToken);
        httpPost2GetAccessToken.addHeader("Accept", MediaType.APPLICATION_JSON);
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("client_id", this.githubId));
        nameValuePairs.add(new BasicNameValuePair("client_secret", this.githubKey));
        nameValuePairs.add(new BasicNameValuePair("code", code));
        httpPost2GetAccessToken.setEntity(new UrlEncodedFormEntity(nameValuePairs));

        try (CloseableHttpResponse accessTokenResponse = httpclient.execute(httpPost2GetAccessToken)) {
            HttpEntity accessTokenEntity = accessTokenResponse.getEntity();
            accessTokenEntityAsJsonObject = mapper.readValue(EntityUtils.toString(accessTokenEntity), JsonObject.class);
            EntityUtils.consume(accessTokenEntity);
        }
        return accessTokenEntityAsJsonObject;
    }

    private JsonObject getUser(CloseableHttpClient httpclient, String access_token) throws Exception {
        JsonObject entityUserAsJsonObject;
        URI uriGetUser = URI.create("https://api.github.com/user");
        HttpGet httpGetUser = new HttpGet(uriGetUser);
        httpGetUser.addHeader("Accept", MediaType.APPLICATION_JSON);
        httpGetUser.addHeader("Authorization", "token " + access_token);

        try (CloseableHttpResponse responseGetUser = httpclient.execute(httpGetUser)) {
            HttpEntity entityUser = responseGetUser.getEntity();
            entityUserAsJsonObject = mapper.readValue(EntityUtils.toString(entityUser), JsonObject.class);
            EntityUtils.consume(entityUser);
        }
        return entityUserAsJsonObject;
    }

    private JsonArray getUserRepos(CloseableHttpClient httpclient, String access_token) throws Exception {
        JsonArray entityUserReposAsJsonArray;
        URI uriGetUserRepos = URI.create("https://api.github.com/user/repos");
        HttpGet httpGetUserRepos = new HttpGet(uriGetUserRepos);
        httpGetUserRepos.addHeader("Accept", MediaType.APPLICATION_JSON);
        httpGetUserRepos.addHeader("Authorization", "token " + access_token);

        try (CloseableHttpResponse responseGetUserRepos = httpclient.execute(httpGetUserRepos)) {
            HttpEntity entityUserRepos = responseGetUserRepos.getEntity();
            entityUserReposAsJsonArray = mapper.readValue(EntityUtils.toString(entityUserRepos), JsonArray.class);
            EntityUtils.consume(entityUserRepos);
        }
        return entityUserReposAsJsonArray;
    }

    @SuppressWarnings("unused")
    private JsonArray getOrgs(CloseableHttpClient httpclient, String access_token) throws Exception {
        JsonArray userOrgsAsJsonArray;
        URI uriGetOrgs = URI.create("https://api.github.com/user/orgs");

        HttpGet httpGetOrgs = new HttpGet(uriGetOrgs);
        httpGetOrgs.addHeader("Accept", MediaType.APPLICATION_JSON);
        httpGetOrgs.addHeader("Authorization", "token " + access_token);

        try (CloseableHttpResponse responseGetOrgs = httpclient.execute(httpGetOrgs)) {
            HttpEntity entityUserOrgs = responseGetOrgs.getEntity();
            userOrgsAsJsonArray = mapper.readValue(EntityUtils.toString(entityUserOrgs), JsonArray.class);
            EntityUtils.consume(entityUserOrgs);
        }
        return userOrgsAsJsonArray;
    }

    @SuppressWarnings("unused")
    private JsonArray getOrgRepos(CloseableHttpClient httpclient, String access_token, String org) throws Exception {
        JsonArray orgReposAsJsonArray;
        URI uriGetOrgRepos = URI.create("https://api.github.com/orgs/"+org+"/repos");

        HttpGet httpGetOrgRepos = new HttpGet(uriGetOrgRepos);
        httpGetOrgRepos.addHeader("Accept", MediaType.APPLICATION_JSON);
        httpGetOrgRepos.addHeader("Authorization", "token " + access_token);

        try (CloseableHttpResponse responseOrgRepos = httpclient.execute(httpGetOrgRepos)) {
            HttpEntity entityOrgRepos = responseOrgRepos.getEntity();
            orgReposAsJsonArray = mapper.readValue(EntityUtils.toString(entityOrgRepos), JsonArray.class);
            EntityUtils.consume(entityOrgRepos);
        }
        return orgReposAsJsonArray;
    }

}
