package com.congthanh.productservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 dùng chuỗi credential mặc định của AWS SDK (IRSA/Pod Identity trên EKS, profile khi chạy local),
 * không còn access key tĩnh trong cấu hình.
 */
@Configuration
@ConditionalOnProperty("product.s3.region")
public class AwsS3Config {

    @Bean
    public S3Client s3Client(@Value("${product.s3.region}") String region) {
        return S3Client.builder().region(Region.of(region)).build();
    }
}
