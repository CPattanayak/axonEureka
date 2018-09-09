package com.axon.example.democomplaint;

import org.axonframework.amqp.eventhandling.RoutingKeyResolver;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.spring.stereotype.Aggregate;
import org.hibernate.event.spi.AbstractEvent;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

@SpringBootApplication
@EnableEurekaClient
public class DemoComplaintApplication {
    @Autowired
    Environment env;

    public Exchange exchange()
    {
        return ExchangeBuilder.directExchange(env.getProperty("axon.amqp.exchange")).build();
    }

    public Queue queue(EventType eventType)
    {
        return QueueBuilder.durable(eventType.name()).build();
    }
    @Bean
    public RoutingKeyResolver routingKeyResolver() {
        return new RoutingKeyResolver(){
            @Override
            public String resolveRoutingKey(EventMessage<?> eventMessage) {

                if(ComplaintEventBase.class.isInstance(eventMessage.getPayload())){
                    return ((ComplaintEventBase)eventMessage.getPayload()).getEventType().name();
                }

                return eventMessage.getPayloadType().getPackage().getName();
            }
        };
    }

    public Binding binding(EventType eventType)
    {
        return BindingBuilder.bind(queue(eventType)).to(exchange()).with(eventType.name()).noargs();
    }
    @Autowired
    public void configure(AmqpAdmin admin){
       admin.declareExchange(exchange());
       for(EventType eventType:EventType.values()) {
           admin.declareQueue(queue(eventType));
           admin.declareBinding(binding(eventType));
       }
    }
	public static void main(String[] args) {
		SpringApplication.run(DemoComplaintApplication.class, args);
	}
	@RestController
	public static class ComplainAPI{
	    private final ComplaintQueryObjectRepository repository;
	    private final CommandGateway commandGateway;

        public ComplainAPI(ComplaintQueryObjectRepository repository,CommandGateway commandGateway) {
            this.repository = repository;
            this.commandGateway = commandGateway;
        }
        @PostMapping
        public void createCommand(@RequestBody Map<String,String> request){
            String id = UUID.randomUUID ( ).toString ( );
           commandGateway.send(new FileComplintCommand(id,request.get("company"),request.get("description")),new CommandCallback<FileComplintCommand, Object>() {
                @Override
                public void onSuccess(CommandMessage<? extends FileComplintCommand> commandMessage, Object result) {
                    System.out.println("Success");
                }

                @Override
                public void onFailure(CommandMessage<? extends FileComplintCommand> commandMessage, Throwable cause) {
                   // commandGateway.send(new CancelMoneyTransferCommand(event.getTransferId()));
                    System.out.println("error=====>"+cause);
                }
            });
          // return commandGateway.sendAndWait (new FileComplintCommand(id,request.get("company"),request.get("description")));
        }
        @GetMapping
	    public List<ComplaintQueryObject> findAll()
        {
              return repository.findAll();
        }
        @Aggregate
        public static class Complaint{
            @AggregateIdentifier
            private String complaintId;

            @CommandHandler
            public Complaint(FileComplintCommand cmd) {
               // this.complaintId = complaintId;
                Assert.hasLength(cmd.getCompany(),"company require");
                this.complaintId=cmd.getId();
                apply(new ComplaintFileEvent(cmd.getId(),cmd.getCompany(),cmd.getDescription()));
            }

            public Complaint() {
            }
           // @EventSourcingHandler
            public void on(ComplaintFileEvent event)
            {
                this.complaintId=event.getId();
            }
        }
        @Component
        public static class ComplaintQueryObjectUpdater{
            private final ComplaintQueryObjectRepository repository;

            public ComplaintQueryObjectUpdater(ComplaintQueryObjectRepository repository) {
                this.repository = repository;
            }
            @EventHandler
            public void on(ComplaintFileEvent event)
            {
                this.repository.save(new ComplaintQueryObject(event.getId(),event.getCompany(),event.getDescription()));
            }
        }
        public static class FileComplintCommand {
            private String id;
            private  String company;
            private String description;


            public FileComplintCommand(String id, String company, String description) {
                this.id = id;
                this.company = company;
                this.description = description;
            }

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public String getCompany() {
                return company;
            }

            public void setCompany(String company) {
                this.company = company;
            }

            public String getDescription() {
                return description;
            }

            public void setDescription(String description) {
                this.description = description;
            }
        }
    }
}
