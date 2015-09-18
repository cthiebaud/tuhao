package net.aequologica.neo.tuhao.jaxrs;

import javax.ws.rs.ApplicationPath;

import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.mvc.jsp.JspMvcFeature;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

@ApplicationPath("github-api")
public class Application extends ResourceConfig {

    public Application() {

        register(LoggingFilter.class);
        register(JspMvcFeature.class);
        register(JacksonJsonProvider.class);
        register(Resource.class);

    }
}
