package com.nttdata;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.util.Properties;

@ApplicationScoped
public class KafkaProducerService {

    private static final Logger LOGGER = Logger.getLogger(KafkaProducerService.class);

    private final KafkaProducer<String, String> producer;

    public KafkaProducerService() {
        Properties props = new Properties();

        // Cargamos las propiedades que empiezan por "kafka." desde application.properties
        // y removemos el prefijo "kafka." para que el Kafka Client las reconozca.
        ConfigProvider.getConfig().getPropertyNames().forEach(prop -> {
            if (prop.startsWith("kafka.")) {
                String value = ConfigProvider.getConfig().getValue(prop, String.class);
                props.put(prop.substring(6), value);
            }
        });

        // El login handler de Azure busca estas propiedades del sistema por defecto
        System.setProperty("azure.client.id", ConfigProvider.getConfig().getValue("azure.client.id", String.class));
        System.setProperty("azure.client.secret", ConfigProvider.getConfig().getValue("azure.client.secret", String.class));
        System.setProperty("azure.tenant.id", ConfigProvider.getConfig().getValue("azure.tenant.id", String.class));

        this.producer = new KafkaProducer<>(props);
    }

    public void send(String topic, String message) {
        producer.send(new ProducerRecord<>(topic, message), (metadata, exception) -> {
            if (exception != null) {
                LOGGER.error(exception.getMessage(), exception);
            } else {
                LOGGER.infof("Mensaje enviado al topic %s [partition: %d, offset: %d]%n",
                        metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
    }

    // Cerramos el productor cuando la aplicación se detiene
    public void stop() {
        if (producer != null) {
            producer.close();
        }
    }
}
