package com.congthanh.productservice.application.port.out.productAtrributeValue;

import com.congthanh.productservice.domain.model.ProductAttributeValue;
import jakarta.persistence.Tuple;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Transactional
public interface ProductAttributeValueCustomRepository {

    List<Tuple> getAttributeOfProduct(String productId);

    ProductAttributeValue findProductAttributeValueOfProduct(Long attributeId, String productId);

}
