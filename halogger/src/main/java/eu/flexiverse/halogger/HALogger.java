package eu.flexiverse.halogger;

import java.net.URI;
import java.util.Scanner;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@SpringBootApplication
@ComponentScan(basePackages = {"eu.flexiverse.halogger"})
@PropertySource("file:config/halogger-zem.properties")
@ConfigurationProperties(prefix = "ha")
public class HALogger implements CommandLineRunner {
	private static final Logger logger = LoggerFactory.getLogger(HALogger.class);

    @Value("${user:admin}")
    private String user;

    @Value("${password}")
    private String password;

    @Value("${token}")
    private String token;

    @Value("${host:localhost}")
    private String host;

    @Value("${port:8123}")
    private int port;
    
    @Value("${restResourceUrl}")
	private String restResourceUrl;

    @Value("${elastichost}")
    private String esHost;
    
    @Value("${elasticproto:http}")
    private String esProto;

    @Value("${elasticport}")
    private Integer esPort;

	String wsUrl;
    private static WebSocketHandler m_webSocketHandler = null;

    public long count = 0;

    public static void main(String[] args) {

    	SpringApplicationBuilder springApplication = new SpringApplicationBuilder(HALogger.class);

    	springApplication.web(WebApplicationType.NONE); // .REACTIVE, .SERVLET
        
    	ApplicationPidFileWriter pidwriter = new ApplicationPidFileWriter("halogger.pid");
        	
    	springApplication.run(args).registerShutdownHook();
    }
    
    @Override
    public void run(String... args) throws Exception {
    	wsUrl = "ws://" + host + ":" + port + "/api/websocket";
    	
    	
    	RestHighLevelClient client = new RestHighLevelClient(
    	        RestClient.builder(
    	                new HttpHost(esHost, esPort, esProto)));
    	
    	
        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();

        logger.info("Configuring WebSocket endpoint:\n" + wsUrl);
        
        URI wsUri = new URI(wsUrl);
    	
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        
        WebSocketHandler webSocketHandler = new HassWebSocketHandler();
        m_webSocketHandler = webSocketHandler;
        
        if(token != null)
            ((HassWebSocketHandler)webSocketHandler).initWithToken(token);
        else if(password != null)
        	((HassWebSocketHandler)webSocketHandler).initWithPassword(password);
        
        ((HassWebSocketHandler)webSocketHandler).addEsClient(client);
        
        webSocketClient.doHandshake(webSocketHandler, wsHeaders, wsUri);
        
        /*
        
        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setTaskScheduler(new ConcurrentTaskScheduler());

        StompSessionHandler sessionHandler = new HassStompSessionHandler();
        stompClient.connect(wsUrl, wsHeaders, sessionHandler);
        */
        
        logger.info("\nYou are now connected to Home Assistant");
        
        while(new Scanner(System.in).nextLine() != null); //Don't close immediately.
        
        // Remove subscription
        //session.send
        
        client.close();
    }
    
    
    public void shutdownApplication() {
    	((HassWebSocketHandler)m_webSocketHandler).stopSubscription();
    }
    
    
     /**
     * Creates a new instance of the PropertySourcesPlaceholderConfigurer
     * to pass property sources from the application.properties file.
     *
     * @return   PropertySourcesPlaceholderConfigurer new instance
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
