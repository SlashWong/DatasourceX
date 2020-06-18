package com.dtstack.dtcenter.common.loader.mongo;

import com.dtstack.dtcenter.common.exception.DtCenterDefException;
import com.dtstack.dtcenter.loader.dto.source.MongoSourceDTO;
import org.junit.Test;

import java.util.List;

public class MongoDBClientTest {
    private static MongoDBClient nosqlClient = new MongoDBClient();

    private MongoSourceDTO source = MongoSourceDTO.builder()
            .hostPort("172.16.100.217:27017")
            .schema("admin")
            .username("root")
            .password("root")
            .build();

    @Test
    public void testCon() throws Exception {
        Boolean isConnected = nosqlClient.testCon(source);
        if (!isConnected) {
            throw new DtCenterDefException("数据源连接异常");
        }
    }

    @Test
    public void getTableList() throws Exception {
        List<String> tableList = nosqlClient.getTableList(source, null);
        System.out.println(tableList);
    }
}