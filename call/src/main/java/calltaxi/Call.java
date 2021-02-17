package calltaxi;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

import calltaxi.external.Payment;
import calltaxi.external.PaymentService;

import java.util.List;

@Entity
@Table(name="Call_table")
public class Call {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String destination;
    private String status;
    private String userId;
    private Integer price;

    @PostPersist
    public void onPostPersist(){
        Called called = new Called();
        BeanUtils.copyProperties(this, called);
        called.publishAfterCommit();


    }

    @PostUpdate
    public void onPostUpdate(){
        Arrived arrived = new Arrived();
        BeanUtils.copyProperties(this, arrived);
        arrived.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        Payment payment = new Payment();
        // mappings goes here
        
        payment.setCallId(this.getId());
        payment.setPrice(this.getPrice());
        payment.setUserId(this.getUserId());
        payment.setStatus("payed");
        
        CallApplication.applicationContext.getBean(PaymentService.class)
            .pay(payment);

    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }




}
