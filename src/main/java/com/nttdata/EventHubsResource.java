package com.nttdata;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;

@Path("/eventhubs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.TEXT_PLAIN)
public class EventHubsResource {

    @Inject
    KafkaProducerService producerService;

    @POST
    @Path("/send")
    public Response sendMessage(@QueryParam("topic") String topic, String message) {
        if (topic == null || topic.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Debe indicar el nombre del Event Hub (topic) en el query parameter\"}")
                    .build();
        }

        try {
            producerService.send(topic, message + " Time:" + LocalDateTime.now());
            return Response.ok("{\"status\": \"Mensaje enviado a cola de procesamiento\"}").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
}
