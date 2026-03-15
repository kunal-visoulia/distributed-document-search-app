package com.docsearch.config;

import org.opensearch.client.RestHighLevelClient;
import org.opensearch.data.client.orhlc.AbstractOpenSearchConfiguration;
import org.opensearch.data.client.orhlc.ClientConfiguration;
import org.opensearch.data.client.orhlc.RestClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenSearch RestHighLevelClient.
 * Extending AbstractOpenSearchConfiguration auto-creates the
 * ElasticsearchOperations bean used by DocumentService and TenantIndexService.
 */
@Configuration
public class OpenSearchConfig extends AbstractOpenSearchConfiguration {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Override
    public RestHighLevelClient opensearchClient() {
        ClientConfiguration config = ClientConfiguration.builder()
                .connectedTo(host + ":" + port)
                .withConnectTimeout(5000)
                .withSocketTimeout(10000)
                .build();
        return RestClients.create(config).rest();
    }
}