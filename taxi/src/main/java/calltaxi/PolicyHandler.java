package calltaxi;

import calltaxi.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }
    
    @Autowired
    TaxiRepository taxiRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCalled_(@Payload Called called){

        if(called.isMe()){
            System.out.println("##### listener  : " + called.toJson());
            

            Taxi taxi = new Taxi();
            
            taxi.setCallId(called.getId());
            taxi.setStatus("Assigned");
            
            taxiRepository.save(taxi);
        }
    }

}
