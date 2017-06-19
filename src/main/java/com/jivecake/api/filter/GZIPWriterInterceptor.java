package com.jivecake.api.filter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

@GZip
public class GZIPWriterInterceptor implements WriterInterceptor {
    private final HttpServletRequest request;

    @Inject
    public GZIPWriterInterceptor(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        boolean acceptsGZIP = this.request.getHeader("Accept-Encoding")
            .toLowerCase()
            .contains("gzip");

        if (acceptsGZIP) {
            context.getHeaders().add("Content-Encoding", "gzip");
            OutputStream outputStream = context.getOutputStream();
            context.setOutputStream(new GZIPOutputStream(outputStream));
        }

        context.proceed();
    }
}