package calltaxi;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name="CallList_table")
public class CallList {

        @Id
        @GeneratedValue(strategy=GenerationType.AUTO)
        private Long id;
        private Long callId;
        private Long assignId;
        private Long paymentId;
        private String destination;
        private String userId;
        private String status;


        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
        public Long getCallId() {
            return callId;
        }

        public void setCallId(Long callId) {
            this.callId = callId;
        }
        public Long getAssignId() {
            return assignId;
        }

        public void setAssignId(Long assignId) {
            this.assignId = assignId;
        }
        public Long getPaymentId() {
            return paymentId;
        }

        public void setPaymentId(Long paymentId) {
            this.paymentId = paymentId;
        }
        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

}
