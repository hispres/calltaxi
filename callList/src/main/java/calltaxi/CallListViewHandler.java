package calltaxi;

import calltaxi.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class CallListViewHandler {


    @Autowired
    private CallListRepository callListRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenCalled_then_CREATE_1 (@Payload Called called) {
        try {
            if (called.isMe()) {
            	
            	CallList callList = new CallList();
                // view 객체 생성
                // view 객체에 이벤트의 Value 를 set 함
            	callList.setCallId(called.getId());
            	callList.setDestination(called.getDestination());
            	callList.setUserId(called.getUserId());
            	callList.setStatus(called.getStatus());
                // view 레파지 토리에 save
            	callListRepository.save(callList);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenAssigned_then_UPDATE_1(@Payload Assigned assigned) {
        try {
            if (assigned.isMe()) {
                // view 객체 조회
                List<CallList> list = callListRepository.findByCallId(assigned.getCallId());
                
                for(CallList callList : list){
                	callList.setAssignId(assigned.getId());
                	callList.setStatus("Assigned");
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                	callListRepository.save(callList);
                }
                
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenPayed_then_UPDATE_2(@Payload Payed payed) {
        try {
            if (payed.isMe()) {
                // view 객체 조회
            	List<CallList> list = callListRepository.findByCallId(payed.getCallId());
                
                for(CallList callList : list){
                	callList.setPaymentId(payed.getId());
                	callList.setStatus("payed");
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                	callListRepository.save(callList);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}