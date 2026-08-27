package com.nyam.domain.food.batch;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

/**
 * API의 JPA 읽기와 Batch 메타데이터·식품 쓰기에 사용할 트랜잭션 관리자를 분리합니다.
 */
@Configuration
@EnableBatchProcessing(dataSourceRef = "dataSource", transactionManagerRef = "batchTransactionManager")
public class FoodBatchInfrastructureConfiguration {

    /**
     * API 서비스의 기본 {@code @Transactional} 처리를 담당할 JPA 트랜잭션 관리자를 구성합니다.
     *
     * @param entityManagerFactory 애플리케이션 JPA 엔티티 매니저 팩토리
     * @return API JPA 트랜잭션 관리자
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /**
     * 동일 DataSource의 Batch 메타데이터와 JDBC food 쓰기를 한 chunk 트랜잭션으로 묶습니다.
     *
     * @param dataSource 애플리케이션 MySQL DataSource
     * @return Spring Batch 전용 JDBC 트랜잭션 관리자
     */
    @Bean(name = "batchTransactionManager")
    public PlatformTransactionManager batchTransactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}
