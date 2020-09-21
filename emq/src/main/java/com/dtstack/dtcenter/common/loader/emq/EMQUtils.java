package com.dtstack.dtcenter.common.loader.emq;

import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Date: 2020/2/25
 * Company: www.dtstack.com
 *
 * @author xiaochen
 */
@Slf4j
public class EMQUtils {
    public static Boolean checkConnection(String address, String userName, String password) {
        log.info("获取 EMQ 数据源连接, address : {}, userName : {}", address, userName);
        String clientId = "DTSTACK_" + System.currentTimeMillis();
        try (MemoryPersistence persistence = new MemoryPersistence();
             MqttClient sampleClient = new MqttClient(address, clientId, persistence);
        ) {
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            if (StringUtils.isNotBlank(userName)) {
                connOpts.setUserName(userName);
            }
            if (StringUtils.isNotBlank(password)) {
                connOpts.setPassword(password.toCharArray());
            }
            sampleClient.setTimeToWait(5L * 1000);
            sampleClient.connect(connOpts);
            sampleClient.disconnect();
            return true;
        } catch (MqttException e) {
            throw new DtLoaderException(e.getMessage(), e);
        }
    }
}
