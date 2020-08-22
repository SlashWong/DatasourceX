package com.dtstack.dtcenter.loader.client.sql;

import com.dtstack.dtcenter.loader.client.AbsClientCache;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.client.IKafka;
import com.dtstack.dtcenter.loader.dto.KafkaOffsetDTO;
import com.dtstack.dtcenter.loader.dto.KafkaTopicDTO;
import com.dtstack.dtcenter.loader.dto.source.KafkaSourceDTO;
import com.dtstack.dtcenter.loader.enums.ClientType;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.requests.MetadataResponse;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 13:04 2020/2/29
 * @Description：Kafka 测试类
 */
@Slf4j
public class KafkaTest {
    private static final AbsClientCache kafkaClientCache = ClientType.KAFKA_CLIENT.getClientCache();
    private static final AbsClientCache clientCache = ClientType.DATA_SOURCE_CLIENT.getClientCache();
    KafkaSourceDTO source = KafkaSourceDTO.builder()
            .url("172.16.100.241:2181/kafka")
            .build();

    @Before
    public void setUp() throws Exception {
        createTopic();
    }

    @Test
    public void testConForKafka() throws Exception {
        IKafka client = kafkaClientCache.getKafka(DataSourceType.KAFKA_09.getPluginName());
        Boolean isConnected = client.testCon(source);
        if (Boolean.FALSE.equals(isConnected)) {
            throw new DtLoaderException("连接异常");
        }
    }

    @Test
    public void testConForClient() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.KAFKA_09.getPluginName());
        Boolean isConnected = client.testCon(source);
        if (Boolean.FALSE.equals(isConnected)) {
            throw new DtLoaderException("连接异常");
        }
    }

    @Test
    public void getAllBrokersAddress() throws Exception {
        IKafka client = kafkaClientCache.getKafka(DataSourceType.KAFKA_09.getPluginName());
        String brokersAddress = client.getAllBrokersAddress(source);
        assert (null != brokersAddress);
    }

    @Test
    public void getTopicList() throws Exception {
        IKafka client = kafkaClientCache.getKafka(DataSourceType.KAFKA_09.getPluginName());
        List<String> topicList = client.getTopicList(source);
        assert (topicList != null);
        System.out.println(topicList);
    }

    @Test
    public void createTopic() throws Exception {
        try {
            IKafka client = kafkaClientCache.getKafka(DataSourceType.KAFKA_09.getPluginName());
            KafkaTopicDTO topicDTO = KafkaTopicDTO.builder().partitions(1).replicationFactor(1).topicName(
                    "nanqi").build();
            Boolean clientTopic = client.createTopic(source, topicDTO);
            assert (Boolean.TRUE.equals(clientTopic));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

    }

    @Test
    public void getAllPartitions() throws Exception {
        // 测试的时候需要引进 kafka 包
        IKafka client = kafkaClientCache.getKafka(DataSourceType.KAFKA_09.getPluginName());
        List<MetadataResponse.PartitionMetadata> allPartitions = client.getAllPartitions(source, "nanqi");
        System.out.println(allPartitions.size());
    }

    @Test
    public void getOffset() throws Exception {
        IKafka client = kafkaClientCache.getKafka(DataSourceType.KAFKA_09.getPluginName());
        List<KafkaOffsetDTO> offset = client.getOffset(source, "nanqi");
        assert (offset != null);
    }
}
