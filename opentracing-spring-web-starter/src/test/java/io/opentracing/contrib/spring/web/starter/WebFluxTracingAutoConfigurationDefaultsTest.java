/**
 * Copyright 2016-2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.spring.web.starter;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.opentracing.contrib.spring.web.webfilter.WebFluxSpanDecorator;
import io.opentracing.mock.MockTracer;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Csaba Kos
 * @author Gilles Robert
 *
 * Test that the default settings in {@link WebTracingProperties} work as expected.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {WebFluxTracingAutoConfigurationDefaultsTest.SpringConfiguration.class},
        properties = {"opentracing.spring.web.client.enabled=false", "spring.main.web-application-type=reactive"})
@RunWith(SpringJUnit4ClassRunner.class)
public class WebFluxTracingAutoConfigurationDefaultsTest extends AutoConfigurationBaseTest  {

    private static CountDownLatch infoCountDownLatch = new CountDownLatch(1);

    @RestController
    @Configuration
    @EnableAutoConfiguration
    public static class SpringConfiguration {
        @Bean
        public MockTracer tracer() {
            return new MockTracer();
        }

        @RequestMapping("/hello")
        public void hello() {
        }

        @RequestMapping("/hello/nested")
        public void nestedHello() {
        }

        @RequestMapping("/info")
        public void info() {
            infoCountDownLatch.countDown();
        }
    }

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private MockTracer mockTracer;

    @MockBean
    @Qualifier("mockDecorator1")
    private WebFluxSpanDecorator mockDecorator1;
    @MockBean
    @Qualifier("mockDecorator2")
    private WebFluxSpanDecorator mockDecorator2;

    // Test that top level paths are traced by default
    @Test
    public void testRequestIsTraced() {
        testRestTemplate.getForEntity("/hello", String.class);
        Awaitility.await().until(reportedSpansSize(), IsEqual.equalTo(1));
        assertThat(mockTracer.finishedSpans()).hasSize(1);
    }

    // Test that lower level paths are traced by default as well
    @Test
    public void testNestedRequestIsTraced() {
        testRestTemplate.getForEntity("/hello/nested", String.class);
        Awaitility.await().until(reportedSpansSize(), IsEqual.equalTo(1));
        assertThat(mockTracer.finishedSpans()).hasSize(1);
    }

    // Test that /info is excluded due to the default skipPattern
    @Test
    public void testExcluded() {
        testRestTemplate.getForEntity("/actuator/info", String.class);

        assertThat(mockTracer.finishedSpans()).hasSize(0);
        assertThat(Mockito.mockingDetails(mockDecorator1).getInvocations()).hasSize(0);
        assertThat(Mockito.mockingDetails(mockDecorator2).getInvocations()).hasSize(0);
    }

    public Callable<Integer> reportedSpansSize() {
        return () -> mockTracer.finishedSpans().size();
    }

    @After()
    public void reset() {
        mockTracer.reset();
        infoCountDownLatch = new CountDownLatch(1);
    }
}
