# redisDelayHandler

#### 介绍
实现redis延迟（延时）任务处理器（设计中）。
针对delayMessage项目中的Redis延迟任务实现方案，简化其延迟任务处理的逻辑复杂度，增强部分其他功能

#### 优势
相比于RabbitMQ，该项目的优势在于可以在同一个key中处理多个不同逻辑、不同延迟时间的任务。
RabbitMQ受限于队列的先进先出策略，同一个队列中后进的消息就算延迟时间更短，也不能出队列到死信队列中执行延迟任务。

#### 软件架构
支持任务轮询的三种模型
1.  public模型：公共轮询线程，key等于delay.task.public
2.  customize模型：自定义轮询线程，获取数据后，根据任务类型分别执行处理器Handler
3.  exclusive模型：独立轮询线程，获取数据后，调用各类型的处理器Handler执行任务

#### 使用说明

第一步：添加redisDelayHandler与spring-boot-starter-data-redis依赖
```xml
<dependency>
    <groupId>com.five</groupId>
    <artifactId>redisDelayHandler</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <optional>true</optional>
</dependency>
```

第二步：设置redis与redisDelayHandler的基本参数
```yaml
spring:
  redis:
    host: 127.0.0.1
    password:
    jedis:
      pool:
        max-active: 10
        max-wait: 10
        max-idle: 10
        min-idle: 1
delay:
  handler:
    initialDelay: 5000  # 毫秒，项目初始化时，启动轮询任务的延迟时间
    pollTimeout: 2000   # 毫秒，轮询任务时间间隔
    corePoolSize: 10    # 轮询任务核心线程数配置
    threadPrefix: sync-five.delayHandler-pool  # 轮询任务线程名称前缀
```

第三步：添加消息：调用DelayMessageService.sendMessage()方法
```java
@Autowired
private DelayMessageService delayMessageService;

delayMessageService.sendMessage(new DelayMessage<String>("OID1", "value10001", 10));
delayMessageService.sendMessage(new DelayMessage<Order>("OID2", new Order(), 10));
```

第四步：延迟任务处理：在方法上使用@DelayListener注解
```java
@Component
public class TestDelayHandler {

    @DelayListener(name ="OID", mode = DelayPollModeConf.MODE_PUBLIC)
    public void process1(String value){
        System.out.println(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss")+"-------->任务处理成功！ com.five.delay.temp.TestDelayHandler.process1   ==== " + value);
    }

    @DelayListener(name ="OID2", mode = DelayPollModeConf.MODE_CUSTOMIZE, task = "task.name")
    public void process2(Order order){
        System.out.println(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss")+"-------->任务处理成功！ com.five.delay.temp.TestDelayHandler.process2   ==== " + order);
    }

    @DelayListener(name ="OID3")
    public void process3(String value){
        System.out.println(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss")+"-------->任务处理成功！ com.five.delay.temp.TestDelayHandler.process3   ==== " + value);
    }
}
```

#### TODO LIST
* [x] 支持三种任务轮询模式
* [x] 支持消息类型多样化
* [ ] 消息序列化优化
* [ ] 任务失败处理机制
* [ ] 当前没有任务时，降低轮询的频率
* [ ] 轮询性能优化