# calltaxi
# 서비스 시나리오
### 기능적 요구사항
1. 고객이 택시를 호출한다.
2. 택시가 배정 한다.
3. 택시가 도착 후 결재한다.

### 비기능적 요구사항
1. 트랜잭션
    1. 택시 도착시 결재가 이루어져야 한다. → Sync 호출
1. 장애격리
    1. 택시 배정이 장애가 발생하더라도 택시 호출은 가능하다. → Async (event-driven), Eventual Consistency
    1. 택시 도착시 결재가 처리되야 도착이 완료된다. → Circuit breaker, fallback
1. 성능
    1. 고객이 택시호출상태를 calllist에서 확인 할 수 있어야 한다. → CQRS 

# Event Storming 결과

![00 이벤트 스토밍결과](https://user-images.githubusercontent.com/77368578/108170084-fbb74300-713c-11eb-87da-cccf1cc5ea45.png)


# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 8084, 8088 이다)
```
cd call
mvn spring-boot:run  

cd payment
mvn spring-boot:run

cd taxi
mvn spring-boot:run 

cd callList
mvn spring-boot:run  

cd gateway
mvn spring-boot:run  
```

## DDD 의 적용
msaez.io 를 통해 구현한 Aggregate 단위로 Entity 를 선언 후, 구현을 진행하였다.

Entity Pattern 과 Repository Pattern 을 적용하기 위해 Spring Data REST 의 RestRepository 를 적용하였다.

**call 서비스의 call.java**

```java 
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
```

- DDD 적용 후 REST API의 테스트를 통하여 정상적으로 동작하는 것을 확인할 수 있었다.  
  
- 원격 주문 (call 동작 후 결과)

![02 API1](https://user-images.githubusercontent.com/77368578/108169391-01f8ef80-713c-11eb-90cc-c768acee3091.png)

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
        - id: call
          uri: http://localhost:8081
          predicates:
            - Path=/calls/** 
        - id: taxi
          uri: http://localhost:8082
          predicates:
            - Path=/taxis/** 
        - id: payment
          uri: http://localhost:8083
          predicates:
            - Path=/payments/** 
        - id: callList
          uri: http://localhost:8084
          predicates:
            - Path= /callLists/**
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
        - id: call
          uri: http://call:8080
          predicates:
            - Path=/calls/** 
        - id: taxi
          uri: http://taxi:8080
          predicates:
            - Path=/taxis/** 
        - id: payment
          uri: http://payment:8080
          predicates:
            - Path=/payments/** 
        - id: callList
          uri: http://callList:8080
          predicates:
            - Path= /callLists/**
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
본 프로젝트에서 View 역할은 CallList 서비스가 수행한다.

- 택시 호출(call) 실행 후 CallList 화면

![03 CALLTAXI_callList](https://user-images.githubusercontent.com/77368578/108169419-0a512a80-713c-11eb-92ff-eea71dd5d6b5.png)

- 택시 도착(arrive) 실행 후 CallList 화면

![04 CALLTAXI_동기3](https://user-images.githubusercontent.com/77368578/108169437-0f15de80-713c-11eb-9c83-9d0414fffa0d.png)

위와 같이 호출을 하게되면 call -> taxi로 호출과 배정이 되고

도착을 하게되면 call -> payment로 도착과 결재가 진행된다.

또한 Correlation을 key를 활용하여 callId Key값을 하고 원하는 주문하고 서비스간의 공유가 이루어 졌다.

위 결과로 서로 다른 마이크로 서비스 간에 트랜잭션이 묶여 있음을 알 수 있다.

# 폴리글랏

call 서비스의 DB와 taxi 의 DB를 다른 DB를 사용하여 폴리글랏을 만족시키고 있다.

**call의 pom.xml DB 설정 코드**
```xml
		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>runtime</scope>
		</dependency>
```

**taxi의 pom.xml DB 설정 코드**
```xml
		<dependency>
			<groupId>org.hsqldb</groupId>
			<artifactId>hsqldb</artifactId>
			<scope>runtime</scope>
		</dependency>
```

# 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 도착(call)->결제(payment) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다.

**call 서비스 내 external.PaymentService**
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

![09 동기 서비시 강제 중지](https://user-images.githubusercontent.com/77368578/108174224-85b5da80-7142-11eb-856d-8d8cca552dee.png)

- 주문 요청시 에러 발생

![09 동기 서비시 강제 중지 실행결과](https://user-images.githubusercontent.com/77368578/108174215-83538080-7142-11eb-8156-8bf87ed26c3a.png)

- Payment 서비스 재기동 후 정상동작 확인

![04 CALLTAXI_동기1](https://user-images.githubusercontent.com/77368578/108169427-0c1aee00-713c-11eb-8a57-d1a3a329f511.png)
![04 CALLTAXI_동기2](https://user-images.githubusercontent.com/77368578/108169432-0d4c1b00-713c-11eb-8c2c-61d69ecb81b1.png)

# 운영

# Deploy / Pipeline

- git에서 소스 가져오기
```
git clone https://github.com/hispres/calltaxi.git
```
- Build 하기
```
cd /calltaxi
cd gateway
mvn package

cd ..
cd call
mvn package

cd ..
cd payment
mvn package

cd ..
cd taxi
mvn package

cd ..
cd calllist
mvn package
```

- Docker Image Push/deploy/서비스생성
```
cd gateway
az acr build --registry skuser06 --image skuser06.azurecr.io/gateway:v1 .
kubectl create ns tutorial

kubectl create deploy gateway --image=skuser06.azurecr.io/gateway:v1 -n tutorial
kubectl expose deploy gateway --type=ClusterIP --port=8080 -n tutorial

cd ..
cd payment
az acr build --registry skuser06 --image skuser06.azurecr.io/payment:v1 .

kubectl create deploy payment --image=skuser06.azurecr.io/payment:v1 -n tutorial
kubectl expose deploy payment --type=ClusterIP --port=8080 -n tutorial

cd ..
cd callList
az acr build --registry skuser06 --image skuser06.azurecr.io/calllist:v1 .

kubectl create deploy calllist --image=skuser06.azurecr.io/calllist:v1 -n tutorial
kubectl expose deploy calllist --type=ClusterIP --port=8080 -n tutorial

cd ..
cd taxi
az acr build --registry skuser06 --image skuser06.azurecr.io/taxi:v1 .

kubectl create deploy taxi --image=skuser06.azurecr.io/taxi:v1 -n tutorial
kubectl expose deploy taxi --type=ClusterIP --port=8080 -n tutorial
```

- yml파일 이용한 deploy
```
cd ..
cd call
az acr build --registry skuser06 --image skuser06.azurecr.io/call:v1 .
```

```
kubectl apply -f ./deployment.yml
```

```
kubectl expose deploy shop --type=ClusterIP --port=8080 -n tutorial
```

- calltaxi/call/kubernetes/deployment.yml 파일 
```yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: call
  namespace: tutorial
  labels:
    app: call
spec:
  replicas: 1
  selector:
    matchLabels:
      app: call
  template:
    metadata:
      labels:
        app: call
    spec:
      containers:
        - name: call
          image: skuser06.azurecr.io/call:v1
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
          env:
            - name: configurl
              valueFrom:
                configMapKeyRef:
                  name: apiurl
                  key: url
```	  
- deploy 완료
![01  서비스 전체](https://user-images.githubusercontent.com/77368578/108169387-00c7c280-713c-11eb-8904-13f96bdb92fc.png)


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

- ConfigMap 사용(/call/src/main/java/calltaxi/external/PaymentService.java) 

```java

      @FeignClient(name="Payment", url="${api.url.Payment}")
      public interface PaymentService {
      
	      @RequestMapping(method= RequestMethod.POST, path="/payments")
              public void pay(@RequestBody Payment payment);
	      
      }
```

- ConfigMap 생성

```
kubectl create configmap apiurl --from-literal=url=http://payment:8080 -n tutorial
```

# 오토스케일 아웃

- 서킷 브레이커는 시스템을 안정되게 운영할 수 있게 해줬지만, 사용자의 요청이 급증하는 경우, 오토스케일 아웃이 필요하다.

>- 단, 부하가 제대로 걸리기 위해서, taxi 서비스의 리소스를 줄여서 재배포한다.(calltaxi/taxi/kubernetes/deployment.yml 수정)

```yaml
          resources:
            limits:
              cpu: 500m
            requests:
              cpu: 200m
```

- 다시 expose 해준다.
```
kubectl expose deploy taxi --type=ClusterIP --port=8080 -n tutorial
```
- taxi 시스템에 replica를 자동으로 늘려줄 수 있도록 HPA를 설정한다. 설정은 CPU 사용량이 15%를 넘어서면 replica를 10개까지 늘려준다.
```
kubectl autoscale deploy taxi --min=1 --max=10 --cpu-percent=15 -n tutorial
```
- siege를 활용해서 워크로드를 1분간 걸어준다. (Cloud 내 siege pod에서 부하줄 것)
```
kubectl exec -it pod/siege -c siege -n tutorial -- /bin/bash
siege -c100 -t60S -r10 -v --content-type "application/json" 'http://taxi:8080/taxis '
```

- 오토스케일 모니터링을 걸어 스케일 아웃이 자동으로 진행됨을 확인한다.
```
kubectl get all -n tutorial
```
![06 autoscale_결과](https://user-images.githubusercontent.com/77368578/108169450-1341fc00-713c-11eb-88db-96ca286cc423.png)

# 서킷 브레이킹

- 서킷 브레이킹 프레임워크의 선택 : Spring FeignClient + Hystrix 옵션을 사용하여 구현함
- Hystrix를 설정 : 요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도
  유지되면 CB 회로가 닫히도록(요청을 빠르게 실패처리, 차단) 설정

- 동기 호출 주체인 call에서 Hystrix 설정 
- call/src/main/resources/application.yml 파일
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
- calltaxi/payment/src/main/java/calltaxi/external/Payment.java
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
siege -c100 -t60S -r10 -v --content-type "application/json" 'http://gateway:8080/calls/1 PUT {"userId": "user01", "destination": "jinju", "status": "pay", "price":100}'
```
- 부하 발생하여 CB가 발동하여 요청 실패처리하였고, 밀린 부하가 다시 처리되면서 호출을 서비스 시작
- 
![05 CB발생결과](https://user-images.githubusercontent.com/77368578/108169446-1210cf00-713c-11eb-95d1-

# 무정지 배포

- 무정지 배포가 되지 않는 readiness 옵션을 제거 설정
calltaxi/taxi/kubernetes/deployment_noReadiness.yml
```yml
    spec:
      containers:
        - name: taxi
          image: skuser06.azurecr.io/taxi:v1
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

![07 무정지배포_READINESS삭제 실행](https://user-images.githubusercontent.com/77368578/108169486-20f78180-713c-11eb-8b42-0d896183ace5.png)
![07 무정지배포_READINESS삭제 결과](https://user-images.githubusercontent.com/77368578/108169475-1d63fa80-713c-11eb-92fe-2384b3125efa.png)

- 무정지 배포를 위한 readiness 옵션 설정
calltaxi/taxi/kubernetes/deployment.yml
```yml
    spec:
      containers:
        - name: taxi
          image: skuser06.azurecr.io/shop:v1
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

![07 무정지배포_READINESS포함 실행](https://user-images.githubusercontent.com/77368578/108169506-26ed6280-713c-11eb-955c-abee37d07b00.png)
![07 무정지배포_READINESS포함 결과](https://user-images.githubusercontent.com/77368578/108169496-248b0880-713c-11eb-9c8c-a125e9296b33.png)

# Self-healing (Liveness Probe)

- Self-healing 확인을 위한 Liveness Probe 옵션 변경
calltaxi/taxi/kubernetes/deployment_liveness.yml
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

- taxi pod에 Liveness Probe 옵션 적용 확인

![08 SELF HEALING_설정변경 확인](https://user-images.githubusercontent.com/77368578/108169526-2c4aad00-713c-11eb-86db-764ffe9a6662.png)

- taxi pod에서 적용 시 retry발생 확인

![08 SELF HEALING_설정변경 결과](https://user-images.githubusercontent.com/77368578/108169513-29e85300-713c-11eb-9321-51ae25c4b191.png)

