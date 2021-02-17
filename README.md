# winterone
# 서비스 시나리오
### 기능적 요구사항
1. 고객이 음료를 주문한다.
2. 고객이 결제를 한다.
3. 결제가 완료되면 주문내역을 매장으로 보낸다.
4. 매장에서 주문을 할당한다. 
5. 고객이 주문을 취소할 수 있다.
6. 고객이 중간중간 주문상태를 조회한다.


### 비기능적 요구사항
1. 트랜잭션
    1. 결제가 되지않으면 주문이 진행되지 않는다 → Sync 호출
1. 장애격리
    1. 결제시스템에서 장애가 발생해도 주문취소는 24시간 받을 수 있어야한다 → Async (event-driven), Eventual Consistency
    1. 주문량이 많아 결재시스템 과중되면 잠시 주문을 받지 않고 잠시후에 하도록 유도한다 → Circuit breaker, fallback
1. 성능
    1. 고객이 주문상태를 SirenOrderHome에서 확인 할 수 있어야 한다. → CQRS 

# Event Storming 결과

![EventStormingV1](https://user-images.githubusercontent.com/53815271/107929718-66417500-6fbd-11eb-9754-5dd92a06afe5.png)

# 헥사고날 아키텍처 다이어그램 도출

![폴리글랏 아키텍처](https://user-images.githubusercontent.com/53815271/107929578-2f6b5f00-6fbd-11eb-9176-ac3455d5c7be.png)


# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 8084, 8088 이다)
```
cd SirenOrder
mvn spring-boot:run  

cd Payment
mvn spring-boot:run

cd SirenOrderHome
mvn spring-boot:run 

cd Shop
mvn spring-boot:run  

cd gateway
mvn spring-boot:run  
```

## DDD 의 적용
msaez.io 를 통해 구현한 Aggregate 단위로 Entity 를 선언 후, 구현을 진행하였다.

Entity Pattern 과 Repository Pattern 을 적용하기 위해 Spring Data REST 의 RestRepository 를 적용하였다.

**SirenOrder 서비스의 SirenOrder.java**

```java 
package winterschoolone;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

import winterschoolone.external.Payment;
import winterschoolone.external.PaymentService;

import java.util.List;

@Entity
@Table(name="SirenOrder_table")
public class SirenOrder {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String userId;
    private String menuId;
    private Integer qty;
    private String status;

    @PostPersist
    public void onPostPersist(){
    	Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        Payment payment = new Payment();
        payment.setOrderId(this.getId());
        payment.setMenuId(this.menuId);
        payment.setQty(this.getQty());
        payment.setUserId(this.getUserId());
        // mappings goes here
        SirenOrderApplication.applicationContext.getBean(PaymentService.class)
        .pay(payment);
    }

    @PostUpdate
    public void onPostUpdate(){
        Updated updated = new Updated();
        BeanUtils.copyProperties(this, updated);
        updated.publishAfterCommit();


    }

    @PreRemove
    public void onPreRemove(){
        OrderCancelled orderCancelled = new OrderCancelled();
        BeanUtils.copyProperties(this, orderCancelled);
        orderCancelled.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getMenuId() {
        return menuId;
    }

    public void setMenuId(String menuId) {
        this.menuId = menuId;
    }
    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    
}
```

**SirenOrder 서비스의 PolicyHandler.java**
```java
package winterschoolone;

import winterschoolone.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

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
	SirenOrderRepository sirenOrderRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverAssigned_(@Payload Assigned assigned){

        if(assigned.isMe()){
        	Optional<SirenOrder> optional = sirenOrderRepository.findById(assigned.getOrderId());
        	if(optional != null && optional.isPresent())
        	{
        		SirenOrder sirenOrder = optional.get();
        		
        		sirenOrder.setStatus("Assigned");
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                // view 레파지 토리에 save
            	sirenOrderRepository.save(sirenOrder);
        	}
            
            System.out.println("##### listener  : " + assigned.toJson());
        }
    }

}
```

- DDD 적용 후 REST API의 테스트를 통하여 정상적으로 동작하는 것을 확인할 수 있었다.  
  
- 원격 주문 (SirenOrder 동작 후 결과)

![증빙1](https://user-images.githubusercontent.com/53815271/107907569-64fd5180-6f97-11eb-9f1e-cb1fb97fd4ff.png)

# GateWay 적용
API GateWay를 통하여 마이크로 서비스들의 집입점을 통일할 수 있다.
다음과 같이 GateWay를 적용하였다.

```yaml
server:
  port: 8088

---

spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: SirenOrder
          uri: http://localhost:8081
          predicates:
            - Path=/sirenOrders/** 
        - id: Payment
          uri: http://localhost:8082
          predicates:
            - Path=/payments/** 
        - id: Shop
          uri: http://localhost:8083
          predicates:
            - Path=/shops/** 
        - id: SirenOrderHome
          uri: http://localhost:8084
          predicates:
            - Path= /sirenOrderHomes/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true


---

spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: SirenOrder
          uri: http://SirenOrder:8080
          predicates:
            - Path=/sirenOrders/** 
        - id: Payment
          uri: http://Payment:8080
          predicates:
            - Path=/payments/** 
        - id: Shop
          uri: http://Shop:8080
          predicates:
            - Path=/shops/** 
        - id: SirenOrderHome
          uri: http://SirenOrderHome:8080
          predicates:
            - Path= /sirenOrderHomes/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080

```

# CQRS
Materialized View 를 구현하여, 타 마이크로서비스의 데이터 원본에 접근없이(Composite 서비스나 조인SQL 등 없이) 도 내 서비스의 화면 구성과 잦은 조회가 가능하게 구현해 두었다.
본 프로젝트에서 View 역할은 SirenOrderHomes 서비스가 수행한다.

- 주문(ordered) 실행 후 SirenOrderHomes 화면

![증빙2](https://user-images.githubusercontent.com/53815271/107907619-7e060280-6f97-11eb-89b3-4e3236ff9ddd.png)

- 주문(OrderCancelled) 취소 후 SirenOrderHomes 화면

![증빙3](https://user-images.githubusercontent.com/53815271/107908429-5e6fd980-6f99-11eb-8afc-2a2c070a1663.png)

위와 같이 주문을 하게되면 SirenOrder -> Payment -> Shop -> SirenOrder 로 주문이 Assigend 되고

주문 취소가 되면 Status가 refunded로 Update 되는 것을 볼 수 있다.

또한 Correlation을 key를 활용하여 orderId를 Key값을 하고 원하는 주문하고 서비스간의 공유가 이루어 졌다.

위 결과로 서로 다른 마이크로 서비스 간에 트랜잭션이 묶여 있음을 알 수 있다.

# 폴리글랏

Shop 서비스의 DB와 SirenOrder의 DB를 다른 DB를 사용하여 폴리글랏을 만족시키고 있다.

**Shop의 pom.xml DB 설정 코드**

![증빙5](https://user-images.githubusercontent.com/53815271/107909600-e2c35c00-6f9b-11eb-8ec4-e8ef46c07949.png)

**SirenOrder의 pom.xml DB 설정 코드**

![증빙4](https://user-images.githubusercontent.com/53815271/107909551-d17a4f80-6f9b-11eb-8af2-71b4d0112206.png)

# 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 주문(SirenOrder)->결제(pay) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다.

**SirenOrder 서비스 내 external.PaymentService**
```java
package winterschoolone.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="Payment", url="${api.url.Payment}")
public interface PaymentService {

    @RequestMapping(method= RequestMethod.POST, path="/payments")
    public void pay(@RequestBody Payment payment);

}
```

**동작 확인**
- 잠시 Payment 서비스 중시

![증빙6](https://user-images.githubusercontent.com/53815271/107910391-a85abe80-6f9d-11eb-8dd5-6b7a4d1cdc01.png)

- 주문 요청시 에러 발생

![증빙7](https://user-images.githubusercontent.com/53815271/107910392-a8f35500-6f9d-11eb-98e4-2cf9fa2fbd46.png)

- Payment 서비스 재기동 후 정상동작 확인

![증빙8](https://user-images.githubusercontent.com/53815271/107910393-a98beb80-6f9d-11eb-833f-150d11f51067.png)
![증빙9](https://user-images.githubusercontent.com/53815271/107910394-a98beb80-6f9d-11eb-841c-aa6ab38cf99b.png)

# 운영

# Deploy / Pipeline

- git에서 소스 가져오기
```
git clone https://github.com/hispres/winterone.git
```
- Build 하기
```
cd /winterone
cd gateway
mvn package

cd ..
cd sirenorder
mvn package

cd ..
cd payment
mvn package

cd ..
cd shop
mvn package

cd ..
cd sirenorderhome
mvn package
```

- Docker Image Push/deploy/서비스생성
```
cd gateway
az acr build --registry skteam01 --image skteam01.azurecr.io/gateway:v1 .
kubectl create ns tutorial

kubectl create deploy gateway --image=skteam01.azurecr.io/gateway:v1 -n tutorial
kubectl expose deploy gateway --type=ClusterIP --port=8080 -n tutorial

cd ..
cd payment
az acr build --registry skteam01 --image skteam01.azurecr.io/payment:v1 .

kubectl create deploy payment --image=skteam01.azurecr.io/payment:v1 -n tutorial
kubectl expose deploy payment --type=ClusterIP --port=8080 -n tutorial

cd ..
cd shop
az acr build --registry skteam01 --image skteam01.azurecr.io/sirenorderhome:v1 .

kubectl create deploy shop --image=skteam01.azurecr.io/sirenorderhome:v1 -n tutorial
kubectl expose deploy shop --type=ClusterIP --port=8080 -n tutorial

cd ..
cd sirenorderhome
az acr build --registry skteam01 --image skteam01.azurecr.io/sirenorderhome:v1 .

kubectl create deploy sirenorderhome --image=skteam01.azurecr.io/sirenorderhome:v1 -n tutorial
kubectl expose deploy sirenorderhome --type=ClusterIP --port=8080 -n tutorial
```

- yml파일 이용한 deploy
```
cd ..
cd SirenOrder
az acr build --registry skteam01 --image skteam01.azurecr.io/sirenorder:v1 .
```
![증빙7](https://user-images.githubusercontent.com/77368578/107920373-35a70e80-6fb0-11eb-8024-a6fc42fea93f.png)

```
kubectl expose deploy shop --type=ClusterIP --port=8080 -n tutorial
```

- winterone/SirenOrder/kubernetes/deployment.yml 파일 
```yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sirenorder
  namespace: tutorial
  labels:
    app: sirenorder
spec:
  replicas: 1
  selector:
    matchLabels:
      app: sirenorder
  template:
    metadata:
      labels:
        app: sirenorder
    spec:
      containers:
        - name: sirenorder
          image: hispres.azurecr.io/sirenorder:v4
          ports:
            - containerPort: 8080
          env:
            - name: configurl
              valueFrom:
                configMapKeyRef:
                  name: apiurl
                  key: url
```	  
- deploy 완료

![전체 MSA](https://user-images.githubusercontent.com/77368578/108006011-992b4d80-703d-11eb-8df9-a2cea19aa693.png)

# ConfigMap 
- 시스템별로 변경 가능성이 있는 설정들을 ConfigMap을 사용하여 관리

- application.yml 파일에 ${configurl} 설정

```yaml
      feign:
        hystrix:
          enabled: true
      hystrix:
        command:
          default:
            execution.isolation.thread.timeoutInMilliseconds: 610
      api:
        url:
          Payment: ${configurl}

```

- ConfigMap 사용(/SirenOrder/src/main/java/winterschoolone/external/PaymentService.java) 

```java

      @FeignClient(name="Payment", url="${api.url.Payment}")
      public interface PaymentService {
      
	      @RequestMapping(method= RequestMethod.POST, path="/payments")
              public void pay(@RequestBody Payment payment);
	      
      }
```

- Deployment.yml 에 ConfigMap 적용

![image](https://user-images.githubusercontent.com/74236548/107925407-c2a19600-6fb7-11eb-9325-6bd2cd94455c.png)

- ConfigMap 생성

```
kubectl create configmap apiurl --from-literal=url=http://10.0.92.205:8080 -n tutorial
```

   ![image](https://user-images.githubusercontent.com/74236548/107968395-aa4e6d00-6ff1-11eb-9112-2f1d77a561ad.png)

# 오토스케일 아웃

- 서킷 브레이커는 시스템을 안정되게 운영할 수 있게 해줬지만, 사용자의 요청이 급증하는 경우, 오토스케일 아웃이 필요하다.

>- 단, 부하가 제대로 걸리기 위해서, recipe 서비스의 리소스를 줄여서 재배포한다.(winterone/Shop/kubernetes/deployment.yml 수정)

```yaml
          resources:
            limits:
              cpu: 500m
            requests:
              cpu: 200m
```

- 다시 expose 해준다.
```
kubectl expose deploy shop --type=ClusterIP --port=8080 -n tutorial
```
- recipe 시스템에 replica를 자동으로 늘려줄 수 있도록 HPA를 설정한다. 설정은 CPU 사용량이 15%를 넘어서면 replica를 10개까지 늘려준다.
```
kubectl autoscale deploy shop --min=1 --max=10 --cpu-percent=15 -n tutorial
```
- siege를 활용해서 워크로드를 2분간 걸어준다. (Cloud 내 siege pod에서 부하줄 것)
```
kubectl exec -it pod/siege -c siege -n tutorial -- /bin/bash
siege -c100 -t120S -r10 -v --content-type "application/json" 'http://10.0.14.180:8080/shops POST {"orderId": 111, "userId": "user10", "menuId": "menu10", "qty":10}'
```
![autoscale(hpa) 실행 및 부하발생](https://user-images.githubusercontent.com/77368578/107917594-8405de80-6fab-11eb-830c-b15f255b2314.png)
- 오토스케일 모니터링을 걸어 스케일 아웃이 자동으로 진행됨을 확인한다.
```
kubectl get all -n tutorial
```
![autoscale(hpa)결과](https://user-images.githubusercontent.com/77368578/107917604-8831fc00-6fab-11eb-83bb-9ba19159d00d.png)

# 서킷 브레이킹

- 서킷 브레이킹 프레임워크의 선택 : Spring FeignClient + Hystrix 옵션을 사용하여 구현함
- Hystrix를 설정 : 요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도
  유지되면 CB 회로가 닫히도록(요청을 빠르게 실패처리, 차단) 설정

- 동기 호출 주체인 SirenOrder에서 Hystrix 설정 
- SirenOrder/src/main/resources/application.yml 파일
```yaml
feign:
  hystrix:
    enabled: true
hystrix:
  command:
    default:
      execution.isolation.thread.timeoutInMilliseconds: 610
```

- 부하에 대한 지연시간 발생코드
- winterone/SirenOrder/src/main/java/winterschoolone/external/PaymentService.java
``` java
    @PostPersist
    public void onPostPersist(){
        Payed payed = new Payed();
        BeanUtils.copyProperties(this, payed);
        payed.publishAfterCommit();
        
        try {
                Thread.currentThread().sleep((long) (400 + Math.random() * 220));
        } catch (InterruptedException e) {
                e.printStackTrace();
        }
    }
```

- 부하 테스터 siege툴을 통한 서킷 브레이커 동작확인 :
  
  동시 사용자 100명, 60초 동안 실시 
```
siege -c100 -t60S -r10 -v --content-type "application/json" 'http://10.0.14.180:8080/sirenOrders 
POST {"userId": "user10", "menuId": "menu10", "qty":10}'
```
- 부하 발생하여 CB가 발동하여 요청 실패처리하였고, 밀린 부하가 다시 처리되면서 SirenOrders를 받기 시작

![증빙10](https://user-images.githubusercontent.com/77368578/107917672-a8fa5180-6fab-11eb-9864-69af16a94e5e.png)

# 무정지 배포

- 무정지 배포가 되지 않는 readiness 옵션을 제거 설정
winterone/Shop/kubernetes/deployment_n_readiness.yml
```yml
    spec:
      containers:
        - name: shop
          image: hispres.azurecr.io/shop:v1
          ports:
            - containerPort: 8080
#          readinessProbe:
#            httpGet:
#              path: '/actuator/health'
#              port: 8080
#            initialDelaySeconds: 10
#            timeoutSeconds: 2
#            periodSeconds: 5
#            failureThreshold: 10
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```
- 무정지 배포가 되지 않아 Siege 결과 Availability가 100%가 되지 않음

![무정지배포(readiness 제외) 실행](https://user-images.githubusercontent.com/77368578/108004272-c0cbe700-7038-11eb-94c4-22a0785a7ebc.png)
![무정지배포(readiness 제외) 실행결과](https://user-images.githubusercontent.com/77368578/108004276-c295aa80-7038-11eb-9618-1c85fe0a2f53.png)

- 무정지 배포를 위한 readiness 옵션 설정
winterone/Shop/kubernetes/deployment.yml
```yml
    spec:
      containers:
        - name: shop
          image: hispres.azurecr.io/shop:v1
          ports:
            - containerPort: 8080
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```

- 무정지 배포를 위한 readiness 옵션 설정 후 적용 시 Siege 결과 Availability가 100% 확인

![무정지배포(readiness 포함) 설정 및 실행](https://user-images.githubusercontent.com/77368578/108004281-c75a5e80-7038-11eb-857d-72a1c8bde94c.png)
![무정지배포(readiness 포함) 설정 결과](https://user-images.githubusercontent.com/77368578/108004284-ca554f00-7038-11eb-8f62-9fcb3b069ed2.png)

# Self-healing (Liveness Probe)

- Self-healing 확인을 위한 Liveness Probe 옵션 변경
winterone/Shop/kubernetes/deployment_live.yml
```yml
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8081
            initialDelaySeconds: 5
            periodSeconds: 5
```

- Shop pod에 Liveness Probe 옵션 적용 확인

![self-healing설정 결과](https://user-images.githubusercontent.com/77368578/108004513-697a4680-7039-11eb-917a-1e100ddd2ccd.png)

- Shop pod에서 적용 시 retry발생 확인

![self-healing설정 후 restart증적](https://user-images.githubusercontent.com/77368578/108004507-6717ec80-7039-11eb-809f-67316db013c6.png)

