package app.premierleague.endpoint;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;
import org.springframework.core.io.ClassPathResource;

@EnableWs
@Configuration
public class WsConfig {
  private static final String NS = "http://pltracker.com/match";

  @Bean
  public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext ctx) {
    MessageDispatcherServlet servlet = new MessageDispatcherServlet();
    servlet.setApplicationContext(ctx);
    servlet.setTransformWsdlLocations(true);
    return new ServletRegistrationBean<>(servlet, "/ws/*");
  }

  @Bean
  public XsdSchema matchesSchema() {
    return new SimpleXsdSchema(new ClassPathResource("matches.xsd"));
  }

  @Bean(name = "matches")
  public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema matchesSchema) {
    DefaultWsdl11Definition def = new DefaultWsdl11Definition();
    def.setPortTypeName("MatchesPort");
    def.setLocationUri("/ws");
    def.setTargetNamespace(NS);
    def.setSchema(matchesSchema);
    return def;
  }

  @Bean
  public Jaxb2Marshaller marshaller() {
    Jaxb2Marshaller m = new Jaxb2Marshaller();
    m.setContextPath("app.premierleague.ws");
    return m;
  }
}
