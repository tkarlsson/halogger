package eu.flexiverse.halogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HassWebSocketHandler extends TextWebSocketHandler {
	private static final Logger logger = LoggerFactory.getLogger(TextWebSocketHandler.class);

	private static final HashSet<String> loggIds = new HashSet<String>();
	
	private static WebSocketSession m_session = null;
	
	private static AtomicInteger m_currentCommandId = new AtomicInteger(1);
	
	private String m_auth = null;
	private boolean m_useToken = false;
	
	private RestHighLevelClient m_esClient = null;
	
	ArrayList<String> m_subscriptions = new ArrayList<String>();
	
	public void initWithPassword(String auth) {
		this.m_auth = auth;
		this.m_useToken = false;
	}

	public void initWithToken(String auth) {
		this.m_auth = auth;
		this.m_useToken = true;
	}

	public void addEsClient(RestHighLevelClient esClient) {
		this.m_esClient = esClient;
	}
	
	public void stopSubscription() {
		try {
			for(String subid : m_subscriptions) {
				m_session.sendMessage(new TextMessage("{\"id\": 1, \"type\": \"unsubscribe_events\", \"subscription\": " + subid + "}\n".getBytes()));
			}
		} catch (IOException ioe) {
			logger.error("Couldn't cancel subscription: " + ioe.getMessage());
		}
	}

	
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		
		m_session = session;
		//TextMessage msg = new TextMessage("{\"type\": \"auth\", \"access_token\": \"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJiNmQ5ODU0MmY2M2U0NTNmOGyYSIsImlhdCI6MTU0Mzg3NTg1MywiZXhwIjoxODU5MjM1ODUzfQ.6N4fvWRzDo3BfpdsxuECMYPmZPfOfgOjhPb1XJicPes\"}\n".getBytes());
		//session.sendMessage(msg);
		//System.out.println("Sent auth:");
		//System.out.println(msg.getPayload().toString());
		
		//session.sendMessage(new TextMessage("{\"id\": 1, \"type\": \"subscribe_events\", \"event_type\": \"state_changed\"}\n".getBytes()));
		
		loggIds.add("sensor.fibaro_wall_plug_gen5_livingroom_1_energy");
		loggIds.add("sensor.fibaro_wall_plug_gen5_livingroom_1_power");
		
		loggIds.add("sensor.fibaro_wall_plug_gen5_livingroom_2_energy");
		loggIds.add("sensor.fibaro_wall_plug_gen5_livingroom_2_power");
		
		loggIds.add("sensor.fibaro_wall_plug_gen5_server_energy");
		loggIds.add("sensor.fibaro_wall_plug_gen5_server_power");
		
		//loggIds.add("sensor.aeotec_zw096_smart_switch_6_front_bedroom_id10_current");
		//loggIds.add("sensor.aeotec_zw096_smart_switch_6_front_bedroom_id10_voltage");
	}
	
	@Override
	public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
		// TODO Auto-generated method stub
		if(message.getPayload().toString().contains("auth_required"))
		{
			System.out.println("Got auth request");
			TextMessage authmsg = null;
			
			if(m_useToken) {
				authmsg = new TextMessage("{" +
						  "\"type\": \"auth\"," +
						  "\"access_token\": \"" + m_auth + "\"" +
						"}");
			}
			else {
				authmsg = new TextMessage("{" +
						  "\"type\": \"auth\"," +
						  "\"api_password\": \"" + m_auth + "\"" +
						"}");
			}
				
			session.sendMessage(authmsg);
			
			return;
		}

		if(message.getPayload().toString().contains("auth_ok")) {
			logger.info("Authenticated successfully");
			
			// Start subscribing to relevant events
			Integer tmpCmdId =  m_currentCommandId.getAndIncrement();
			session.sendMessage(new TextMessage("{\"id\": " + tmpCmdId + ", \"type\": \"subscribe_events\", \"event_type\": \"state_changed\"}\n".getBytes()));
			
			m_subscriptions.add(tmpCmdId.toString());
			
			return;
		}
		else if(message.getPayload().toString().contains("result")) {
			System.out.println("Breaking due to confirmation message");
			return;
		}

		
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(((TextMessage)message).getPayload());

		if(logger.isDebugEnabled()) {
			System.out.println();
			System.out.println(prettyPrintJsonString(root));
		}
		System.out.println(prettyPrintJsonString(root));

		JsonNode event = root.path("event");
		JsonNode data = event.path("data");
		JsonNode new_state = data.path("new_state");
		JsonNode new_state_attributes = new_state.path("attributes");
		
		if(loggIds.contains(new_state.path("entity_id").asText())) {
			logger.debug("--- Will log message ---");
			logger.debug(event.path("origin").toString());
			logger.debug(new_state_attributes.path("friendly_name").toString());
			logger.debug(new_state.path("entity_id").toString());
			logger.debug(new_state_attributes.path("node_id").toString());
			logger.debug(new_state_attributes.path("value_index").toString());
			logger.debug(new_state.path("state").toString());
			logger.debug(new_state.path("last_changed").toString());
			
			System.out.println();
			System.out.println(prettyPrintJsonString(new_state));
			
			// Log everything to ES
			String jsonString = prettyPrintJsonString(new_state);
			
			System.out.println("== Logging to ES ==");
			System.out.println(jsonString);

			if(m_esClient != null)
			{
				IndexRequest request = new IndexRequest(
				        "hass", 
				        "doc");
				
				request.source(jsonString, XContentType.JSON);
				
				m_esClient.index(request, RequestOptions.DEFAULT);
			}

		}
		else {
			// Log everything to ES
			String jsonString = prettyPrintJsonString(new_state);
			
			logger.debug("== Logging to ES ==");
			logger.debug(jsonString);

			if(m_esClient != null)
			{
				IndexRequest request = new IndexRequest(
				        "hass", 
				        "doc");
				
				request.source(jsonString, XContentType.JSON);
				
				m_esClient.index(request, RequestOptions.DEFAULT);
			}
		}
		
		
	}
	
	public String prettyPrintJsonString(JsonNode jsonNode) {
	    try {
	        ObjectMapper mapper = new ObjectMapper();
	        Object json = mapper.readValue(jsonNode.toString(), Object.class);
	        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
	    } catch (Exception e) {
	        return "Sorry, pretty print didn't work";
	    }
	}
	
	
	
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		// TODO Auto-generated method stub
		super.afterConnectionClosed(session, status);
	}
	
	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		session.close(CloseStatus.SERVER_ERROR);;
	}
}
