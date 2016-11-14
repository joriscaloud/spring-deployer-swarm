package org.springframework.cloud.deployer.spi.deployer.socket.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.deployer.socket.service.server.SocketServiceServerController;
import org.springframework.cloud.deployer.spi.deployer.socket.service.zmq.ZMQContextManager;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.zeromq.ZMQ;

import static org.junit.Assert.assertEquals;

/**
 * Created by joriscaloud on 10/11/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class CreateAppRequestTest {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private SocketServiceServerController controller;

    @Autowired
    private ZMQContextManager zmqCM;

    @Before
    public void setUp() throws Exception {
        // Prepare controller and publish
        controller.init();
        publish();
    }

    @After
    public void tearDown() {
        zmqCM.close();
    }

    @Test
    public void subscribeRequest() {
        LoadBalancerInfos loadBalancerInfosToTest =
                new LoadBalancerInfos("springcloud/spring-cloud-deployer-spi-test-app:latest",
                "450", "load", "313", "512", "2000");
        ZMQ.Socket subscriber = zmqCM.getContext().socket(ZMQ.SUB);
        subscriber.bind("tcp://localhost:5563");
        subscriber.subscribe("TEST REQUEST".getBytes());
        while (!Thread.currentThread().isInterrupted()){
            String sink_name = subscriber.recvStr();
            String exec_speed = subscriber.recvStr();
            String load = subscriber.recvStr();
            String lifetime = subscriber.recvStr();
            String min_ram = subscriber.recvStr();
            String min_cpu = subscriber.recvStr();
            log.info(sink_name + "\n" +
                    exec_speed + "\n" +
                    load + "\n" +
                    lifetime + "\n" +
                    min_ram + "\n" +
                    min_cpu);
            LoadBalancerInfos loadBalancerInfos = new LoadBalancerInfos(sink_name, exec_speed, load,
                    lifetime, min_ram, min_cpu);
            assertEquals(loadBalancerInfos, loadBalancerInfosToTest);
        }
        subscriber.close();
    }

    public void publish() throws Exception {
        ZMQ.Socket publisher = zmqCM.getContext().socket(ZMQ.PUB);

        publisher.bind("tcp://localhost:5563");
        while (!Thread.currentThread().isInterrupted()) {
            // Write two messages, each with an envelope and content
            publisher.send("TEST REQUEST");
            publisher.sendMore("springcloud/spring-cloud-deployer-spi-test-app:latest");
            publisher.sendMore("450");
            publisher.sendMore("load");
            publisher.sendMore("313");
            publisher.sendMore("512");
            publisher.sendMore("2000");
        }
        publisher.close();
    }

    public class LoadBalancerInfos {
        private String sink_name;
        private String exec_speed;
        private String load;
        private String lifetime;
        private String min_ram;
        private String min_cpu;

        public LoadBalancerInfos(String sink_name, String exec_speed, String load,
                                 String lifetime, String min_ram, String min_cpu) {
            this.sink_name = sink_name;
            this.exec_speed = exec_speed;
            this.load = load;
            this.lifetime = lifetime;
            this.min_ram = min_ram;
            this.min_cpu = min_cpu;
        }

        public String getSink_name() {
            return sink_name;
        }

        public void setSink_name(String sink_name) {
            this.sink_name = sink_name;
        }

        public String getExec_speed() {
            return exec_speed;
        }

        public void setExec_speed(String exec_speed) {
            this.exec_speed = exec_speed;
        }

        public String getLoad() {
            return load;
        }

        public void setLoad(String load) {
            this.load = load;
        }

        public String getLifetime() {
            return lifetime;
        }

        public void setLifetime(String lifetime) {
            this.lifetime = lifetime;
        }

        public String getMin_ram() {
            return min_ram;
        }

        public void setMin_ram(String min_ram) {
            this.min_ram = min_ram;
        }

        public String getMin_cpu() {
            return min_cpu;
        }

        public void setMin_cpu(String min_cpu) {
            this.min_cpu = min_cpu;
        }
    }
}
