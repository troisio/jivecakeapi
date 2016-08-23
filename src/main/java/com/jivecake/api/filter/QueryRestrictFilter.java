package com.jivecake.api.filter;

import java.util.List;
import java.util.Map;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

@QueryRestrict(hasAny=false, target={})
public class QueryRestrictFilter implements ContainerRequestFilter {
     @Context
     private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext context) {
        QueryRestrict[] restrictions = this.resourceInfo.getResourceMethod().getAnnotationsByType(QueryRestrict.class);

        for (QueryRestrict restrict: restrictions) {
            boolean passes = true;

            if (restrict.hasAny()) {
                Map<String, List<String>> query = context.getUriInfo().getQueryParameters();

                boolean containsAny = restrict.target().length == 0;

                for (String parameter: restrict.target()) {
                    List<String> values = query.get(parameter);

                   if (values != null && !values.isEmpty()) {
                       containsAny = true;
                       break;
                   }
                }

                passes = containsAny;
            }

            if (!passes) {
                context.abortWith(
                    Response.status(Response.Status.BAD_REQUEST).build()
                );

                break;
            }
        }
    }
}