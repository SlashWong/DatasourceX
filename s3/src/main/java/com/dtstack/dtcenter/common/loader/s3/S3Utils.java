package com.dtstack.dtcenter.common.loader.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.ISourceDTO;
import com.dtstack.dtcenter.loader.dto.source.S3SourceDTO;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 15:01 2020/9/29
 * @Description：S3 工具类
 */
public class S3Utils {
    /**
     * 获取 S3 客户端
     *
     * @param source
     * @param queryDTO
     * @return
     */
    public static AmazonS3Client getClient(ISourceDTO source, SqlQueryDTO queryDTO) {
        S3SourceDTO s3SourceDTO = (S3SourceDTO) source;
        ClientConfiguration opts = new ClientConfiguration();
        //指定client的签名算法
        opts.setSignerOverride("S3SignerType");
        opts.setRequestTimeout(60 * 1000);
        opts.setClientExecutionTimeout(60 * 1000);
        AWSCredentials credentials = new BasicAWSCredentials(s3SourceDTO.getUsername(), s3SourceDTO.getPassword());
        AmazonS3Client client = new AmazonS3Client(credentials, opts);
        client.setEndpoint(s3SourceDTO.getHostname());
        return client;
    }
}