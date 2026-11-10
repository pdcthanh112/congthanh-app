package com.congthanh.productservice.application.port.out.productAttribute;


import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository // fragment của Spring Data: phải nằm cùng package với interface repository
public class ProductAttributeCustomRepositoryImpl implements ProductAttributeCustomRepository {

    @PersistenceContext
    private EntityManager entityManager;


}
