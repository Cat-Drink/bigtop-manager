/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.bigtop.manager.server.proxy;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;

@Component
public class BigtopProxy {

    private final WebClient webClient;
    @Resource
    private PrometheusProxy prometheusProxy;

    @Value("${monitoring.agent-host-job-name}")
    private String agentHostJobName;
    public BigtopProxy(
            WebClient.Builder webClientBuilder, @Value("http://localhost:5173") String bigTopHost) {
        this.webClient = webClientBuilder.baseUrl(bigTopHost).build();
    }
    private MultiValueMap<String, String> createFormData(String clusterId,Integer pageNum) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("hostnameLike", ""); // 主机名正则
        formData.add("clusterId", clusterId); // 集群ID
        formData.add("ipv4Like", ""); // ipv4 正则
        formData.add("status", ""); // 主机状态
        formData.add("pageNum", String.valueOf(pageNum));
        formData.add("pageSize", "20");
        formData.add("orderBy", "id");
        formData.add("sort", "asc");
        return formData;
    }

    public JsonNode getAgentsIpv4(String clusterId) {
//        int size = prometheusProxy.queryAgentsHealthyStatus().size();
        ArrayList<String> hosts = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        int pageNum = 1;
        while (true){
            Mono<JsonNode> body = webClient
                    .post()
                    .uri(uriBuilder -> uriBuilder.path("/api/hosts").build())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(createFormData(clusterId,pageNum++)))
                    .retrieve()
                    .bodyToMono(JsonNode.class);
            JsonNode result = body.block();
            if (result == null
                    || result.isEmpty()
                    || !(result.get("failed").asBoolean())) {
                break;
            }
            JsonNode agents = result.get("data").get("content");
            agents.forEach(
                    agent -> hosts.add(agent.get("ipv4").asText())
            );
        }
        return objectMapper.createObjectNode().set("agents",objectMapper.valueToTree(hosts));
    }
}