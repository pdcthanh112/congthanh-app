package com.congthanh.productservice.application.port.out.attributeValue;

import com.congthanh.productservice.domain.model.ProductAttributeValue;
import jakarta.persistence.Tuple;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Transactional
public interface AttributeValueCustomRepository {

    List<Tuple> getAttributeOfProduct(String productId);

    ProductAttributeValue findAttributeValueOfProduct(Long attributeId, String productId);

}
