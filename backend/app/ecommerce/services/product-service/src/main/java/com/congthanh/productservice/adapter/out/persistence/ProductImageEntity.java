package com.congthanh.productservice.adapter.out.persistence;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

/**
 * Image attached to a product. The id is the media id issued by media-service,
 * so it is assigned by the caller instead of generated here.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "product_images")
class ProductImageEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonBackReference
    @ToString.Exclude
    private ProductEntity product;

    @Column(name = "image_path", nullable = false)
    private String imagePath;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
