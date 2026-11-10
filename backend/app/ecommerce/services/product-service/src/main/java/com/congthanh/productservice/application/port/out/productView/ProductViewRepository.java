package com.congthanh.productservice.application.port.out.productView;

import com.congthanh.productservice.domain.model.ProductView;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@Transactional
public interface ProductViewRepository extends JpaRepository<ProductView, Long>, ProductViewCustomRepository {

}
