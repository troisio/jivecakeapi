package com.jivecake.api.filter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;

import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.Datastore;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.JWTVerifyException;
import com.jivecake.api.model.Request;

@Log
public class LogFilter implements ContainerRequestFilter {
    @Context
    private HttpServletRequest request;
    @Context
    private ResourceInfo resourceInfo;
    private final List<JWTVerifier> verifiers;
    private final Datastore datastore;

    @Inject
    public LogFilter(Datastore datastore, List<JWTVerifier> verifiers) {
        this.datastore = datastore;
        this.verifiers = verifiers;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        Log log = this.resourceInfo.getResourceMethod().getAnnotation(Log.class);

        Request request = new Request();
        URI uri = requestContext.getUriInfo().getRequestUri();
        request.cookies = requestContext.getCookies();
        request.uri = uri.toString();
        request.path = uri.getPath();
        request.ip = this.request.getRemoteAddr();
        request.headers = requestContext.getHeaders();
        request.query = this.request.getParameterMap();
        request.date = requestContext.getDate();
        request.user_id = null;
        request.timeCreated = new Date();

        if (log.body()) {
            String requestEncoding = this.request.getCharacterEncoding();
            String encoding = requestEncoding == null ? "UTF-8" : requestEncoding;

            StringWriter writer = new StringWriter();
            IOUtils.copy(requestContext.getEntityStream(), writer, encoding);
            request.body = writer.toString();
            requestContext.setEntityStream(new ByteArrayInputStream(request.body.getBytes()));
        }

        String authorization = requestContext.getHeaderString("Authorization");

        if (authorization != null && authorization.startsWith("Bearer .")) {
            String token = authorization.substring("Bearer ".length());

            for (JWTVerifier verifier: this.verifiers) {
                try {
                    Map<String, Object> claims = verifier.verify(token);
                    request.user_id = (String)claims.get("sub");
                    break;
                } catch (InvalidKeyException | NoSuchAlgorithmException | IllegalStateException | SignatureException | IOException | JWTVerifyException e) {
                }
            }
        }

        this.datastore.save(request);
    }
}